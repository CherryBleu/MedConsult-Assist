package com.medconsult.medicalrecord.medicalrecord.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.medconsult.common.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 电子病历表（对应《数据库设计文档》§2.7 medical_record，含《修改建议》§2.1 调整）。
 *
 * <p>保存医生接诊过程中的病历内容，作为病历摘要、用药分析等 AI 能力的数据来源
 * （《需求文档》§4.1.4）。
 *
 * <p><b>修订项（§2.1 权威）</b>：原设计把处方塞进 {@code prescriptions} TEXT JSON，
 * 现处方独立成 {@code prescription} + {@code prescription_item} 表（通过 record_id 反查）。
 * 本表的 {@code prescriptionsSnapshot} 列降级为<b>只读快照</b>（归档时拍一份，便于病历展示，
 * 不再作为处方数据来源）。创建病历接口<b>不再</b>接受 prescriptions 入参。
 *
 * <p>状态机（《数据库设计文档》§2.7）：
 * <ul>
 *   <li>DRAFT：草稿，医生接诊中，可改可删</li>
 *   <li>ARCHIVED：已归档，医疗文书不可变，不可再改（符合《需求文档》§6.1 医疗文书留痕要求）</li>
 *   <li>REVISED：已修订（归档后因质控/纠错触发的修订版本，本批不实现修订流转，仅预留状态值）</li>
 * </ul>
 */
@Getter
@Setter
@TableName("medical_record")
public class MedicalRecord extends BaseEntity {

    /** 病历编号，如 MR202607060001（业务可读，对外暴露） */
    private String recordNo;

    /** 患者 ID（BIGINT 主键，跨服务引用 patient-service） */
    private Long patientId;

    /** 医生 ID（BIGINT 主键，跨服务引用 outpatient-service） */
    private Long doctorId;

    /** 预约 ID（BIGINT 主键，跨服务引用 outpatient-service，可空——复诊/急诊可能无预约） */
    private Long appointmentId;

    /** 主诉（VARCHAR(1000)） */
    private String chiefComplaint;

    /** 现病史（TEXT） */
    private String presentIllness;

    /** 既往史（TEXT） */
    private String pastHistory;

    /** 体格检查（TEXT） */
    private String physicalExam;

    /** 初步诊断（TEXT，JSON 数组字符串或文本，如 ["心律失常待查","高血压"]） */
    private String initialDiagnosis;

    /** 最终诊断（TEXT，归档时填写） */
    private String finalDiagnosis;

    /**
     * 处方只读快照（TEXT）。
     * <p>修订项 §2.1：降级为快照用途，归档时拍一份；处方真实数据走 prescription 表。
     * 本批 create/updateDraft 不写入此列（留 null），留待归档时或后续 PR 填充。
     */
    private String prescriptionsSnapshot;

    /** 医嘱（TEXT） */
    private String doctorAdvice;

    /** 状态：DRAFT / ARCHIVED / REVISED */
    private String status;

    /** 归档时间（status=ARCHIVED 时填写） */
    private LocalDateTime archivedAt;
}
