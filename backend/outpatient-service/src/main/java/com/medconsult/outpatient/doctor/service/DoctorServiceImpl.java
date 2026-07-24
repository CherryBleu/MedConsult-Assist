package com.medconsult.outpatient.doctor.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medconsult.common.core.BusinessException;
import com.medconsult.common.core.ErrorCode;
import com.medconsult.common.core.PageResult;
import com.medconsult.common.core.PageQuery;
import com.medconsult.common.feign.dto.EntityIdDTO;
import com.medconsult.outpatient.department.entity.Department;
import com.medconsult.outpatient.department.mapper.DepartmentMapper;
import com.medconsult.outpatient.doctor.dto.DoctorDTO;
import com.medconsult.outpatient.doctor.entity.Doctor;
import com.medconsult.outpatient.doctor.mapper.DoctorMapper;
import com.medconsult.outpatient.schedule.entity.DoctorSchedule;
import com.medconsult.outpatient.schedule.mapper.DoctorScheduleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 医生服务实现。
 *
 * <p>核心逻辑（对齐《需求文档》§4.3.1）：
 * <ul>
 *   <li>分页查询：可按 departmentId(department_no) / enabled 过滤</li>
 *   <li>{@code departmentName} 业务层二次查询组装（不跨表 join，架构红线）：
 *       先把本页医生涉及的 department_id 收集，批量查 department 再回填。
 *       同 schema 内多 Mapper 访问不违反"跨 schema join"红线——红线针对的是 SQL JOIN，
 *       业务层组装是推荐做法。</li>
 *   <li>{@code specialties} JSON 数组串反序列化为 List&lt;String&gt;</li>
 *   <li>{@code requireByNo}：按 doctor_no 查，未找到抛 NOT_FOUND（供 schedule 服务校验）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DoctorServiceImpl implements DoctorService {

    private final DoctorMapper doctorMapper;
    /** 同 schema 内直接用 DepartmentMapper 做 department_no → id 解析与批量回填（非 SQL JOIN） */
    private final DepartmentMapper departmentMapper;
    private final DoctorScheduleMapper doctorScheduleMapper;
    private final ObjectMapper objectMapper;

    // ===== §2.3.2 分页查询 =====

    @Override
    public PageResult<DoctorDTO.ListItem> list(int page, int pageSize, String departmentId, Boolean enabled) {
        Page<Doctor> p = new Page<>(PageQuery.normalizePage(page), PageQuery.normalizePageSize(pageSize));
        QueryWrapper<Doctor> qw = new QueryWrapper<>();
        if (enabled != null) {
            qw.eq("enabled", enabled ? 1 : 0);
        }
        // departmentId 为 department_no，需先解析为 BIGINT 主键
        if (departmentId != null && !departmentId.isBlank()) {
            Department dept = departmentMapper.selectOne(
                    new QueryWrapper<Department>().eq("department_no", departmentId));
            if (dept == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "科室不存在: " + departmentId);
            }
            qw.eq("department_id", dept.getId());
        }
        qw.orderByDesc("created_at");
        IPage<Doctor> result = doctorMapper.selectPage(p, qw);

        // 业务层组装 departmentName：收集本页 department_id 批量查（一次 selectBatchIds）
        List<Long> deptIds = new ArrayList<>();
        for (Doctor d : result.getRecords()) {
            if (d.getDepartmentId() != null && !deptIds.contains(d.getDepartmentId())) {
                deptIds.add(d.getDepartmentId());
            }
        }
        Map<Long, Department> deptMap = loadDepartments(deptIds);
        Map<Long, BigDecimal> feeMap = loadRegistrationFees(result.getRecords().stream()
                .map(Doctor::getId)
                .filter(id -> id != null)
                .distinct()
                .toList());

        List<DoctorDTO.ListItem> items = new ArrayList<>();
        for (Doctor d : result.getRecords()) {
            Department dept = deptMap.get(d.getDepartmentId());
            items.add(new DoctorDTO.ListItem(
                    d.getDoctorNo(),
                    d.getId(),
                    d.getName(),
                    dept != null ? dept.getDepartmentNo() : null,
                    dept != null ? dept.getName() : null,
                    d.getTitle(),
                    fromJsonArray(d.getSpecialties()),
                    d.getEnabled() != null && d.getEnabled() == 1,
                    feeMap.get(d.getId())));
        }
        return PageResult.of((int) result.getCurrent(), (int) result.getSize(), result.getTotal(), items);
    }

    @Override
    public DoctorDTO.Detail detail(String doctorId) {
        Doctor d = requireByNo(doctorId);
        Department dept = d.getDepartmentId() != null ? departmentMapper.selectById(d.getDepartmentId()) : null;
        return new DoctorDTO.Detail(
                d.getDoctorNo(),
                d.getId(),
                d.getName(),
                dept != null ? dept.getDepartmentNo() : null,
                dept != null ? dept.getName() : null,
                d.getTitle(),
                fromJsonArray(d.getSpecialties()),
                d.getIntroduction(),
                d.getEnabled() != null && d.getEnabled() == 1,
                latestRegistrationFee(d.getId()));
    }

    // ===== 内部校验 =====

    @Override
    public Doctor requireByNo(String doctorNo) {
        if (doctorNo == null || doctorNo.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "医生编号不能为空");
        }
        Doctor d = doctorMapper.selectOne(new QueryWrapper<Doctor>().eq("doctor_no", doctorNo));
        // 兼容 /auth/me 返回的 BIGINT 主键 id（auth-service 把 sys_user.doctor_id 作为 doctorId 返回）：
        // doctor_no 查不到时，若入参是纯数字，回退按主键 id 查一次。
        if (d == null && doctorNo.chars().allMatch(Character::isDigit)) {
            d = doctorMapper.selectById(Long.parseLong(doctorNo));
        }
        if (d == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "医生不存在: " + doctorNo);
        }
        return d;
    }

    @Override
    public EntityIdDTO internalResolveId(String doctorNo) {
        return EntityIdDTO.of(requireByNo(doctorNo).getId());
    }

    @Override
    public List<String> internalDepartmentNosWithDoctors() {
        // 查启用医生的 department_id（去重），再批量查 department 拿 department_no。
        // 业务层组装（非 SQL JOIN），符合架构红线。
        List<Doctor> activeDoctors = doctorMapper.selectList(
                new QueryWrapper<Doctor>().eq("enabled", 1).select("DISTINCT department_id"));
        List<Long> deptIds = new ArrayList<>();
        for (Doctor d : activeDoctors) {
            if (d.getDepartmentId() != null && !deptIds.contains(d.getDepartmentId())) {
                deptIds.add(d.getDepartmentId());
            }
        }
        if (deptIds.isEmpty()) {
            return List.of();
        }
        List<Department> depts = departmentMapper.selectBatchIds(deptIds);
        List<String> nos = new ArrayList<>();
        for (Department dept : depts) {
            if (dept.getDepartmentNo() != null && !dept.getDepartmentNo().isBlank()) {
                nos.add(dept.getDepartmentNo());
            }
        }
        return nos;
    }

    // ===== 管理员维护（HOSPITAL_ADMIN） =====

    @Override
    @Transactional
    public DoctorDTO.MutationResponse create(DoctorDTO.CreateRequest req) {
        // 1. 校验 department_no 存在并解析为 BIGINT 主键
        Department dept = departmentMapper.selectOne(
                new QueryWrapper<Department>().eq("department_no", req.departmentId()));
        if (dept == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "科室不存在: " + req.departmentId());
        }
        // 2. doctor_no 唯一性自检（uk_doctor_no 兜底，提前给出友好错误）
        String doctorNo = generateDoctorNo();
        // 3. 落库
        Doctor d = new Doctor();
        d.setDoctorNo(doctorNo);
        d.setName(req.name());
        d.setDepartmentId(dept.getId());
        d.setTitle(req.title());
        d.setSpecialties(toJsonArray(req.specialties()));
        d.setIntroduction(req.introduction());
        d.setEnabled(req.enabled() == null || req.enabled() ? 1 : 0);
        doctorMapper.insert(d);
        log.info("新增医生 doctorNo={}, name={}, departmentNo={}", d.getDoctorNo(), d.getName(), req.departmentId());
        return new DoctorDTO.MutationResponse(d.getDoctorNo());
    }

    @Override
    @Transactional
    public DoctorDTO.MutationResponse update(String doctorNo, DoctorDTO.UpdateRequest req) {
        Doctor d = requireByNo(doctorNo); // 复用：未找到抛 NOT_FOUND
        if (StringUtils.hasText(req.name())) {
            d.setName(req.name());
        }
        if (StringUtils.hasText(req.departmentId())) {
            Department dept = departmentMapper.selectOne(
                    new QueryWrapper<Department>().eq("department_no", req.departmentId()));
            if (dept == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "科室不存在: " + req.departmentId());
            }
            d.setDepartmentId(dept.getId());
        }
        if (req.title() != null) {
            d.setTitle(req.title());
        }
        if (req.specialties() != null) {
            d.setSpecialties(toJsonArray(req.specialties()));
        }
        if (req.introduction() != null) {
            d.setIntroduction(req.introduction());
        }
        if (req.enabled() != null) {
            d.setEnabled(req.enabled() ? 1 : 0);
        }
        doctorMapper.updateById(d);
        log.info("更新医生 doctorNo={}, name={}", d.getDoctorNo(), d.getName());
        return new DoctorDTO.MutationResponse(d.getDoctorNo());
    }

    @Override
    @Transactional
    public void delete(String doctorNo) {
        Doctor d = requireByNo(doctorNo);
        // 逻辑删除（BaseEntity @TableLogic 自动处理 deleted 字段）
        doctorMapper.deleteById(d.getId());
        log.info("删除医生 doctorNo={}, name={}", d.getDoctorNo(), d.getName());
    }

    /** 生成医生业务编号：D + 雪花 ID base36 大写（对齐 department 生成范式） */
    private static String generateDoctorNo() {
        return "D" + Long.toUnsignedString(IdWorker.getId(), Character.MAX_RADIX).toUpperCase();
    }

    /**
     * 逗号分隔字符串 → JSON 数组串。
     * <p>前端 form.specialties 是 textarea 输入"高血压,冠心病"格式，后端存 JSON 数组串
     * {@code ["高血压","冠心病"]} 以与现有 specialties 字段语义一致（list 接口反序列化展示）。
     */
    private String toJsonArray(String csv) {
        if (csv == null || csv.isBlank()) {
            return "[]";
        }
        List<String> items = Arrays.stream(csv.split("[,，]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        try {
            return objectMapper.writeValueAsString(items);
        } catch (JsonProcessingException e) {
            log.warn("序列化 specialties 失败，回退空数组: {}", csv, e);
            return "[]";
        }
    }

    // ===== 私有助手 =====

    /** 批量按 id 查询科室（一次 selectBatchIds），返回 id → Department 映射 */
    private Map<Long, Department> loadDepartments(List<Long> deptIds) {
        if (deptIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Department> depts = departmentMapper.selectBatchIds(deptIds);
        Map<Long, Department> map = new HashMap<>();
        for (Department d : depts) {
            map.put(d.getId(), d);
        }
        return map;
    }

    /** JSON 数组串 → List<String>。null/空/解析失败返回空列表 */
    private Map<Long, BigDecimal> loadRegistrationFees(List<Long> doctorIds) {
        if (doctorIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<DoctorSchedule> schedules = doctorScheduleMapper.selectList(new QueryWrapper<DoctorSchedule>()
                .in("doctor_id", doctorIds)
                .orderByDesc("schedule_date")
                .orderByAsc("period"));
        Map<Long, BigDecimal> feeMap = new HashMap<>();
        for (DoctorSchedule s : schedules) {
            if (s.getDoctorId() != null && s.getRegistrationFee() != null) {
                feeMap.putIfAbsent(s.getDoctorId(), s.getRegistrationFee());
            }
        }
        return feeMap;
    }

    private BigDecimal latestRegistrationFee(Long doctorId) {
        if (doctorId == null) {
            return null;
        }
        return loadRegistrationFees(List.of(doctorId)).get(doctorId);
    }

    private List<String> fromJsonArray(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("反序列化 specialties 失败，原样返回单元素: {}", json, e);
            return List.of(json);
        }
    }
}
