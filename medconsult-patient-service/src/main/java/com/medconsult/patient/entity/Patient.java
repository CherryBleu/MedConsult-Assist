package com.medconsult.patient.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.medconsult.common.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 患者档案表（对应《数据库设计文档》§2.4 patient 表）。
 *
 * <p>承载患者基础信息、联系方式、病史和过敏史，是预约、病历和 AI 分析的核心数据来源
 * （《需求文档》§4.1.1）。
 *
 * <p><b>敏感字段</b>：{@code id_no} 在生产应做 AES-256 落库（《修改建议》§5.3），
 * 当前暂明文存储（待 common 加密模块就绪后改造，见 schema.sql TODO）。
 * {@code allergies} / {@code past_medical_history} / {@code family_history} 存 JSON 数组串
 * （如 {@code ["青霉素","头孢类"]}），{@code emergency_contact} 存 JSON 对象串。
 */
@Getter
@Setter
@TableName("patient")
public class Patient extends BaseEntity {

    /** 患者编号，如 P202607060001（业务可读，对外暴露） */
    private String patientNo;

    /** 患者姓名 */
    private String name;

    /** 性别：MALE / FEMALE / UNKNOWN */
    private String gender;

    /** 出生日期 */
    private LocalDate birthDate;

    /** 证件类型：ID_CARD / PASSPORT / OTHER */
    private String idType;

    /** 证件号（TODO AES-256 加密，见 schema.sql 注释） */
    private String idNo;

    /** 手机号 */
    private String phone;

    /** 地址 */
    private String address;

    /** 过敏史（JSON 数组串） */
    private String allergies;

    /** 既往病史（JSON 数组串） */
    private String pastMedicalHistory;

    /** 家族病史（JSON 数组串） */
    private String familyHistory;

    /** 紧急联系人（JSON 串） */
    private String emergencyContact;

    /** 档案状态：ACTIVE / DISABLED / MERGED */
    private String status;
}
