package com.medconsult.auth.serviceaccount.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.medconsult.common.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 服务账号表（架构文档 §4.2，对应 sys_service_account）。
 *
 * <p>微服务（如 ai-service）用 {@code serviceCode} + {@code apiKey} 向 auth-service 换发
 * SERVICE 类型 JWT，下游服务通过 {@code SecurityContext.requireService()} 校验服务身份，
 * 实现 /internal/** 内部接口的统一鉴权（§2.4）。
 *
 * <p>{@code apiKeyHash} 存 BCrypt 摘要；明文 apiKey 仅在创建时返回一次，不入库。
 */
@Getter
@Setter
@TableName("sys_service_account")
public class SysServiceAccount extends BaseEntity {

    /** 服务编码（如 ai-service），对应 JwtPayload.serviceCode */
    private String serviceCode;

    /** 服务显示名 */
    private String serviceName;

    /** API Key 明文（仅创建/轮换时临时承载，DB 存摘要；查询时不回填） */
    private String apiKey;

    /** API Key 的 BCrypt 摘要（查询时用此字段比对） */
    private String apiKeyHash;

    /** 权限点列表（逗号分隔，如 patient:read,drug:read） */
    private String scope;

    /** 状态：ACTIVE / DISABLED */
    private String status;
}
