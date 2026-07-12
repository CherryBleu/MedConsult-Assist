package com.medconsult.common.feign.dto;

/**
 * 内部建档请求（供 auth-service 注册 PATIENT 时调 patient-service 自动建档）。
 *
 * <p>与 patient-service 对外的 {@code CreateRequest} 区分：本 record 仅含建档最小字段集，
 * 供跨服务 Feign 调用，避免依赖 patient-service 的 DTO 包。字段语义：
 * <ul>
 *   <li>{@code name} —— 患者姓名（来自注册请求）</li>
 *   <li>{@code idNo} —— 身份证号（PATIENT 角色注册必填）</li>
 *   <li>{@code phone} —— 手机号（来自注册请求）</li>
 *   <li>{@code idType} —— 证件类型，默认 ID_CARD（当前只支持身份证场景）</li>
 * </ul>
 *
 * @param name    患者姓名
 * @param idNo    身份证号
 * @param phone   手机号
 * @param idType  证件类型（可空，patient-service 默认 ID_CARD）
 */
public record PatientRegisterRequest(
        String name,
        String idNo,
        String phone,
        String idType
) {}
