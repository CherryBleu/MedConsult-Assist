package com.medconsult.patient.service;

import com.medconsult.common.core.PageResult;
import com.medconsult.common.feign.dto.EntityIdDTO;
import com.medconsult.common.feign.dto.PatientContextDTO;
import com.medconsult.patient.dto.PatientDTO;

import java.util.List;

/**
 * 患者档案服务接口（对齐《接口文档》§2.2 + 架构文档 §2.3 内部接口）。
 *
 * <p>方法对应 5 个对外接口（create/detail/list/update/updateStatus）
 * 与 2 个对内接口（internalContext/internalAllergies）。
 *
 * <p>对外接口的 {@code patientId} 参数实际是 {@code patient_no}（业务可读编号，如 P202607060001），
 * 与《接口文档》§2.2 字段命名一致；对内接口的 {@code patientId} 是 BIGINT 主键（架构文档 §2.3）。
 */
public interface PatientService {

    /** §2.2.1 创建患者档案 */
    PatientDTO.SummaryResponse create(PatientDTO.CreateRequest req);

    /** §2.2.2 查询患者档案详情（按 patient_no） */
    PatientDTO.DetailResponse detail(String patientNo);

    /** §2.2.3 分页查询患者（按姓名/手机号/证件号/患者编号模糊检索） */
    PageResult<PatientDTO.ListItem> list(int page, int pageSize, String keyword);

    /** §2.2.4 更新患者档案（按 patient_no） */
    PatientDTO.UpdateResponse update(String patientNo, PatientDTO.UpdateRequest req);

    /** §2.2.5 更新患者档案状态（按 patient_no） */
    PatientDTO.StatusResponse updateStatus(String patientNo, PatientDTO.StatusUpdateRequest req);

    // ===== 对内接口（架构文档 §2.3）=====

    /**
     * 内部接口：查患者上下文（按 BIGINT 主键）。
     * <p>供 ai-service 做用药/分诊分析，返回脱敏基础信息 + 过敏史 + 既往史。
     */
    PatientContextDTO internalContext(Long patientId);

    /**
     * 内部接口：查患者过敏史（按 BIGINT 主键）。
     * <p>供 ai-service / drug-service 用药分析快速查过敏。
     */
    List<String> internalAllergies(Long patientId);

    /**
     * 内部接口：按 patient_no 反查 BIGINT 主键（架构文档 §2.3 补充）。
     * <p>供 medical-record-service 落库存真实主键，替代正哈希占位。未找到抛 NOT_FOUND。
     */
    EntityIdDTO internalResolveId(String patientNo);

    /**
     * 内部接口：注册时自动建档（供 auth-service 注册 PATIENT 角色时调用）。
     * <p>复用 create 的校验逻辑（证件/手机唯一性），但返回 BIGINT 主键而非 patient_no，
     * 供 auth-service 回写 sys_user.patient_id。建档失败（证件/手机冲突）抛 CONFLICT。
     *
     * @param name    患者姓名（必填）
     * @param idNo    身份证号（PATIENT 注册必填）
     * @param phone   手机号
     * @param idType  证件类型（可空，默认 ID_CARD）
     * @return 新建档案的主键 id
     */
    EntityIdDTO internalCreate(String name, String idNo, String phone, String idType);

    /**
     * 内部接口：按主键 ID 批量查患者姓名（供 outpatient-service 预约列表显示患者名）。
     * <p>返回 patientId → name 映射；不存在的 ID 不含在结果中。
     */
    java.util.Map<Long, String> internalNamesByIds(java.util.Collection<Long> patientIds);
}
