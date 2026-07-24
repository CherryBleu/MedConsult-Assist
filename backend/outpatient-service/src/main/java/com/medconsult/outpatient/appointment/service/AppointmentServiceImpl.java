package com.medconsult.outpatient.appointment.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medconsult.common.core.BusinessException;
import com.medconsult.common.core.ErrorCode;
import com.medconsult.common.core.PageResult;
import com.medconsult.common.core.PageQuery;
import com.medconsult.common.redis.DistributedLock;
import com.medconsult.common.redis.RedisKey;
import com.medconsult.common.security.JwtPayload;
import com.medconsult.common.security.SecurityContext;
import com.medconsult.outpatient.appointment.dto.AppointmentDTO;
import com.medconsult.outpatient.appointment.dto.AppointmentOwnershipDTO;
import com.medconsult.outpatient.appointment.entity.Appointment;
import com.medconsult.outpatient.appointment.mapper.AppointmentMapper;
import com.medconsult.outpatient.department.entity.Department;
import com.medconsult.outpatient.department.mapper.DepartmentMapper;
import com.medconsult.outpatient.doctor.entity.Doctor;
import com.medconsult.outpatient.doctor.mapper.DoctorMapper;
import com.medconsult.outpatient.schedule.entity.DoctorSchedule;
import com.medconsult.outpatient.schedule.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 预约挂号服务实现。
 *
 * <p>核心逻辑（对齐《需求文档》§4.1.3 业务规则 + 架构文档 §7.1 抢号锁）：
 * <ul>
 *   <li>创建预约：Redis 分布式锁 lock:schedule:{scheduleId}（租约 5s）内，
 *       委托 {@link AppointmentTxService#createInTx} 执行事务体：
 *       校验排班 status=AVAILABLE 且 remaining>0（规则 1）；
 *       扣减 remaining（schedule.booked_quota++，若 booked==total 则 status=FULL，规则 2）；
 *       生成就诊序号 queue_no（该排班已预约数+1）；插入 appointment（BOOKED/UNPAID）；
 *       同 patient+同 schedule 已有未取消预约 → CONFLICT（规则 5 简化版）</li>
 *   <li>取消：Redis 锁内委托 {@link AppointmentTxService#cancelInTx} 释放号源
 *       （booked_quota--，若原 FULL 则 status=AVAILABLE，规则 3）；
 *       appointment.status=CANCELLED；BOOKED/CHECKED_IN 可取消，COMPLETED/IN_PROGRESS/CANCELLED 不可（规则 4）</li>
 *   <li>状态流转：BOOKED→CHECKED_IN→IN_PROGRESS→COMPLETED；或 →NO_SHOW；CANCELLED 不可跳转</li>
 * </ul>
 *
 * <p><b>抢号锁关键</b>（架构文档 §7.1，红线）：必须用 Redis 分布式锁，不用 JVM synchronized
 * （多实例失效）。锁包裹事务方法（withLock → txService.createInTx），保证"持锁期间事务已提交"，
 * 避免锁内释放但事务未提交导致其他线程读到旧 booked_quota。
 *
 * <p>事务体独立到 {@link AppointmentTxService} 避免自注入循环依赖：本类负责锁 + 非事务查询，
 * txService 负责锁内 @Transactional 的 DB 写操作。
 *
 * <p>同 schema 内多 Mapper 访问（schedule/doctor/department）不违反"跨 schema join"红线——
 * 红线针对 SQL JOIN，业务层组装 + 本地事务是推荐做法（架构文档 §7.1：本地事务 + Redis 锁保证一致）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentServiceImpl implements AppointmentService {

    private final AppointmentMapper appointmentMapper;
    private final DoctorMapper doctorMapper;
    private final DepartmentMapper departmentMapper;
    private final ScheduleService scheduleService;
    private final DistributedLock distributedLock;
    /** 锁内事务体（独立 Bean，避免 self-injection 循环依赖） */
    private final AppointmentTxService txService;
    /** Feign 调 patient-service 批量查患者姓名（列表展示用） */
    private final com.medconsult.common.feign.client.PatientFeignClient patientFeignClient;

    /** 分布式锁租约：5s（够覆盖一次 DB 事务 + 网络往返） */
    private static final Duration LOCK_LEASE = Duration.ofSeconds(5);

    /** 可取消的预约状态：BOOKED / CHECKED_IN（COMPLETED/IN_PROGRESS/CANCELLED 不可取消，规则 4） */
    private static final Set<String> CANCELLABLE_STATUS = Set.of("BOOKED", "CHECKED_IN");

    /** 允许的支付状态白名单（§5） */
    private static final Set<String> ALLOWED_PAYMENT_STATUS =
            Set.of("UNPAID", "PAID");

    /** 允许的预约状态白名单（§5） */
    private static final Set<String> ALLOWED_APPOINTMENT_STATUS =
            Set.of("BOOKED", "CANCELLED", "CHECKED_IN", "IN_PROGRESS", "COMPLETED", "NO_SHOW");

    private static final Set<String> COMPLETABLE_VISIT_STATUS =
            Set.of("BOOKED", "CHECKED_IN", "IN_PROGRESS", "COMPLETED");

    /** 合法的状态流转（from → to 集合），不允许从 CANCELLED 跳转 */
    private static final Map<String, Set<String>> STATUS_TRANSITIONS = buildTransitions();

    // ===== §2.5.1 创建预约（抢号）=====

    @Override
    public AppointmentDTO.CreateResponse create(AppointmentDTO.CreateRequest req) {
        // 越权防护：PATIENT 不得代他人挂号（覆盖为本人身份）
        enforceCreateScope(req);
        // 前置校验：排班存在
        DoctorSchedule schedule = scheduleService.requireByNo(req.getScheduleId());

        // Redis 分布式锁：lock:schedule:{scheduleId}，防并发超卖（架构文档 §7.1）
        String lockKey = lockKey(schedule.getId());
        try {
            return distributedLock.withLock(lockKey, LOCK_LEASE,
                    () -> txService.createInTx(schedule.getScheduleNo(), req));
        } catch (DistributedLock.LockNotAcquiredException e) {
            // 未获锁 = 号源正被抢占中，转为 CONFLICT（号源已被抢占）
            throw new BusinessException(ErrorCode.CONFLICT, "号源抢占繁忙，请稍后重试: " + req.getScheduleId());
        }
    }

    // ===== §2.5.2 详情 =====

    @Override
    public AppointmentDTO.DetailResponse detail(String appointmentNo) {
        Appointment a = requireByNo(appointmentNo);
        // 越权防护（IDOR）：PATIENT 只能查本人，DOCTOR 只能查本人接诊的
        enforceAppointmentOwnership(a);
        // 业务层组装医生名/科室名（同 schema，不跨表 join）。
        // 患者名跨服务（patient-service）回填，兼容 patient_id 与历史 patient_no。
        Doctor doctor = a.getDoctorId() != null ? doctorMapper.selectById(a.getDoctorId()) : null;
        Department dept = a.getDepartmentId() != null ? departmentMapper.selectById(a.getDepartmentId()) : null;
        String patientName = resolvePatientName(
                a,
                resolvePatientNames(a.getPatientId() == null ? Set.of() : Set.of(a.getPatientId())),
                resolvePatientNamesByNos(a.getPatientNo() == null || a.getPatientNo().isBlank()
                        ? Set.of()
                        : Set.of(a.getPatientNo())));
        return new AppointmentDTO.DetailResponse(
                a.getAppointmentNo(),
                patientName,
                doctor != null ? doctor.getName() : null,
                dept != null ? dept.getName() : null,
                a.getAppointmentDate(),
                a.getPeriod(),
                a.getQueueNo() == null ? 0 : a.getQueueNo(),
                a.getFee(),
                a.getPaymentStatus(),
                a.getAppointmentStatus(),
                a.getVisitReason(),
                a.getCancelReason(),
                a.getCreatedAt() != null ? a.getCreatedAt().atOffset(java.time.OffsetDateTime.now().getOffset())
                        : null);
    }

    // ===== §2.5.3 列表 =====

    @Override
    public PageResult<AppointmentDTO.ListItem> list(int page, int pageSize, String patientId, String status) {
        Page<Appointment> p = new Page<>(PageQuery.normalizePage(page), PageQuery.normalizePageSize(pageSize));
        QueryWrapper<Appointment> qw = new QueryWrapper<>();
        if (status != null && !status.isBlank()) {
            qw.eq("appointment_status", status);
        }
        // 越权防护（IDOR）：PATIENT 强制只查本人预约（按主键 patient_id，忽略请求传入的 patientId）
        JwtPayload payload = requireUser();
        if (isPatient(payload)) {
            Long selfPatientId = payload.patientId();
            if (selfPatientId == null) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "当前账号未关联患者档案，无法查询预约");
            }
            qw.eq("patient_id", selfPatientId);
        } else if (isDoctor(payload)) {
            // DOCTOR 只查本人接诊的预约（按 doctor_id 过滤，忽略请求传入的 patientId）
            Long selfDoctorId = payload.doctorId();
            if (selfDoctorId == null) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "当前账号未关联医生档案，无法查询预约");
            }
            qw.eq("doctor_id", selfDoctorId);
            // 可选：再按 patient_no 细分
            if (patientId != null && !patientId.isBlank()) {
                qw.eq("patient_no", patientId);
            }
        } else if (patientId != null && !patientId.isBlank()) {
            // 管理员：patientId 实为 patient_no（业务编号串），按业务编号过滤
            qw.eq("patient_no", patientId);
        }
        qw.orderByDesc("created_at");
        IPage<Appointment> result = appointmentMapper.selectPage(p, qw);

        // 组装医生名/科室名（用 LinkedHashSet 去重，O(n)；避免 ArrayList.contains 的 O(n²)）
        Set<Long> doctorIdSet = new java.util.LinkedHashSet<>();
        Set<Long> deptIdSet = new java.util.LinkedHashSet<>();
        Set<Long> patientIdSet = new java.util.LinkedHashSet<>();
        Set<String> patientNoSet = new java.util.LinkedHashSet<>();
        for (Appointment a : result.getRecords()) {
            if (a.getDoctorId() != null) doctorIdSet.add(a.getDoctorId());
            if (a.getDepartmentId() != null) deptIdSet.add(a.getDepartmentId());
            if (a.getPatientId() != null) patientIdSet.add(a.getPatientId());
            if (a.getPatientNo() != null && !a.getPatientNo().isBlank()) patientNoSet.add(a.getPatientNo());
        }
        // 空集合时跳过 selectBatchIds：MyBatis-Plus 对空 IN () 会生成非法 SQL 抛异常（500）。
        Map<Long, Doctor> doctorMap = doctorIdSet.isEmpty()
                ? java.util.Collections.emptyMap()
                : toDoctorMap(doctorMapper.selectBatchIds(new ArrayList<>(doctorIdSet)));
        Map<Long, Department> deptMap = deptIdSet.isEmpty()
                ? java.util.Collections.emptyMap()
                : toDeptMap(departmentMapper.selectBatchIds(new ArrayList<>(deptIdSet)));
        // 批量查患者姓名（Feign 调 patient-service），失败降级为空 Map 不阻断列表
        Map<Long, String> patientNameMap = resolvePatientNames(patientIdSet);
        Map<String, String> patientNoNameMap = resolvePatientNamesByNos(patientNoSet);

        List<AppointmentDTO.ListItem> items = new ArrayList<>();
        for (Appointment a : result.getRecords()) {
            Doctor doctor = doctorMap.get(a.getDoctorId());
            Department dept = deptMap.get(a.getDepartmentId());
            items.add(new AppointmentDTO.ListItem(
                    a.getAppointmentNo(),
                    a.getPatientNo(),
                    resolvePatientName(a, patientNameMap, patientNoNameMap),
                    dept != null ? dept.getName() : null,
                    doctor != null ? doctor.getName() : null,
                    a.getAppointmentDate(),
                    a.getPeriod(),
                    a.getQueueNo(),
                    a.getFee(),
                    a.getPaymentStatus(),
                    a.getAppointmentStatus(),
                    a.getVisitReason()));
        }
        return PageResult.of((int) result.getCurrent(), (int) result.getSize(), result.getTotal(), items);
    }

    // ===== §2.5.4 取消预约（释放号源）=====

    @Override
    public AppointmentDTO.CancelResponse cancel(String appointmentNo, AppointmentDTO.CancelRequest req) {
        Appointment a = requireByNo(appointmentNo);
        // 越权防护（IDOR）：PATIENT 只能取消本人预约，DOCTOR 只能取消本人接诊的
        enforceAppointmentOwnership(a);

        // 校验当前状态可取消（规则 4：COMPLETED/IN_PROGRESS/CANCELLED 不可取消）
        if (!CANCELLABLE_STATUS.contains(a.getAppointmentStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "当前状态不可取消: " + a.getAppointmentStatus());
        }

        // Redis 锁：释放号源需与创建同样加锁，防并发读旧 booked_quota
        String lockKey = lockKey(a.getScheduleId());
        try {
            return distributedLock.withLock(lockKey, LOCK_LEASE,
                    () -> txService.cancelInTx(appointmentNo, req, CANCELLABLE_STATUS));
        } catch (DistributedLock.LockNotAcquiredException e) {
            throw new BusinessException(ErrorCode.CONFLICT, "号源操作繁忙，请稍后重试: " + appointmentNo);
        }
    }

    // ===== §2.5.5 更新支付状态 =====

    @Override
    public AppointmentDTO.PaymentResponse updatePayment(String appointmentNo, AppointmentDTO.PaymentUpdateRequest req) {
        Appointment a = requireByNo(appointmentNo);
        // 越权防护（IDOR）：PATIENT 只能支付本人预约
        enforceAppointmentOwnership(a);
        String lockKey = lockKey(a.getScheduleId());
        try {
            return distributedLock.withLock(lockKey, LOCK_LEASE,
                    () -> txService.updatePaymentInTx(appointmentNo, req, ALLOWED_PAYMENT_STATUS));
        } catch (DistributedLock.LockNotAcquiredException e) {
            throw new BusinessException(ErrorCode.CONFLICT, "号源操作繁忙，请稍后重试: " + appointmentNo);
        }
    }

    // ===== 患者签到（专用 SELF 动作）=====

    @Override
    public AppointmentDTO.StatusResponse checkIn(String appointmentNo) {
        Appointment a = requireByNo(appointmentNo);
        enforceAppointmentOwnership(a);
        if (!"PAID".equals(a.getPaymentStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "预约未支付，不能签到");
        }
        String lockKey = lockKey(a.getScheduleId());
        try {
            return distributedLock.withLock(lockKey, LOCK_LEASE,
                    () -> txService.checkInInTx(a.getAppointmentNo(),
                            STATUS_TRANSITIONS, ALLOWED_APPOINTMENT_STATUS));
        } catch (DistributedLock.LockNotAcquiredException e) {
            throw new BusinessException(ErrorCode.CONFLICT, "号源操作繁忙，请稍后重试: " + appointmentNo);
        }
    }

    // ===== §2.5.6 更新就诊状态（状态机）=====

    @Override
    public AppointmentDTO.StatusResponse updateStatus(String appointmentNo, AppointmentDTO.StatusUpdateRequest req) {
        Appointment a = requireByNo(appointmentNo);
        // 越权防护（IDOR）：PATIENT 只能改本人预约状态，DOCTOR 只能改本人接诊的
        enforceAppointmentOwnership(a);
        return transitionStatus(a, req.getAppointmentStatus());
    }

    @Override
    public AppointmentOwnershipDTO internalResolveOwnership(String appointmentNo) {
        Appointment a = requireByNo(appointmentNo);
        return new AppointmentOwnershipDTO(
                a.getId(),
                a.getAppointmentNo(),
                a.getPatientId(),
                a.getDoctorId());
    }

    @Override
    public void internalComplete(Long appointmentId) {
        Appointment a = requireById(appointmentId);
        if ("COMPLETED".equals(a.getAppointmentStatus())) {
            return;
        }
        String lockKey = lockKey(a.getScheduleId());
        try {
            distributedLock.withLock(lockKey, LOCK_LEASE,
                    () -> txService.completeInternalInTx(a.getAppointmentNo(), COMPLETABLE_VISIT_STATUS));
        } catch (DistributedLock.LockNotAcquiredException e) {
            throw new BusinessException(ErrorCode.CONFLICT, "鍙锋簮鎿嶄綔绻佸繖锛岃绋嶅悗閲嶈瘯: " + a.getAppointmentNo());
        }
    }

    private AppointmentDTO.StatusResponse transitionStatus(Appointment a, String newStatus) {
        if (!ALLOWED_APPOINTMENT_STATUS.contains(newStatus)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "非法预约状态: " + newStatus);
        }
        String current = a.getAppointmentStatus();
        // 校验状态流转合法性（不能从 CANCELLED 跳转；只能走定义的流转）
        Set<String> allowed = STATUS_TRANSITIONS.get(current);
        if (allowed == null || !allowed.contains(newStatus)) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "非法状态流转: " + current + " → " + newStatus);
        }
        AppointmentDTO.StatusResponse response;
        String lockKey = lockKey(a.getScheduleId());
        try {
            response = distributedLock.withLock(lockKey, LOCK_LEASE,
                    () -> txService.transitionStatusInTx(a.getAppointmentNo(), newStatus,
                            STATUS_TRANSITIONS, ALLOWED_APPOINTMENT_STATUS));
        } catch (DistributedLock.LockNotAcquiredException e) {
            throw new BusinessException(ErrorCode.CONFLICT, "号源操作繁忙，请稍后重试: " + a.getAppointmentNo());
        }
        log.info("预约状态流转: appointmentNo={} {} → {}", a.getAppointmentNo(), current, newStatus);
        return response;
    }

    // ===== 越权防护（IDOR，架构 §4.3 SELF 数据范围 / 修改建议 §2.3 权限矩阵）=====
    //
    // PATIENT 身份只能访问/操作自己的预约（appointment.patient_id == 本人 patientId）；
    // DOCTOR 身份只能操作自己接诊的预约（appointment.doctor_id == 本人 doctorId）；
    // 管理员（HOSPITAL_ADMIN）不限。
    // 身份取自 SecurityContext（网关已解析 X-User-* 头重建 JwtPayload，§4.4）。
    // 直连（无网关头、无 token）时 SecurityContext.getPayload() 返回 null，按"匿名拒绝"处理。

    /**
     * 校验对单条预约的访问权（detail/cancel/updatePayment/updateStatus）。
     * <p>PATIENT：必须是本人预约；DOCTOR：必须为接诊医生；管理员：放行。
     */
    private void enforceAppointmentOwnership(Appointment a) {
        JwtPayload payload = requireUser();
        if (isPatient(payload)) {
            Long selfId = payload.patientId();
            if (selfId == null || !selfId.equals(a.getPatientId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该预约");
            }
        } else if (isDoctor(payload)) {
            Long selfDoctorId = payload.doctorId();
            if (selfDoctorId == null || !selfDoctorId.equals(a.getDoctorId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作非本人接诊的预约");
            }
        }
        // HOSPITAL_ADMIN / PHARMACY_ADMIN 等管理角色不限制（架构 §4.3 ALL）
    }

    /**
     * PATIENT 创建预约时强制覆盖 patientId 为本人（防代他人挂号）。
     * <p>PATIENT 身份：忽略请求体 patientId，改用本人 patientId；
     * DOCTOR/管理员：保留请求体值（代挂号场景）。
     */
    private void enforceCreateScope(AppointmentDTO.CreateRequest req) {
        JwtPayload payload = requireUser();
        if (isPatient(payload)) {
            Long selfId = payload.patientId();
            if (selfId == null) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "当前账号未关联患者档案，无法挂号");
            }
            // JWT pid 即 patient 表主键 id（注册即建档时写入 sys_user.patient_id）。
            // PATIENT 一律以本人身份挂号，强制覆盖请求体 patientId 为本人主键，
            // txService 据此直接写入 appointment.patient_id（不再 hash），list/ownership 用同一值比对。
            if (req.getPatientId() != null && !req.getPatientId().isBlank()
                    && !String.valueOf(selfId).equals(req.getPatientId())) {
                log.warn("PATIENT 创建预约时传入了 patientId={}，已覆盖为本人身份 {}", req.getPatientId(), selfId);
            }
            req.setPatientId(String.valueOf(selfId));
        }
    }

    /** 要求登录用户身份；匿名或服务身份拒绝 */
    private JwtPayload requireUser() {
        JwtPayload payload = SecurityContext.getPayload();
        if (payload == null || !payload.isUser()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "需要用户登录");
        }
        return payload;
    }

    private static boolean isPatient(JwtPayload p) {
        if (p == null) return false;
        if ("PATIENT".equals(p.primaryRole())) return true;
        return p.roles() != null && p.roles().contains("PATIENT");
    }

    private static boolean isDoctor(JwtPayload p) {
        if (p == null) return false;
        if ("DOCTOR".equals(p.primaryRole())) return true;
        return p.roles() != null && p.roles().contains("DOCTOR");
    }

    // ===== 私有助手 =====

    /** 按预约编号查询，未找到抛 NOT_FOUND */
    private Appointment requireByNo(String appointmentNo) {
        if (appointmentNo == null || appointmentNo.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "预约编号不能为空");
        }
        Appointment a = appointmentMapper.selectOne(
                new QueryWrapper<Appointment>().eq("appointment_no", appointmentNo));
        if (a == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "预约不存在: " + appointmentNo);
        }
        return a;
    }

    private Appointment requireById(Long appointmentId) {
        if (appointmentId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "棰勭害ID涓嶈兘涓虹┖");
        }
        Appointment a = appointmentMapper.selectById(appointmentId);
        if (a == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "棰勭害涓嶅瓨鍦? " + appointmentId);
        }
        return a;
    }

    private Map<Long, Doctor> toDoctorMap(List<Doctor> doctors) {
        Map<Long, Doctor> map = new HashMap<>();
        for (Doctor d : doctors) {
            map.put(d.getId(), d);
        }
        return map;
    }

    private Map<Long, Department> toDeptMap(List<Department> depts) {
        Map<Long, Department> map = new HashMap<>();
        for (Department d : depts) {
            map.put(d.getId(), d);
        }
        return map;
    }

    /**
     * 批量查患者姓名（Feign 调 patient-service /internal/patients/names）。
     * <p>patient-service 不可用时降级为空 Map——列表仍正常显示，只是 patientName 为 null。
     */
    private Map<Long, String> resolvePatientNames(Set<Long> patientIds) {
        if (patientIds == null || patientIds.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        try {
            com.medconsult.common.core.Result<java.util.Map<Long, String>> resp =
                    patientFeignClient.namesByIds(new ArrayList<>(patientIds));
            if (resp != null && resp.data() != null) {
                return resp.data();
            }
        } catch (Exception e) {
            log.warn("批量查患者姓名失败，降级为空: {}", e.getMessage());
        }
        return java.util.Collections.emptyMap();
    }

    private Map<String, String> resolvePatientNamesByNos(Set<String> patientNos) {
        if (patientNos == null || patientNos.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        try {
            com.medconsult.common.core.Result<java.util.Map<String, String>> resp =
                    patientFeignClient.namesByNos(new ArrayList<>(patientNos));
            if (resp != null && resp.data() != null) {
                return resp.data();
            }
        } catch (Exception e) {
            log.warn("按患者编号批量查患者姓名失败，降级为空: {}", e.getMessage());
        }
        return java.util.Collections.emptyMap();
    }

    private static String resolvePatientName(Appointment a, Map<Long, String> patientNameMap,
                                             Map<String, String> patientNoNameMap) {
        String name = patientNameMap.get(a.getPatientId());
        if (name != null && !name.isBlank()) {
            return name;
        }
        if (a.getPatientNo() != null && !a.getPatientNo().isBlank()) {
            name = patientNoNameMap.get(a.getPatientNo());
            if (name != null && !name.isBlank()) {
                return name;
            }
        }
        if (a.getPatientId() != null) {
            name = patientNoNameMap.get(String.valueOf(a.getPatientId()));
            if (name != null && !name.isBlank()) {
                return name;
            }
        }
        return null;
    }

    /** 锁 key：复用 RedisKey.SCHEDULE_QUOTA_LOCK 常量，避免锁键字符串漂移 */
    private static String lockKey(Long scheduleId) {
        return RedisKey.SCHEDULE_QUOTA_LOCK + scheduleId;
    }

    /** 状态流转表：from → {允许的 to} */
    private static Map<String, Set<String>> buildTransitions() {
        Map<String, Set<String>> m = new HashMap<>();
        m.put("BOOKED", Set.of("CANCELLED", "CHECKED_IN", "NO_SHOW"));
        m.put("CHECKED_IN", Set.of("IN_PROGRESS", "CANCELLED", "NO_SHOW"));
        m.put("IN_PROGRESS", Set.of("COMPLETED", "NO_SHOW"));
        // 终态不可流转
        m.put("COMPLETED", Set.of());
        m.put("CANCELLED", Set.of());
        m.put("NO_SHOW", Set.of());
        return Map.copyOf(m);
    }
}
