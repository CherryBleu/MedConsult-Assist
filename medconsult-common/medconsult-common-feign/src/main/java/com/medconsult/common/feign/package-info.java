/**
 * common-feign：服务间通信的公共能力（待实现，对应架构文档 §2.4 / §3.2）。
 *
 * <p>计划内容：
 * <ul>
 *   <li>{@code AuthRelayInterceptor} - 双模鉴权拦截器：用户触发链路透传用户 Token；
 *       自动/无用户链路注入服务自身 JWT；永远透传 traceId（架构文档 §2.4）</li>
 *   <li>{@code FeignErrorDecoder} - 把 4xx/5xx 错误响应解码回 {@link com.medconsult.common.core.BusinessException}</li>
 *   <li>{@code RequestContext} - 当前调用链的身份/traceId 持有器（ThreadLocal 或虚拟线程友好实现）</li>
 *   <li>内部接口 DTO 契约（PatientContext、DrugRiskInfo、RecordFullText 等，跨服务共享）</li>
 * </ul>
 *
 * <p>关键约束（架构文档 §2.5）：ai-service 不允许持有任何写类 Feign 客户端，禁止循环依赖。
 *
 * <p>本模块当前为占位。
 */
package com.medconsult.common.feign;
