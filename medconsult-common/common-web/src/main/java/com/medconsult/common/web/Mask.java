package com.medconsult.common.web;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 敏感字段脱敏注解（架构文档 §3.2 / 《修改建议》§5.3）。
 *
 * <p>标注在 DTO/响应体字段上，Jackson 序列化时自动打码。原始值不变，仅影响输出。
 *
 * <p>用法：
 * <pre>
 *   public class PatientDetailDTO {
 *       &#64;Mask(MaskType.PHONE)        // 138****0001
 *       private String phone;
 *
 *       &#64;Mask(MaskType.ID_NO)        // 110101********0011
 *       private String idNo;
 *
 *       &#64;Mask(MaskType.NAME)         // 张*（保留首字，其余打码）
 *       private String name;
 *   }
 * </pre>
 *
 * <p>脱敏规则见 {@link MaskType}。
 */
@JacksonAnnotationsInside
@JsonSerialize(using = MaskingSerializer.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Mask {

    MaskType value();

    /** 仅当字段为 null 时是否保留 null（默认 true，不输出 "null" 字符串） */
    boolean keepNull() default true;
}
