/**
 * common-mybatis：MyBatis-Plus 公共配置（待实现，对应架构文档 §3.2）。
 *
 * <p>计划内容：
 * <ul>
 *   <li>{@code AutoFillMetaHandler} - 自动填充 created_at / updated_at（插入/更新时）</li>
 *   <li>逻辑删除统一字段 {@code deleted}（0/1），见《修改建议.md》§5.1</li>
 *   <li>{@code JsonTypeHandler} 基类 - MySQL JSON 字段 ↔ POJO 映射（allergies / contraindications 等）</li>
 *   <li>雪花 ID 配置 / 分页插件 / 审计拦截器</li>
 * </ul>
 *
 * <p>本模块当前为占位。
 */
package com.medconsult.common.mybatis;
