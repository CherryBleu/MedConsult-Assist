/**
 * common-web：Web 层公共能力（待实现，对应架构文档 §3.2）。
 *
 * <p>计划内容：
 * <ul>
 *   <li>{@code GlobalExceptionHandler} - 全局异常处理器，把 {@link com.medconsult.common.core.BusinessException}
 *       与未知异常转成 {@link com.medconsult.common.core.Result}，并设置正确 HTTP 状态码</li>
 *   <li>{@code TraceIdFilter} - 透传/生成 {@code X-Trace-Id}，写入 MDC（架构文档 §7.4）</li>
 *   <li>{@code MaskingSerializer} / {@code @Mask} - 敏感字段脱敏（手机号/身份证/姓名）</li>
 *   <li>{@code ResultBodyAdvice} - 统一包装 Controller 返回值 + 回填 traceId/timestamp</li>
 * </ul>
 *
 * <p>本模块当前为占位，依赖与实现在后续 PR 加入。
 */
package com.medconsult.common.web;
