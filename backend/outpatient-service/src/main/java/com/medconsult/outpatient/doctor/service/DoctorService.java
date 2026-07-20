package com.medconsult.outpatient.doctor.service;

import com.medconsult.common.core.PageResult;
import com.medconsult.common.feign.dto.EntityIdDTO;
import com.medconsult.outpatient.doctor.dto.DoctorDTO;
import com.medconsult.outpatient.doctor.entity.Doctor;

import java.util.List;

/**
 * 医生服务接口（对齐《接口文档》§2.3.2）。
 *
 * <p>医生为只读查询（§2.3.2 列表）；创建/维护接口文档未定义，暂不实现。
 * 内部 {@link #requireByNo(String)} 供 schedule 服务校验医生存在与启用。
 */
public interface DoctorService {

    /**
     * §2.3.2 分页查询医生，可按 departmentId(department_no)/enabled 过滤。
     * <p>返回时业务层组装 departmentName（不跨表 join）。
     */
    PageResult<DoctorDTO.ListItem> list(int page, int pageSize, String departmentId, Boolean enabled);

    /**
     * 按医生编号查询，未找到抛 NOT_FOUND。
     * <p>供 ScheduleService 创建排班时校验医生存在与启用（需求 §4.3.1 规则 2）。
     */
    Doctor requireByNo(String doctorNo);

    /**
     * 内部接口：按 doctor_no 反查 BIGINT 主键（架构文档 §2.3 补充）。
     * <p>供 medical-record-service 落库存真实主键。未找到抛 NOT_FOUND。
     */
    EntityIdDTO internalResolveId(String doctorNo);

    /**
     * 内部接口：查询有启用医生的科室编号集合（department_no）。
     * <p>供 ai-service 智能分诊过滤"无医生可预约"的科室，避免推荐全科医学科等
     * 只有科室没有医生的空科室，导致用户挂号时无医生可选。
     * <p>逻辑：SELECT DISTINCT department_id FROM doctor WHERE enabled=1，
     * 再回查 department 拿 department_no（业务层组装，非 SQL JOIN）。
     */
    List<String> internalDepartmentNosWithDoctors();

    /**
     * 管理员新增医生：生成 doctor_no（D + 雪花 base36），校验 department_no 存在，
     * 落 doctor 表。specialties 入参为逗号分隔字符串，service 层序列化为 JSON 数组串。
     */
    DoctorDTO.MutationResponse create(DoctorDTO.CreateRequest req);

    /**
     * 管理员更新医生：按 doctor_no 查现有记录后部分更新（仅非 null 字段覆盖）。
     * departmentId 入参为 department_no，service 层解析为 BIGINT 主键。
     */
    DoctorDTO.MutationResponse update(String doctorNo, DoctorDTO.UpdateRequest req);

    /**
     * 管理员删除医生：逻辑删除（deleted=1）。如有未就诊的预约排班等业务约束，
     * 由调用方自行校验，本方法仅做 doctor 表逻辑删除。
     */
    void delete(String doctorNo);
}
