package com.medconsult.notification.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medconsult.common.core.BusinessException;
import com.medconsult.common.core.ErrorCode;
import com.medconsult.common.core.PageResult;
import com.medconsult.notification.notification.dto.NotificationDTO;
import com.medconsult.notification.notification.entity.Notification;
import com.medconsult.notification.notification.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 通知服务实现（对齐《接口文档》§2.8）。
 *
 * <p>对外 POST/GET/PATCH 走 controller，内部 POST /internal/notifications 也调 create（同步兜底）。
 * MQ 消费者收到 NotificationEvent 后转 CreateRequest 调 create。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;

    @Override
    @Transactional
    public NotificationDTO.CreateResponse create(NotificationDTO.CreateRequest req) {
        Notification n = new Notification();
        n.setNotificationNo(generateNotificationNo());
        n.setReceiverId(req.getReceiverId());
        n.setReceiverRole(req.getReceiverRole());
        n.setType(req.getType());
        n.setTitle(req.getTitle());
        n.setContent(req.getContent());
        n.setRelatedType(req.getRelatedType());
        n.setRelatedId(req.getRelatedId());
        n.setReadStatus(0); // 初始未读
        notificationMapper.insert(n);
        log.info("通知创建: notificationNo={} receiverId={} type={}",
                n.getNotificationNo(), req.getReceiverId(), req.getType());
        return new NotificationDTO.CreateResponse(n.getNotificationNo(), false);
    }

    @Override
    public PageResult<NotificationDTO.ListItem> list(int page, int pageSize, String receiverId, Boolean read) {
        Page<Notification> p = new Page<>(page <= 0 ? 1 : page, pageSize <= 0 ? 10 : pageSize);
        QueryWrapper<Notification> qw = new QueryWrapper<>();
        if (receiverId != null && !receiverId.isBlank()) {
            qw.eq("receiver_id", receiverId);
        }
        if (read != null) {
            qw.eq("read_status", read ? 1 : 0);
        }
        qw.orderByDesc("created_at");
        IPage<Notification> result = notificationMapper.selectPage(p, qw);
        List<NotificationDTO.ListItem> items = new ArrayList<>();
        for (Notification n : result.getRecords()) {
            items.add(new NotificationDTO.ListItem(
                    n.getNotificationNo(),
                    n.getType(),
                    n.getTitle(),
                    n.getReadStatus() != null && n.getReadStatus() == 1));
        }
        return PageResult.of((int) result.getCurrent(), (int) result.getSize(), result.getTotal(), items);
    }

    @Override
    @Transactional
    public NotificationDTO.ReadResponse markRead(String notificationNo) {
        Notification n = requireByNo(notificationNo);
        if (n.getReadStatus() != null && n.getReadStatus() == 1) {
            // 已读幂等：重复标记已读不报错，直接返回
            return new NotificationDTO.ReadResponse(n.getNotificationNo(), true, n.getReadAt());
        }
        n.setReadStatus(1);
        n.setReadAt(LocalDateTime.now());
        notificationMapper.updateById(n);
        log.info("通知标记已读: notificationNo={}", notificationNo);
        return new NotificationDTO.ReadResponse(n.getNotificationNo(), true, n.getReadAt());
    }

    // ===== 私有助手 =====

    private Notification requireByNo(String notificationNo) {
        if (notificationNo == null || notificationNo.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "通知编号不能为空");
        }
        Notification n = notificationMapper.selectOne(
                new QueryWrapper<Notification>().eq("notification_no", notificationNo));
        if (n == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "通知不存在: " + notificationNo);
        }
        return n;
    }

    /** 生成通知编号：N + 雪花序列 base36。DB 有 uk_notification_no 兜底 */
    private static String generateNotificationNo() {
        long id = IdWorker.getId();
        return "N" + Long.toUnsignedString(id, Character.MAX_RADIX).toUpperCase();
    }
}
