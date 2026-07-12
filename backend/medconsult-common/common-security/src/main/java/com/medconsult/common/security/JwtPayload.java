package com.medconsult.common.security;

import java.util.List;

/**
 * JWT 载荷统一模型（架构文档 §4.2）。
 *
 * <p>用户身份与服务身份共用同一结构，靠 {@link SubjectType} 区分。
 * 这样 Token 解析、{@link SecurityContext}、{@code @Permission} 切面只需处理一种类型，
 * 避免双套鉴权链路。
 *
 * <p><b>用户身份</b>（subjectType = USER）：
 * <ul>
 *   <li>{@code userId} = 系统用户 ID</li>
 *   <li>{@code roles} = 角色码列表（PATIENT/DOCTOR/PHARMACY_ADMIN/HOSPITAL_ADMIN）</li>
 *   <li>{@code patientId} / {@code doctorId} 可选，支持一人多角色（RBAC 五表，§4.3）</li>
 *   <li>{@code userNo} = 用户业务编号（U+雪花base36），供通知等按业务编号关联的服务用</li>
 *   <li>{@code scope} = 权限点列表（如 patient:read、prescription:review）</li>
 * </ul>
 *
 * <p><b>服务身份</b>（subjectType = SERVICE，对应 sys_service_account）：
 * <ul>
 *   <li>{@code userId} 留空</li>
 *   <li>{@code serviceCode} = 服务编码（如 ai-service）</li>
 *   <li>{@code scope} = 该服务被授予的权限点（如 patient:read,drug:read）</li>
 *   <li>{@code roles} 通常为空（服务不走 sys_role）</li>
 * </ul>
 *
 * @param subjectType  主体类型
 * @param userId       用户 ID（服务身份为 null）
 * @param serviceCode  服务编码（用户身份为 null）
 * @param name         主体名（用户姓名 / 服务名）
 * @param roles        角色码列表（可空）
 * @param primaryRole  主角色（用户身份必填，便于数据范围过滤快速判定）
 * @param patientId    关联患者档案 ID（可空，一人多角色）
 * @param doctorId     关联医生 ID（可空）
 * @param userNo       用户业务编号（U+雪花base36，可空；供通知等按业务编号关联的服务用，
 *                     旧 token 无此 claim 时为 null）
 * @param scope        权限点列表（可空）
 * @param jti          Token 唯一 ID（登出黑名单用）
 * @param exp          过期时间（Unix 秒）
 */
public record JwtPayload(
        SubjectType subjectType,
        Long userId,
        String serviceCode,
        String name,
        List<String> roles,
        String primaryRole,
        Long patientId,
        Long doctorId,
        String userNo,
        List<String> scope,
        String jti,
        Long exp
) {
    public enum SubjectType {
        USER,
        SERVICE
    }

    public boolean isUser() {
        return subjectType == SubjectType.USER;
    }

    public boolean isService() {
        return subjectType == SubjectType.SERVICE;
    }

    /**
     * 是否拥有指定权限码。scope 含通配 {@code *} 视为拥有全部权限（仅限超管/根服务，慎用）。
     */
    public boolean hasPermission(String code) {
        if (scope == null || scope.isEmpty()) {
            return false;
        }
        return scope.contains(code) || scope.contains("*");
    }

    /**
     * 是否具有某角色。
     */
    public boolean hasRole(String roleCode) {
        return roles != null && roles.contains(roleCode);
    }
}
