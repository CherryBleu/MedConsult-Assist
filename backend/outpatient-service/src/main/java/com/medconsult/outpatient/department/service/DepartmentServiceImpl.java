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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 科室服务实现。
 *
 * <p>核心逻辑（对齐《需求文档》§4.3.1）：
 * <ul>
 *   <li>分页查询：可按 enabled 过滤（true=仅启用）</li>
 *   <li>{@code requireByNo}：按 department_no 查，未找到抛 NOT_FOUND（供 schedule 服务校验）</li>
 *   <li>本服务对科室只读，无创建/更新</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentMapper departmentMapper;
    /** 删科室时校验是否有关联启用医生（外键约束） */
    private final DoctorMapper doctorMapper;

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

    // ===== 管理员维护接口（2026-07-17 补齐） =====

    @Override
    public DepartmentDTO.SaveResponse create(DepartmentDTO.CreateRequest req) {
        Department d = new Department();
        d.setDepartmentNo(generateDepartmentNo());
        d.setName(req.getDepartmentName());
        d.setDescription(req.getDescription());
        d.setLocation(req.getLocation());
        d.setEnabled(req.getEnabled() == null ? 1 : req.getEnabled());
        departmentMapper.insert(d);
        return new DepartmentDTO.SaveResponse(d.getDepartmentNo());
    }

    @Override
    public DepartmentDTO.SaveResponse update(String departmentNo, DepartmentDTO.UpdateRequest req) {
        Department d = requireByNo(departmentNo);
        if (req.getDepartmentName() != null) d.setName(req.getDepartmentName());
        if (req.getDescription() != null) d.setDescription(req.getDescription());
        if (req.getLocation() != null) d.setLocation(req.getLocation());
        if (req.getEnabled() != null) d.setEnabled(req.getEnabled());
        departmentMapper.updateById(d);
        return new DepartmentDTO.SaveResponse(d.getDepartmentNo());
    }

    @Override
    public void delete(String departmentNo) {
        Department d = requireByNo(departmentNo);
        // 外键约束：科室下有启用医生时拒绝删除（避免孤儿医生/排班）
        Long activeDoctors = doctorMapper.selectCount(new QueryWrapper<Doctor>()
                .eq("department_id", d.getId()).eq("enabled", 1));
        if (activeDoctors != null && activeDoctors > 0) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "科室下有 " + activeDoctors + " 名启用医生，无法删除（请先调整医生归属或停用）");
        }
        departmentMapper.deleteById(d.getId());  // 软删（BaseEntity @TableLogic）
    }

    /** 生成科室编号：DEP + 雪花序列 base36（与 generateScheduleNo 同范式） */
    private static String generateDepartmentNo() {
        long id = IdWorker.getId();
        return "DEP" + Long.toUnsignedString(id, Character.MAX_RADIX).toUpperCase();
    }
}
