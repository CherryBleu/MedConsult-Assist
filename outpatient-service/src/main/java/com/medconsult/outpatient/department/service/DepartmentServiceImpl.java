package com.medconsult.outpatient.department.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medconsult.common.core.BusinessException;
import com.medconsult.common.core.ErrorCode;
import com.medconsult.common.core.PageResult;
import com.medconsult.common.feign.dto.EntityIdDTO;
import com.medconsult.outpatient.department.dto.DepartmentDTO;
import com.medconsult.outpatient.department.entity.Department;
import com.medconsult.outpatient.department.mapper.DepartmentMapper;
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

    // ===== §2.3.1 分页查询 =====

    @Override
    public PageResult<DepartmentDTO.ListItem> list(int page, int pageSize, Boolean enabled) {
        Page<Department> p = new Page<>(page <= 0 ? 1 : page, pageSize <= 0 ? 10 : pageSize);
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
}
