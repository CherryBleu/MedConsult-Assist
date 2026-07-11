package com.medconsult.outpatient.doctor.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

        List<DoctorDTO.ListItem> items = new ArrayList<>();
        for (Doctor d : result.getRecords()) {
            Department dept = deptMap.get(d.getDepartmentId());
            items.add(new DoctorDTO.ListItem(
                    d.getDoctorNo(),
                    d.getName(),
                    dept != null ? dept.getDepartmentNo() : null,
                    dept != null ? dept.getName() : null,
                    d.getTitle(),
                    fromJsonArray(d.getSpecialties()),
                    d.getEnabled() != null && d.getEnabled() == 1));
        }
        return PageResult.of((int) result.getCurrent(), (int) result.getSize(), result.getTotal(), items);
    }

    // ===== 内部校验 =====

    @Override
    public Doctor requireByNo(String doctorNo) {
        if (doctorNo == null || doctorNo.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "医生编号不能为空");
        }
        Doctor d = doctorMapper.selectOne(new QueryWrapper<Doctor>().eq("doctor_no", doctorNo));
        if (d == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "医生不存在: " + doctorNo);
        }
        return d;
    }

    @Override
    public EntityIdDTO internalResolveId(String doctorNo) {
        return EntityIdDTO.of(requireByNo(doctorNo).getId());
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
