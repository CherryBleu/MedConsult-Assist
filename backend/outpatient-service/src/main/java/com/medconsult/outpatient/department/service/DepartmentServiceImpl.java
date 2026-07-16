package com.medconsult.outpatient.department.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medconsult.common.core.BusinessException;
import com.medconsult.common.core.ErrorCode;
import com.medconsult.common.core.PageResult;
import com.medconsult.common.core.PageQuery;
import com.medconsult.common.feign.dto.EntityIdDTO;
import com.medconsult.outpatient.department.dto.DepartmentDTO;
import com.medconsult.outpatient.department.entity.Department;
import com.medconsult.outpatient.department.mapper.DepartmentMapper;
import com.medconsult.outpatient.doctor.entity.Doctor;
import com.medconsult.outpatient.doctor.mapper.DoctorMapper;
import com.medconsult.outpatient.schedule.entity.DoctorSchedule;
import com.medconsult.outpatient.schedule.mapper.DoctorScheduleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 科室服务实现。
 *
 * <p>核心逻辑（对齐《需求文档》§4.3.1）：
 * <ul>
 *   <li>分页查询：可按 enabled 过滤（true=仅启用）</li>
 *   <li>{@code requireByNo}：按 department_no 查，未找到抛 NOT_FOUND（供 schedule 服务校验）</li>
 *   <li>§2.3.2/2.3.3/2.3.4 新增/编辑/删除（#15）：departmentNo 后端自动生成；删除前校验是否被医生/排班引用</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentMapper departmentMapper;
    private final DoctorMapper doctorMapper;
    private final DoctorScheduleMapper doctorScheduleMapper;

    // ===== §2.3.1 分页查询 =====

    @Override
    public PageResult<DepartmentDTO.ListItem> list(int page, int pageSize, Boolean enabled) {
        Page<Department> p = new Page<>(PageQuery.normalizePage(page), PageQuery.normalizePageSize(pageSize));
        QueryWrapper<Department> qw = new QueryWrapper<>();
        if (enabled != null) {
            qw.eq("enabled", enabled ? 1 : 0);
        }
        qw.orderByDesc("created_at");
        IPage<Department> result = departmentMapper.selectPage(p, qw);

        List<DepartmentDTO.ListItem> items = new ArrayList<>();
        for (Department d : result.getRecords()) {
            items.add(new DepartmentDTO.ListItem(
                    d.getDepartmentNo(),
                    d.getName(),
                    d.getLocation(),
                    d.getEnabled() != null && d.getEnabled() == 1));
        }
        return PageResult.of((int) result.getCurrent(), (int) result.getSize(), result.getTotal(), items);
    }

    // ===== §2.3.2/2.3.3/2.3.4 新增/编辑/删除（#15） =====

    @Override
    @Transactional
    public DepartmentDTO.MutationResponse create(DepartmentDTO.CreateRequest req) {
        // 科室名称唯一性预检（uk_department_no 仅约束编号，名称重复由业务校验）
        Long nameCount = departmentMapper.selectCount(
                new QueryWrapper<Department>().eq("name", req.name()));
        if (nameCount != null && nameCount > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "科室名称已存在: " + req.name());
        }
        Department d = new Department();
        d.setDepartmentNo(generateDepartmentNo());
        d.setName(req.name());
        d.setDescription(req.description());
        d.setLocation(req.location());
        d.setEnabled(req.enabled() == null || req.enabled() ? 1 : 0);
        departmentMapper.insert(d);
        log.info("新增科室 departmentNo={}, name={}", d.getDepartmentNo(), d.getName());
        return new DepartmentDTO.MutationResponse(d.getDepartmentNo());
    }

    @Override
    @Transactional
    public DepartmentDTO.MutationResponse update(String departmentNo, DepartmentDTO.UpdateRequest req) {
        Department d = requireByNo(departmentNo); // 复用：未找到抛 NOT_FOUND
        // 名称变更时校验唯一性（排除自身）
        if (req.name() != null && !req.name().isBlank() && !req.name().equals(d.getName())) {
            Long nameCount = departmentMapper.selectCount(
                    new QueryWrapper<Department>().eq("name", req.name()).ne("id", d.getId()));
            if (nameCount != null && nameCount > 0) {
                throw new BusinessException(ErrorCode.CONFLICT, "科室名称已存在: " + req.name());
            }
            d.setName(req.name());
        }
        if (req.description() != null) {
            d.setDescription(req.description());
        }
        if (req.location() != null) {
            d.setLocation(req.location());
        }
        if (req.enabled() != null) {
            d.setEnabled(req.enabled() ? 1 : 0);
        }
        departmentMapper.updateById(d);
        return new DepartmentDTO.MutationResponse(d.getDepartmentNo());
    }

    @Override
    @Transactional
    public void delete(String departmentNo) {
        Department d = requireByNo(departmentNo);
        // 删除前校验：被医生或排班引用的科室不可删（避免破坏医生管理/排班/分诊链路）
        Long docCount = doctorMapper.selectCount(
                new QueryWrapper<Doctor>().eq("department_id", d.getId()));
        if (docCount != null && docCount > 0) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "科室已被 " + docCount + " 名医生引用，不可删除（请先调整医生归属科室）");
        }
        Long scheduleCount = doctorScheduleMapper.selectCount(
                new QueryWrapper<DoctorSchedule>().eq("department_id", d.getId()));
        if (scheduleCount != null && scheduleCount > 0) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "科室存在 " + scheduleCount + " 条关联排班，不可删除（请先清理排班）");
        }
        // 逻辑删除（BaseEntity @TableLogic）
        departmentMapper.deleteById(d.getId());
        log.info("删除科室 departmentNo={}, name={}", d.getDepartmentNo(), d.getName());
    }

    /** 生成科室业务编号：DEP_ + 雪花 ID base36 大写（对齐 schedule 生成范式） */
    private static String generateDepartmentNo() {
        return "DEP_" + Long.toUnsignedString(IdWorker.getId(), Character.MAX_RADIX).toUpperCase();
    }

    // ===== 内部校验 =====

    @Override
    public Department requireByNo(String departmentNo) {
        if (departmentNo == null || departmentNo.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "科室编号不能为空");
        }
        Department d = departmentMapper.selectOne(
                new QueryWrapper<Department>().eq("department_no", departmentNo));
        if (d == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "科室不存在: " + departmentNo);
        }
        return d;
    }

    @Override
    public EntityIdDTO internalResolveId(String departmentNo) {
        return EntityIdDTO.of(requireByNo(departmentNo).getId());
    }
}
