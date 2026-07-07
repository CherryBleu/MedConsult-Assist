/**
 * common-security：鉴权与权限（待实现，对应架构文档 §4）。
 *
 * <p>计划内容：
 * <ul>
 *   <li>{@code JwtCodec} - JWT 签发/解析，支持用户身份与服务身份双模</li>
 *   <li>{@code @Permission} - 接口级权限注解（code + dataScope）</li>
 *   <li>{@code PermissionAspect} - 切面，校验权限码并按 dataScope 注入查询条件</li>
 *   <li>{@code ServiceTokenFilter} - /internal/* 的服务 API Key 校验</li>
 *   <li>{@code SecurityContext} - 当前请求的用户/服务身份持有器</li>
 * </ul>
 *
 * <p>本模块当前为占位。
 */
package com.medconsult.common.security;
