package com.medconsult.outpatient.department.service;

import com.medconsult.common.core.PageResult;
import com.medconsult.common.feign.dto.EntityIdDTO;
import com.medconsult.outpatient.department.dto.DepartmentDTO;
import com.medconsult.outpatient.department.entity.Department;

/**
 * 科室服务接口（对齐《接口文档》§2.3）。
 *
 * <p>§2.3.1 列表为只读查询；§2.3.2/2.3.3/2.3.4 新增/编辑/删除为管理员维护接口（#15）。
 * 内部 {@link #requireByNo(String)} 供 schedule 服务校验科室存在与启用。
 */
public interface DepartmentService {

    /** §2.3.1 分页查询科室，可按 enabled 过滤 */
    PageResult<DepartmentDTO.ListItem> list(int page, int pageSize, Boolean enabled);

    /** §2.3.2 新增科室（departmentNo 后端自动生成） */
    DepartmentDTO.MutationResponse create(DepartmentDTO.CreateRequest req);

    /** §2.3.3 更新科室（部分字段，未传保留原值） */
    DepartmentDTO.MutationResponse update(String departmentNo, DepartmentDTO.UpdateRequest req);

    /** §2.3.4 删除科室（逻辑删除；被医生/排班引用时拒绝） */
    void delete(String departmentNo);

    /**
     * 按科室编号查询，未找到抛 NOT_FOUND。
     * <p>供 ScheduleService 创建排班时校验科室存在与启用（需求 §4.3.1 规则 1）。
     */
    Department requireByNo(String departmentNo);

    /**
     * 内部接口：按 department_no 反查 BIGINT 主键（架构文档 §2.3 补充）。
     * <p>供 medical-record-service 落库存真实主键。未找到抛 NOT_FOUND。
     */
    EntityIdDTO internalResolveId(String departmentNo);
}
