package com.medconsult.notification.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medconsult.common.core.BusinessException;
import com.medconsult.common.core.ErrorCode;
import com.medconsult.common.core.PageResult;
import com.medconsult.common.core.PageQuery;
import com.medconsult.common.security.JwtPayload;
import com.medconsult.common.security.SecurityContext;
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
        // IDOR 防护（架构 §4.3 SELF）：PATIENT/DOCTOR 只能查发给自己的通知。
        // notification.receiver_id 存的是业务编号（userNo），与 JWT sub(userId) 不同——
        // 故用 SecurityContext 的 userNo。无身份（匿名/服务）访问对外接口 → 拒绝。
        // 管理员（HOSPITAL_ADMIN/PHARMACY_ADMIN）可指定 receiverId 查任意人的，不传则查全部。
        JwtPayload payload = SecurityContext.requireUser();
        String scopedReceiver = resolveReceiverScope(payload, receiverId);

        Page<Notification> p = new Page<>(PageQuery.normalizePage(page), PageQuery.normalizePageSize(pageSize));
        QueryWrapper<Notification> qw = new QueryWrapper<>();
        if (scopedReceiver != null) {
            qw.eq("receiver_id", scopedReceiver);
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
        // IDOR 防护：只能标记发给自己的通知为已读。管理员不限。
        enforceOwnership(n);
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

    /**
     * 列表 receiver 作用域解析（IDOR 防护，架构 §4.3 SELF）：
     * <ul>
     *   <li>管理员（HOSPITAL_ADMIN/PHARMACY_ADMIN）：尊重入参 receiverId（可查任意人），不传则 null（查全部）</li>
     *   <li>PATIENT/DOCTOR：JWT 目前只有 userId（数字 sub），没有 userNo claim；而
     *       notification.receiver_id 存的是业务编号串（如 patient_no / userNo），
     *       两者类型不同无法可靠匹配。<b>当前阶段：非管理员查询直接拒绝</b>（FORBIDDEN），
     *       避免"安全失败返回空列表"掩盖越权或功能不可用。
     *       RBAC 五表阶段会给 JWT 增加 userNo claim，届时此处恢复为"强制限定本人 userNo"。</li>
     * </ul>
     *
     * @return 用于查询的 receiver_id；null 表示不按 receiver 过滤（仅管理员不传 receiverId 时）
     */
    private String resolveReceiverScope(JwtPayload payload, String receiverId) {
        if (isAdmin(payload)) {
            return (receiverId != null && !receiverId.isBlank()) ? receiverId : null;
        }
        // 非管理员：当前 JWT 无 userNo claim，无法与 receiver_id（业务编号）可靠匹配。
        // 直接拒绝（而不是返回空列表），让调用方明确知道此路径未打通，避免误以为"无数据"。
        // TODO RBAC 五表阶段：JWT 增加 userNo claim，此处改为 return jwtUserNo（强制限定本人）。
        throw new BusinessException(ErrorCode.FORBIDDEN,
                "当前账号角色无权查询通知列表，请联系管理员或使用管理员账号");
    }

    /** 单条通知归属校验（IDOR 防护）：非管理员只能操作发给自己的通知 */
    private void enforceOwnership(Notification n) {
        JwtPayload payload = SecurityContext.requireUser();
        if (isAdmin(payload)) {
            return;
        }
        // 同 resolveReceiverScope：JWT 无 userNo claim，无法与 receiver_id（业务编号）匹配。
        // 非管理员直接拒绝，避免越权标记他人通知。
        // TODO RBAC 五表阶段：JWT 增加 userNo claim，此处改为 jwtUserNo.equals(n.getReceiverId())。
        throw new BusinessException(ErrorCode.FORBIDDEN,
                "当前账号角色无权操作该通知，请联系管理员或使用管理员账号");
    }

    /** 是否管理员角色（可查/操作全部通知）：检查 roles 列表或 primaryRole */
    private static boolean isAdmin(JwtPayload p) {
        if (p == null) return false;
        if ("HOSPITAL_ADMIN".equals(p.primaryRole()) || "PHARMACY_ADMIN".equals(p.primaryRole())) {
            return true;
        }
        if (p.roles() != null) {
            return p.roles().contains("HOSPITAL_ADMIN") || p.roles().contains("PHARMACY_ADMIN");
        }
        return false;
    }

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
