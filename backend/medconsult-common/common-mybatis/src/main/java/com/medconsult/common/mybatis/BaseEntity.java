package com.medconsult.common.mybatis;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 业务实体基类（架构文档 §3.2 / 《修改建议》§5.1）。
 *
 * <p>承载通用的 4 个元数据字段，由 {@link AutoFillMetaHandler} 自动填充：
 * <ul>
 *   <li>{@code id} - 主键，雪花 ID（MyBatis-Plus ASSIGN_ID，跨服务不冲突）</li>
 *   <li>{@code created_at} - 插入时填充</li>
 *   <li>{@code updated_at} - 插入与更新时填充</li>
 *   <li>{@code deleted} - 逻辑删除（0 未删 / 1 已删），{@link TableLogic} 让 MP 自动过滤</li>
 * </ul>
 *
 * <p>流水表（只追加、不更新、不删）不应继承本类，自行定义字段。
 */
@Getter
@Setter
public abstract class BaseEntity {

    /**
     * 主键。雪花 ID，分配型（不依赖 DB 自增，便于跨服务/分库）。
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 创建时间。插入时自动填充。
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间。插入与更新时自动填充。
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除标识：0 未删除，1 已删除。
     * MyBatis-Plus 的 {@link TableLogic} 会自动在查询追加 deleted=0 过滤、
     * 把 DELETE 改写为 UPDATE deleted=1。详见《修改建议》§5.1（所有业务实体表统一）。
     */
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
