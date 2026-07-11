package com.medconsult.notification.notification.service;

import com.medconsult.common.core.PageResult;
import com.medconsult.notification.notification.dto.NotificationDTO;

/**
 * 通知服务接口（对齐《接口文档》§2.8 + 架构文档 §2.3 内部接口）。
 */
public interface NotificationService {

    /** §2.8.1 创建通知（对外 POST /notifications + 内部 POST /internal/notifications 共用） */
    NotificationDTO.CreateResponse create(NotificationDTO.CreateRequest req);

    /** §2.8.2 查询通知列表（可按 receiverId / read 过滤） */
    PageResult<NotificationDTO.ListItem> list(int page, int pageSize, String receiverId, Boolean read);

    /** §2.8.3 标记通知已读 */
    NotificationDTO.ReadResponse markRead(String notificationNo);
}
