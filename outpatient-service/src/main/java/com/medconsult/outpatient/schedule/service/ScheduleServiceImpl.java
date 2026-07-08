package com.medconsult.outpatient.schedule.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medconsult.common.core.BusinessException;
import com.medconsult.common.core.ErrorCode;
import com.medconsult.common.core.PageResult;
import com.medconsult.outpatient.appointment.entity.Appointment;
import com.medconsult.outpatient.appointment.mapper.AppointmentMapper;
import com.medconsult.outpatient.department.entity.Department;
import com.medconsult.outpatient.department.mapper.DepartmentMapper;
import com.medconsult.outpatient.doctor.entity.Doctor;
import com.medconsult.outpatient.doctor.mapper.DoctorMapper;
import com.medconsult.outpatient.schedule.dto.ScheduleDTO;
import com.medconsult.outpatient.schedule.entity.DoctorSchedule;
import com.medconsult.outpatient.schedule.mapper.DoctorScheduleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 医生排班服务实现。
 *
 * <p>核心逻辑（对齐《需求文档》§4.1.2 业务规则）：
 * <ul>
 *   <li>创建：校验医生存在且 enabled（规则 §4.3.1-2）；校验科室存在且 enabled（§4.3.1-1）；
 *       同医生同日期同时段不重复（uk + 业务预检，规则 1）→ CONFLICT；
 *       remaining = total - booked（初始 booked=0）；状态初始 AVAILABLE</li>
 *   <li>列表：分页，可按 departmentId(department_no)/dateFrom/dateTo 过滤；业务层组装医生名/科室名</li>
 *   <li>可预约：status=AVAILABLE 且 remaining>0；按 date 过滤</li>
 *   <li>状态变更：AVAILABLE/FULL/SUSPENDED/CANCELLED；SUSPENDED 时返回 notifiedAppointments
 *       （该排班下未完成预约数，用于通知，规则 4）</li>
 * </ul>
 *
 * <p>同 schema 内多 Mapper 访问（doctor/department/appointment）不违反"跨 schema join"红线——
 * 红线针对 SQL JOIN，业务层组装是推荐做法。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleServiceImpl implements ScheduleService {

    private final DoctorScheduleMapper scheduleMapper;
    private final DoctorMapper doctorMapper;
    private final DepartmentMapper departmentMapper;
    private final AppointmentMapper appointmentMapper;

    /** 允许的排班状态白名单（§2.4.4 / §5） */
    private static final List<String> ALLOWED_STATUS =
            List.of("AVAILABLE", "FULL", "SUSPENDED", "CANCELLED");

    /** 允许的时段白名单（§5） */
    private static final List<String> ALLOWED_PERIOD =
            List.of("MORNING", "AFTERNOON", "EVENING", "FULL_DAY");

    // ===== §2.4.1 创建 =====

    @Override
    @Transactional
    public ScheduleDTO.CreateResponse create(ScheduleDTO.CreateRequest req) {
        // 时段合法性
        if (!ALLOWED_PERIOD.contains(req.getPeriod())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "非法时段: " + req.getPeriod());
        }
        if (req.getTotalQuota() == null || req.getTotalQuota() <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "总号源必须大于 0");
        }

        // 校验医生存在且启用（§4.3.1 规则 2：停用医生不可创建新排班）
        Doctor doctor = doctorMapper.selectOne(new QueryWrapper<Doctor>().eq("doctor_no", req.getDoctorId()));
        if (doctor == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "医生不存在: " + req.getDoctorId());
        }
        if (doctor.getEnabled() == null || doctor.getEnabled() != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "医生已停用，不可创建排班: " + req.getDoctorId());
        }

        // 校验科室存在且启用（§4.3.1 规则 1：停用科室不可创建新排班）
        Department dept = departmentMapper.selectOne(
                new QueryWrapper<Department>().eq("department_no", req.getDepartmentId()));
        if (dept == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "科室不存在: " + req.getDepartmentId());
        }
        if (dept.getEnabled() == null || dept.getEnabled() != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "科室已停用，不可创建排班: " + req.getDepartmentId());
        }

        // 同医生同日期同时段不重复（规则 1：uk + 业务预检双保险）
        Long dupCount = scheduleMapper.selectCount(new QueryWrapper<DoctorSchedule>()
                .eq("doctor_id", doctor.getId())
                .eq("schedule_date", req.getScheduleDate())
                .eq("period", req.getPeriod()));
        if (dupCount != null && dupCount > 0) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "该医生在此日期此时段已存在排班: " + req.getDoctorId() + "/" + req.getScheduleDate() + "/" + req.getPeriod());
        }

        DoctorSchedule s = new DoctorSchedule();
        s.setScheduleNo(generateScheduleNo());
        s.setDoctorId(doctor.getId());
        s.setDepartmentId(dept.getId());
        s.setScheduleDate(req.getScheduleDate());
        s.setPeriod(req.getPeriod());
        s.setStartTime(req.getStartTime());
        s.setEndTime(req.getEndTime());
        s.setTotalQuota(req.getTotalQuota());
        s.setBookedQuota(0);
        s.setRegistrationFee(req.getRegistrationFee());
        s.setStatus("AVAILABLE");
        scheduleMapper.insert(s);

        int remaining = req.getTotalQuota() - 0;
        return new ScheduleDTO.CreateResponse(s.getScheduleNo(), remaining, s.getStatus());
    }

    // ===== §2.4.2 列表 =====

    @Override
    public PageResult<ScheduleDTO.ListItem> list(int page, int pageSize, String departmentId,
                                                  LocalDate dateFrom, LocalDate dateTo) {
        Page<DoctorSchedule> p = new Page<>(page <= 0 ? 1 : page, pageSize <= 0 ? 10 : pageSize);
        QueryWrapper<DoctorSchedule> qw = new QueryWrapper<>();
        if (departmentId != null && !departmentId.isBlank()) {
            Department dept = departmentMapper.selectOne(
                    new QueryWrapper<Department>().eq("department_no", departmentId));
            if (dept == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "科室不存在: " + departmentId);
            }
            qw.eq("department_id", dept.getId());
        }
        if (dateFrom != null) {
            qw.ge("schedule_date", dateFrom);
        }
        if (dateTo != null) {
            qw.le("schedule_date", dateTo);
        }
        qw.orderByAsc("schedule_date").orderByAsc("period");
        IPage<DoctorSchedule> result = scheduleMapper.selectPage(p, qw);

        // 业务层组装医生名/科室名
        List<Long> doctorIds = new ArrayList<>();
        List<Long> deptIds = new ArrayList<>();
        for (DoctorSchedule s : result.getRecords()) {
            if (s.getDoctorId() != null && !doctorIds.contains(s.getDoctorId())) {
                doctorIds.add(s.getDoctorId());
            }
            if (s.getDepartmentId() != null && !deptIds.contains(s.getDepartmentId())) {
                deptIds.add(s.getDepartmentId());
            }
        }
        Map<Long, Doctor> doctorMap = toMap(doctorMapper.selectBatchIds(doctorIds));
        Map<Long, Department> deptMap = toDeptMap(departmentMapper.selectBatchIds(deptIds));

        List<ScheduleDTO.ListItem> items = new ArrayList<>();
        for (DoctorSchedule s : result.getRecords()) {
            Doctor doc = doctorMap.get(s.getDoctorId());
            Department dept = deptMap.get(s.getDepartmentId());
            int booked = s.getBookedQuota() == null ? 0 : s.getBookedQuota();
            int total = s.getTotalQuota() == null ? 0 : s.getTotalQuota();
            items.add(new ScheduleDTO.ListItem(
                    s.getScheduleNo(),
                    doc != null ? doc.getName() : null,
                    dept != null ? dept.getName() : null,
                    s.getScheduleDate(),
                    s.getPeriod(),
                    total,
                    booked,
                    total - booked,
                    s.getStatus()));
        }
        return PageResult.of((int) result.getCurrent(), (int) result.getSize(), result.getTotal(), items);
    }

    // ===== §2.4.3 可预约号源 =====

    @Override
    public List<ScheduleDTO.AvailableItem> available(String departmentId, LocalDate date) {
        QueryWrapper<DoctorSchedule> qw = new QueryWrapper<>();
        qw.eq("status", "AVAILABLE");
        if (date != null) {
            qw.eq("schedule_date", date);
        }
        if (departmentId != null && !departmentId.isBlank()) {
            Department dept = departmentMapper.selectOne(
                    new QueryWrapper<Department>().eq("department_no", departmentId));
            if (dept == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "科室不存在: " + departmentId);
            }
            qw.eq("department_id", dept.getId());
        }
        qw.orderByAsc("schedule_date").orderByAsc("period");
        List<DoctorSchedule> schedules = scheduleMapper.selectList(qw);

        // 组装医生名/编号（批量）
        List<Long> doctorIds = new ArrayList<>();
        for (DoctorSchedule s : schedules) {
            if (s.getDoctorId() != null && !doctorIds.contains(s.getDoctorId())) {
                doctorIds.add(s.getDoctorId());
            }
        }
        Map<Long, Doctor> doctorMap = toMap(doctorMapper.selectBatchIds(doctorIds));

        List<ScheduleDTO.AvailableItem> items = new ArrayList<>();
        for (DoctorSchedule s : schedules) {
            int booked = s.getBookedQuota() == null ? 0 : s.getBookedQuota();
            int total = s.getTotalQuota() == null ? 0 : s.getTotalQuota();
            int remaining = total - booked;
            if (remaining <= 0) {
                continue; // 剩余 0 不展示
            }
            Doctor doc = doctorMap.get(s.getDoctorId());
            items.add(new ScheduleDTO.AvailableItem(
                    s.getScheduleNo(),
                    doc != null ? doc.getDoctorNo() : null,
                    doc != null ? doc.getName() : null,
                    s.getPeriod(),
                    remaining,
                    s.getRegistrationFee()));
        }
        return items;
    }

    // ===== §2.4.4 状态变更 =====

    @Override
    @Transactional
    public ScheduleDTO.StatusResponse updateStatus(String scheduleNo, ScheduleDTO.StatusUpdateRequest req) {
        DoctorSchedule s = requireByNo(scheduleNo);
        String newStatus = req.getStatus();
        if (!ALLOWED_STATUS.contains(newStatus)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "非法排班状态: " + newStatus);
        }
        s.setStatus(newStatus);
        scheduleMapper.updateById(s);

        // SUSPENDED/CANCELLED 时统计受影响未完成预约数（规则 4：排班变更应通知已预约患者）
        int notifiedAppointments = 0;
        if ("SUSPENDED".equals(newStatus) || "CANCELLED".equals(newStatus)) {
            // 未完成 = 非 CANCELLED / 非 COMPLETED / 非 NO_SHOW
            notifiedAppointments = countActiveAppointments(s.getId());
        }

        log.info("排班状态变更: scheduleNo={} {} → {} reason={} notified={}",
                scheduleNo, s.getStatus(), newStatus, req.getReason(), notifiedAppointments);
        return new ScheduleDTO.StatusResponse(s.getScheduleNo(), newStatus, notifiedAppointments);
    }

    // ===== 内部校验 =====

    @Override
    public DoctorSchedule requireByNo(String scheduleNo) {
        if (scheduleNo == null || scheduleNo.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "排班编号不能为空");
        }
        DoctorSchedule s = scheduleMapper.selectOne(
                new QueryWrapper<DoctorSchedule>().eq("schedule_no", scheduleNo));
        if (s == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "排班不存在: " + scheduleNo);
        }
        return s;
    }

    // ===== 私有助手 =====

    /** 统计某排班下"未完成"预约数（非 CANCELLED/COMPLETED/NO_SHOW） */
    private int countActiveAppointments(Long scheduleId) {
        Long count = appointmentMapper.selectCount(new QueryWrapper<Appointment>()
                .eq("schedule_id", scheduleId)
                .notIn("appointment_status", List.of("CANCELLED", "COMPLETED", "NO_SHOW")));
        return count == null ? 0 : count.intValue();
    }

    /** 生成排班编号：S + 雪花序列（IdWorker 的 Long 无符号 base36）。DB 有 uk_schedule_no 兜底 */
    private static String generateScheduleNo() {
        long id = IdWorker.getId();
        return "S" + Long.toUnsignedString(id, Character.MAX_RADIX).toUpperCase();
    }

    private Map<Long, Doctor> toMap(List<Doctor> doctors) {
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
}
