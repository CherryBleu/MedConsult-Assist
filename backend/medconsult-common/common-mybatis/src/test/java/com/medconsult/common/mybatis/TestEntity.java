package com.medconsult.common.mybatis;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * MybatisPlusFlowTest 用的实体桩，继承 {@link BaseEntity} 验证自动填充 + 逻辑删除。
 */
@Getter
@Setter
@TableName("test_entity")
public class TestEntity extends BaseEntity {
    private String name;
    private String status;
}
