package com.medconsult.notification.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medconsult.notification.notification.entity.Notification;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通知 Mapper（纯 BaseMapper，无自定义 SQL，无 XML）。
 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {
}
