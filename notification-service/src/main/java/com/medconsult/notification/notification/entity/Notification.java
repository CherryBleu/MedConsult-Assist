package com.medconsult.notification.notification.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.medconsult.common.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 通知表（对应《数据库设计文档》§2.11 notification，含《修改建议》§5.1 补 deleted/updated_at）。
 *
 * <p>站内消息记录（需求文档 §4.3.2：不强制短信/邮件/第三方推送，只做站内消息）。
 * 通知类型：APPOINTMENT / SCHEDULE / MEDICATION / AI_RISK / SYSTEM（《接口文档》§5 枚举）。
 */
@Getter
@Setter
@TableName("notification")
public class Notification extends BaseEntity {

    /** 通知编号，如 N202607060001（业务可读，对外暴露） */
    private String notificationNo;

    /** 接收人业务编号（如 patient_no / doctor_no） */
    private String receiverId;

    /** 接收人角色：PATIENT / DOCTOR / PHARMACY_ADMIN / HOSPITAL_ADMIN */
    private String receiverRole;

    /** 通知类型：APPOINTMENT / SCHEDULE / MEDICATION / AI_RISK / SYSTEM */
    private String type;

    /** 标题 */
    private String title;

    /** 内容 */
    private String content;

    /** 关联业务类型（如 APPOINTMENT / PRESCRIPTION，可空） */
    private String relatedType;

    /** 关联业务编号（如 appointment_no / prescription_no，可空） */
    private String relatedId;

    /** 是否已读：0 未读，1 已读 */
    private Integer readStatus;

    /** 已读时间（readStatus=1 时填） */
    private LocalDateTime readAt;
}
