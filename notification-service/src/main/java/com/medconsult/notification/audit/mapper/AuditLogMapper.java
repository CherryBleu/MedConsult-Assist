package com.medconsult.notification.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medconsult.notification.audit.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审计日志 Mapper（纯 BaseMapper，无自定义 SQL，无 XML）。
 *
 * <p>注意：AuditLog 不继承 BaseEntity（流水表无逻辑删除），但 BaseMapper 的 selectPage/selectList
 * 不会自动追加 deleted=0 过滤（因实体无 @TableLogic 字段），符合审计日志只追加语义。
 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}
