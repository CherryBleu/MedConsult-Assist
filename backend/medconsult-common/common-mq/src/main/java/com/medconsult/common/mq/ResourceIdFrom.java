package com.medconsult.common.mq;

/**
 * {@link AuditLog#resourceIdFrom()} 取值策略。
 *
 * <ul>
 *   <li>{@link #PATH}：从 Controller 方法的 @PathVariable 取。
 *       按 {@code recordId} → {@code prescriptionId} → 自定义 pathVarName 顺序查找。</li>
 *   <li>{@link #RESULT}：从返回值 {@code Result.data} 的字段取。
 *       按 {@code recordId} → {@code prescriptionId} 顺序反射查找（新增类接口返回值里才有编号）。</li>
 *   <li>{@link #NONE}：不取 resourceId（留空）。</li>
 * </ul>
 */
public enum ResourceIdFrom {
    PATH,
    RESULT,
    NONE
}
