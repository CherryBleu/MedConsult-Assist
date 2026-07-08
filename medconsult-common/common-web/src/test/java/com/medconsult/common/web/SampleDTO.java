package com.medconsult.common.web;

/**
 * GlobalWebFlowTest 的脱敏 DTO 桩。各字段标 {@link Mask} 验证序列化打码。
 */
public class SampleDTO {
    @Mask(MaskType.NAME)
    String name = "张三";

    @Mask(MaskType.PHONE)
    String phoneMasked = "13800000001";

    @Mask(MaskType.ID_NO)
    String idNoMasked = "110101198805120011";

    public String getName() { return name; }
    public String getPhoneMasked() { return phoneMasked; }
    public String getIdNoMasked() { return idNoMasked; }
}
