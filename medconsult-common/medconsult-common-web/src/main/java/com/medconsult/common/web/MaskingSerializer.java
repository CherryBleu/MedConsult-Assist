package com.medconsult.common.web;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;

import java.io.IOException;

/**
 * {@link Mask} 注解的 Jackson 序列化器。
 *
 * <p>序列化时取字段上的 {@link MaskType} 应用打码。通过 {@link ContextualSerializer}
 * 在首次序列化时绑定具体字段的 MaskType（每个字段一份独立实例）。
 */
public class MaskingSerializer extends JsonSerializer<String> implements ContextualSerializer {

    private MaskType type = MaskType.FULL;
    private boolean keepNull = true;

    public MaskingSerializer() {
    }

    public MaskingSerializer(MaskType type, boolean keepNull) {
        this.type = type;
        this.keepNull = keepNull;
    }

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            // 直接写 null，由 Jackson 按 Include 规则处理
            gen.writeNull();
            return;
        }
        gen.writeString(type.mask(value));
    }

    @Override
    public JsonSerializer<?> createContextual(SerializerProvider prov, BeanProperty property) {
        if (property == null) {
            return this;
        }
        Mask ann = property.getAnnotation(Mask.class);
        if (ann == null) {
            // 字段没标 @Mask，但不影响——本序列化器只会被 @Mask 标注的字段触发
            return this;
        }
        return new MaskingSerializer(ann.value(), ann.keepNull());
    }
}
