package com.medconsult.patient.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.medconsult.common.crypto.EncryptedStringTypeHandler;
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
 * <p><b>敏感字段加密（2026-07-17，§5.3）</b>：{@code id_no} 用 AES-256-GCM 字段级加密
 * （经 {@link EncryptedStringTypeHandler} 透明加解密）；{@code id_no_hash} 存 SHA-256 指纹
 * 支撑唯一性校验/精确检索（密文每次 IV 不同不可直接比较）。{@code @TableName(autoResultMap=true)}
 * 是 MyBatis-Plus 让字段级 TypeHandler 生效的前提。
 * <p>{@code allergies} / {@code past_medical_history} / {@code family_history} 存 JSON 数组串，
 * {@code emergency_contact} 存 JSON 对象串。
 */
@Getter
@Setter
@TableName(value = "patient", autoResultMap = true)
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

    /**
     * 证件号（AES-256-GCM 加密存储，TypeHandler 透明加解密）。
     * <p>业务侧读写均为明文；唯一性/检索用 {@link #idNoHash}。
     */
    @TableField(value = "id_no", typeHandler = EncryptedStringTypeHandler.class)
    private String idNo;

    /** 证件号指纹 SHA-256 hex（64 字符）。唯一键挂此列，业务侧用 .eq("id_no_hash", ...) 查重/检索 */
    private String idNoHash;

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

