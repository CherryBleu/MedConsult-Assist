package com.medconsult.outpatient.department.service;

import com.medconsult.common.core.PageResult;
import com.medconsult.outpatient.department.dto.DepartmentDTO;
import com.medconsult.outpatient.department.entity.Department;

/**
 * 科室服务接口（对齐《接口文档》§2.3.1）。
 *
 * <p>科室为只读查询（§2.3.1 列表）；创建/维护接口文档未定义，暂不实现。
 * 内部 {@link #requireByNo(String)} 供 schedule 服务校验科室存在与启用。
 */
public interface DepartmentService {

    /** §2.3.1 分页查询科室，可按 enabled 过滤 */
    PageResult<DepartmentDTO.ListItem> list(int page, int pageSize, Boolean enabled);

    /**
     * 按科室编号查询，未找到抛 NOT_FOUND。
     * <p>供 ScheduleService 创建排班时校验科室存在与启用（需求 §4.3.1 规则 1）。
     */
    Department requireByNo(String departmentNo);
}
