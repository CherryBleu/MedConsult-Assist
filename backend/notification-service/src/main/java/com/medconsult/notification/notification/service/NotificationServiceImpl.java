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
import java.util.LinkedHashSet;
import java.util.Set;

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
    private final NotificationRealtimeService realtimeService;

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
        realtimeService.publishCreatedAfterCommit(n);
        return new NotificationDTO.CreateResponse(n.getNotificationNo(), false);
    }

    @Override
    public PageResult<NotificationDTO.ListItem> list(int page, int pageSize, String receiverId, Boolean read) {
        // IDOR 防护（架构 §4.3 SELF）：PATIENT/DOCTOR 只能查发给自己的通知。
        // notification.receiver_id 存的是业务编号（userNo），由 JWT 的 userNo claim 匹配。
        // 无身份（匿名/服务）访问对外接口 → SecurityContext.requireUser 抛 401。
        // 管理员（HOSPITAL_ADMIN/PHARMACY_ADMIN）可指定 receiverId 查任意人的，不传则查全部。
        JwtPayload payload = SecurityContext.requireUser();
        Set<String> scopedReceiver = resolveReceiverScope(payload, receiverId);

        Page<Notification> p = new Page<>(PageQuery.normalizePage(page), PageQuery.normalizePageSize(pageSize));
        QueryWrapper<Notification> qw = new QueryWrapper<>();
        if (scopedReceiver != null && !scopedReceiver.isEmpty()) {
            if (scopedReceiver.size() == 1) {
                qw.eq("receiver_id", scopedReceiver.iterator().next());
            } else {
                qw.in("receiver_id", scopedReceiver);
            }
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
                    n.getContent(),
                    n.getReadStatus() != null && n.getReadStatus() == 1,
                    n.getCreatedAt()));
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
        realtimeService.publishReadAfterCommit(n);
        return new NotificationDTO.ReadResponse(n.getNotificationNo(), true, n.getReadAt());
    }

    // ===== 私有助手 =====

    /**
     * 列表 receiver 作用域解析（IDOR 防护，架构 §4.3 SELF）：
     * <ul>
     *   <li>管理员（HOSPITAL_ADMIN/PHARMACY_ADMIN）：尊重入参 receiverId（可查任意人），不传则 null（查全部）</li>
     *   <li>PATIENT/DOCTOR：JWT 已携带 userNo claim（用户业务编号），与
     *       notification.receiver_id（业务编号串，如 patient_no / userNo）类型一致可直接匹配，
     *       故强制限定本人 userNo；userNo 为 null（旧 token 无此 claim）才拒绝。</li>
     * </ul>
     *
     * @return 用于查询的 receiver_id；null 表示不按 receiver 过滤（仅管理员不传 receiverId 时）
     */
    private Set<String> resolveReceiverScope(JwtPayload payload, String receiverId) {
        if (isAdmin(payload)) {
            if (receiverId != null && !receiverId.isBlank()) {
                return Set.of(receiverId);
            }
            Set<String> candidates = receiverCandidates(payload);
            addIfPresent(candidates, payload.primaryRole());
            if (payload.roles() != null) {
                payload.roles().forEach(role -> addIfPresent(candidates, role));
            }
            return candidates;
        }
        Set<String> candidates = receiverCandidates(payload);
        if (candidates.isEmpty()) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "当前登录态缺少通知接收人标识，请重新登录后再试");
        }
        return candidates;
    }

    /** 单条通知归属校验（IDOR 防护）：非管理员只能操作发给自己的通知 */
    private void enforceOwnership(Notification n) {
        JwtPayload payload = SecurityContext.requireUser();
        if (isAdmin(payload)) {
            return;
        }
        if (!receiverCandidates(payload).contains(n.getReceiverId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "当前账号角色无权操作该通知，请联系管理员或使用管理员账号");
        }
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

    private static Set<String> receiverCandidates(JwtPayload payload) {
        Set<String> candidates = new LinkedHashSet<>();
        addIfPresent(candidates, payload.userNo());
        if (payload.patientId() != null) {
            candidates.add(String.valueOf(payload.patientId()));
        }
        if (payload.doctorId() != null) {
            candidates.add(String.valueOf(payload.doctorId()));
        }
        if (payload.pharmacistId() != null) {
            candidates.add(String.valueOf(payload.pharmacistId()));
        }
        return candidates;
    }

    private static void addIfPresent(Set<String> candidates, String value) {
        if (value != null && !value.isBlank()) {
            candidates.add(value);
        }
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
