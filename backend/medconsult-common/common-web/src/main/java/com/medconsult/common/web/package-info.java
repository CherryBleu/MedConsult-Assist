/**
 * common-web：Web 层公共能力（对应架构文档 §3.2）。
 *
 * <p>已实现：
 * <ul>
 *   <li>{@link com.medconsult.common.web.GlobalExceptionHandler} - 全局异常处理器，把
 *       {@link com.medconsult.common.core.BusinessException}、校验失败、未知异常统一转成
 *       {@link com.medconsult.common.core.Result}，HTTP 状态码取自 ErrorCode</li>
 *   <li>{@link com.medconsult.common.web.TraceIdFilter} - 透传/生成 {@code X-Trace-Id}，写入 MDC，
 *       回写响应头（架构文档 §7.4）</li>
 *   <li>{@link com.medconsult.common.web.ResultBodyAdvice} - Controller 返回值自动包装为 Result +
 *       回填 traceId/timestamp</li>
 *   <li>{@link com.medconsult.common.web.Mask} / {@link com.medconsult.common.web.MaskingSerializer} -
 *       敏感字段脱敏（手机号/身份证/姓名/邮箱/全打码，对应《修改建议》§5.3）</li>
 *   <li>{@link com.medconsult.common.web.MedConsultWebAutoConfiguration} - 业务服务引入依赖即自动生效</li>
 * </ul>
 *
 * <p>业务服务使用：仅需在 pom 引入 {@code medconsult-common-web}，无需额外配置。
 * Controller 直接返回业务对象即可被自动包装；敏感字段加 {@code @Mask}。
 */
package com.medconsult.common.web;
