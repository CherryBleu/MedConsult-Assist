/**
 * common-core：系统基础设施层的最底层契约。
 *
 * <p>包含：
 * <ul>
 *   <li>{@link com.medconsult.common.core.Result} - 统一响应体</li>
 *   <li>{@link com.medconsult.common.core.ErrorCode} - 错误码枚举</li>
 *   <li>{@link com.medconsult.common.core.BusinessException} - 业务异常基类</li>
 *   <li>{@link com.medconsult.common.core.PageResult} / {@link com.medconsult.common.core.PageQuery} - 分页</li>
 * </ul>
 *
 * <p>本模块刻意保持纯 JDK、零第三方依赖，可被任何上层模块安全引入。
 * 序列化（Jackson）、Web 层处理（异常处理器、traceId）等交给 common-web 与各服务。
 */
package com.medconsult.common.core;
