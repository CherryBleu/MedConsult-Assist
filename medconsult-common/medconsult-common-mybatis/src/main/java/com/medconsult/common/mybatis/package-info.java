/**
 * common-mybatis：MyBatis-Plus 公共配置（对应架构文档 §3.2）。
 *
 * <p>已实现：
 * <ul>
 *   <li>{@link com.medconsult.common.mybatis.BaseEntity} - 业务实体基类（id/created_at/updated_at/deleted）</li>
 *   <li>{@link com.medconsult.common.mybatis.AutoFillMetaHandler} - 自动填充 created_at/updated_at/deleted</li>
 *   <li>{@link com.medconsult.common.mybatis.MedConsultMybatisAutoConfiguration} - 自动装配（分页插件 + 自动填充）</li>
 * </ul>
 *
 * <p><b>JSON 字段</b>：直接用 MyBatis-Plus 内置 JacksonTypeHandler：
 * <pre>
 *   &#64;TableName(value = "drug", autoResultMap = true)
 *   public class Drug extends BaseEntity {
 *       &#64;TableField(typeHandler = JacksonTypeHandler.class)
 *       private List&lt;String&gt; contraindications;  // MySQL JSON 列
 *   }
 * </pre>
 *
 * <p><b>逻辑删除</b>：{@code @TableLogic} 在 BaseEntity 已声明，MP 自动过滤 deleted=0、
 * 把 DELETE 改写为 UPDATE deleted=1（对应《修改建议》§5.1）。
 *
 * <p>流水表（drug_stock_flow / audit_log / login_log / ai_call_log）只追加不更新不删，
 * <b>不应继承 BaseEntity</b>，自行定义无 updated_at/deleted 的字段。
 */
package com.medconsult.common.mybatis;
