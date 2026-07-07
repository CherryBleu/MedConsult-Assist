/**
 * common-feign：服务间通信公共能力（对应架构文档 §2.4 / §3.2）。
 *
 * <p>已实现：
 * <ul>
 *   <li>{@link com.medconsult.common.feign.AuthRelayInterceptor} - 双模鉴权拦截器
 *       （用户链路透传 token + 身份头 / 服务链路注入服务 JWT / 永远透传 traceId，§2.4）</li>
 *   <li>{@link com.medconsult.common.feign.FeignErrorDecoder} - 下游错误响应→BusinessException</li>
 *   <li>{@link com.medconsult.common.feign.RequestContext} - 调用链上下文（身份/traceId/caller）</li>
 *   <li>{@link com.medconsult.common.feign.dto.PatientContextDTO} / {@link com.medconsult.common.feign.dto.DrugRiskInfoDTO} -
 *       内部接口共享 DTO 契约（§2.3）</li>
 *   <li>{@link com.medconsult.common.feign.MedConsultFeignAutoConfiguration} - 自动装配</li>
 * </ul>
 *
 * <p><b>双模鉴权</b>（§2.4 关键）：
 * <ul>
 *   <li>用户触发（医生开方→drug）→ 透传用户 token，被调方审计挂医生名下</li>
 *   <li>自动/无用户（定时任务、MQ 消费）→ 注入服务自身 JWT（sys_service_account.scope）</li>
 * </ul>
 *
 * <p><b>红线 10</b>（§2.5 无循环依赖）：ai-service 不允许持有写类 Feign 客户端。
 * 内部接口 DTO 只含只读查询契约。
 */
package com.medconsult.common.feign;
