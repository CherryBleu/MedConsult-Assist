package com.medconsult.notification.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 审计日志表（对应《修改建议》§2.2 audit_log）。
 *
 * <p><b>流水表，不继承 BaseEntity</b>：审计日志只追加，无 updated_at、无 deleted（§2.2 明确）。
 * 故自行声明 id + createdAt，不带逻辑删除/更新时间。
 *
 * <p>审计范围（架构文档 §0 冻结决策 #9）：所有写操作（CREATE/UPDATE/DELETE）+ 敏感读
 * （病历查阅 VIEW、导出 EXPORT、登录 LOGIN/LOGOUT）→ audit_log；普通业务查询不审计。
 *
 * <p><b>分表 TODO</b>（§2.2 建议）：当前单表 audit_log。MyBatis-Plus 不原生支持分表，
 * 冒烟阶段单表够用。当单表超百万行时改为按月分表 audit_log_YYYYMM（需引入 shardingsphere 或
 * 自定义动态表名，届时评估）。本批在 schema 注释 + 此处 TODO 标注。
 */
@Getter
@Setter
@TableName("audit_log")
public class AuditLog {

    /** 主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 审计编号（业务可读） */
    private String auditNo;

    /** 链路追踪 ID（跨服务串联） */
    private String traceId;

    /** 资源类型：PATIENT / MEDICAL_RECORD / PRESCRIPTION / DRUG / SCHEDULE... */
    private String resourceType;

    /** 资源业务编号 */
    private String resourceId;

    /** 资源名称冗余（便于检索） */
    private String resourceName;

    /** 操作类型：VIEW / CREATE / UPDATE / DELETE / EXPORT / LOGIN / LOGOUT */
    private String action;

    /** 操作人 ID */
    private String operatorId;

    /** 操作人角色 */
    private String operatorRole;

    /** 操作人姓名冗余 */
    private String operatorName;

    /** 资源所属患者 ID（便于按患者检索审计） */
    private Long targetOwnerId;

    /** 变更前后快照（JSON 串） */
    private String detail;

    /** 操作 IP */
    private String ip;

    /** User-Agent */
    private String userAgent;

    /** 结果：SUCCESS / FAILED */
    private String result;

    /** 创建时间（审计日志只追加，无 updated_at） */
    private LocalDateTime createdAt;
}
