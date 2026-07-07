package com.medconsult.common.mybatis;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 自动填充处理器（架构文档 §3.2）。
 *
 * <p>统一填充 {@link BaseEntity} 的三个元数据字段：
 * <ul>
 *   <li>插入时：created_at / updated_at / deleted(=0)</li>
 *   <li>更新时：updated_at</li>
 * </ul>
 *
 * <p>业务代码无需手写这四个字段的赋值，Mapper insert/update 自动触发。
 *
 * <p>{@code getFieldValByName} 防重复填充：若业务已显式赋值（如导入历史数据带原始时间戳），
 * 尊重原值不覆盖。
 */
@Component
public class AutoFillMetaHandler implements MetaObjectHandler {

    private static final int NOT_DELETED = 0;

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        // 仅在字段为空时填充，避免覆盖业务显式赋值（如数据迁移场景）
        strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "deleted", Integer.class, NOT_DELETED);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // updated_at 在 update 时【强制覆盖】，不尊重原值——因为 update 触发即意味着"动过"，
        // 元数据须反映最后一次修改时间。strictUpdateFill 只在 null 时填，不适合本字段。
        if (metaObject.hasSetter("updatedAt")) {
            metaObject.setValue("updatedAt", LocalDateTime.now());
        }
    }
}
