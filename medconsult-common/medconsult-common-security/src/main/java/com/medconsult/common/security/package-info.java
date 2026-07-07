/**
 * common-security：鉴权与权限（对应架构文档 §4）。
 *
 * <p>已实现：
 * <ul>
 *   <li>{@link com.medconsult.common.security.JwtPayload} - 用户/服务身份统一 JWT 载荷模型</li>
 *   <li>{@link com.medconsult.common.security.JwtCodec} - HS256 签发/解析（jjwt 0.12）</li>
 *   <li>{@link com.medconsult.common.security.SecurityContext} - 请求作用域身份持有器（虚拟线程友好）</li>
 *   <li>{@link com.medconsult.common.security.Permission} / {@link com.medconsult.common.security.DataScope} -
 *       接口权限注解 + 数据范围枚举</li>
 *   <li>{@link com.medconsult.common.security.PermissionAspect} - 切面，校验角色 + scope，支持扩展 {@code PermissionChecker}</li>
 *   <li>{@link com.medconsult.common.security.MedConsultSecurityAutoConfiguration} - 自动装配</li>
 * </ul>
 *
 * <p><b>鉴权链路</b>（架构文档 §4.2 双模）：
 * <ol>
 *   <li>Gateway 解析用户 JWT → 写 X-User-* 头（§4.4 信任边界）</li>
 *   <li>业务服务 AuthFilter（后续补）解析 Token / 头 → {@link com.medconsult.common.security.SecurityContext#setPayload}</li>
 *   <li>{@code @Permission} 切面读 SecurityContext → 校验 scope/role</li>
 *   <li>Feign 调用：{@code AuthRelayInterceptor}（common-feign）透传身份</li>
 * </ol>
 *
 * <p><b>密钥管理</b>：JWT secret 由 {@code medconsult.security.jwt.secret} 注入，
 * 生产环境走 Nacos + KMS（架构文档 §7.3）。
 */
package com.medconsult.common.security;
