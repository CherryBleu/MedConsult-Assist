package com.medconsult.notification.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medconsult.common.core.BusinessException;
import com.medconsult.common.core.ErrorCode;
import com.medconsult.common.security.JwtPayload;
import com.medconsult.common.security.SecurityContext;
import com.medconsult.notification.notification.entity.Notification;
import com.medconsult.notification.notification.mapper.NotificationMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link NotificationServiceImpl} 的 IDOR 安全逻辑单元测试。
 *
 * <p>本次给 JWT 增加 userNo claim 后，通知服务非管理员改用 {@code payload.userNo()} 匹配
 * {@code notification.receiver_id}。本测试专门覆盖该核心安全路径，补既有
 * {@code NotificationFlowTest}（需 Redis/RabbitMQ 基础设施）未覆盖的正向/越权场景。
 *
 * <p><b>测试方案：纯单元测试</b>（非 @WebMvcTest / @SpringBootTest）。
 * 理由：IDOR 校验在 Service 层（{@code resolveReceiverScope} / {@code enforceOwnership}
 * 直接调 {@link SecurityContext#getPayload()}），@WebMvcTest 会 @MockBean 掉 Service，
 * 把要测的安全逻辑整个 stub 掉，断言只能停在 Controller 透传层无意义。
 * 此处直接 new NotificationServiceImpl + Mockito mock Mapper，并用
 * {@link RequestContextHolder} 注入身份（{@link SecurityContext#setPayload} 依赖
 * ServletRequestAttributes，同 {@code AuthRelayInterceptorTest} 模式）。
 * 无需任何基础设施。
 *
 * <p>覆盖三条路径：
 * <ul>
 *   <li>匹配通过（正向）：PATIENT 的 userNo 与通知 receiver_id 一致 → 返回该条</li>
 *   <li>不匹配拒绝（越权拦截）：PATIENT 的 userNo 与现有通知不一致 → 作用域强制为本人 userNo，
 *       查不到他人通知（IDOR 防护：入参 receiverId 被忽略）</li>
 *   <li>markRead 归属校验：PATIENT 的 userNo 与通知 receiver_id 不一致 → 403</li>
 *   <li>markRead 归属校验（正向）：userNo 一致 → 放行标记已读</li>
 *   <li>userNo 为空拒绝：旧 token 无 userNo claim → 403（兼容性兜底）</li>
 * </ul>
 */
class NotificationServiceImplSecurityTest {

    private NotificationMapper notificationMapper;
    private NotificationRealtimeService realtimeService;
    private NotificationServiceImpl service;

    /** 测试用接收人业务编号 */
    private static final String RECEIVER_OWN = "U123ABC";
    private static final String RECEIVER_OTHER = "U999ZZZ";

    @BeforeEach
    void setUp() {
        // 建立请求上下文（SecurityContext.setPayload/getPayload 依赖 ServletRequestAttributes）
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));
        notificationMapper = mock(NotificationMapper.class);
        realtimeService = mock(NotificationRealtimeService.class);
        // NotificationServiceImpl 用 @RequiredArgsConstructor 注入 NotificationMapper；
        // 构造器由 Lombok 生成，可直接 new 传入 mock（不启 Spring 容器）。
        service = new NotificationServiceImpl(notificationMapper, realtimeService);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContext.setPayload(null);
    }

    // ===== list: 匹配通过（正向）=====

    @Test
    void list_userNoMatchesReceiver_returnsOwnNotification() {
        // PATIENT 带 userNo=U123ABC；库里恰好有一条 receiver_id=U123ABC 的通知
        setUser(new JwtPayload(
                JwtPayload.SubjectType.USER, 100L, null, "患者A",
                List.of("PATIENT"), "PATIENT", null, null, null, RECEIVER_OWN,
                List.of(), "jti-self", 0L));

        Notification own = notification(RECEIVER_OWN, "N001", "本人预约通知", 0);
        stubSelectPageReturning(own);

        var result = service.list(1, 10, null, null);

        // IDOR 防护核心断言：作用域被强制为本人 userNo，返回自己的通知
        assertEquals(1, result.total());
        assertEquals(1, result.items().size());
        assertEquals("N001", result.items().get(0).notificationId());
        assertEquals("本人预约通知", result.items().get(0).title());
        assertFalse(result.items().get(0).read());

        // 关键：QueryWrapper 的 receiver_id 过滤值 = 本人 userNo（非客户端入参）
        String scoped = extractReceiverIdValue(captureWrapper().getValue());
        assertEquals(RECEIVER_OWN, scoped,
                "receiver_id 过滤值必须是本人 userNo（IDOR：作用域强制本人）");
    }

    // ===== list: 不匹配拒绝（越权拦截 — 入参 receiverId 被忽略，作用域强制本人）=====

    @Test
    void list_userNoMismatch_scopeForcedToOwnUserNo_ignoringClientReceiverId() {
        // PATIENT 带 userNo=U123ABC，却试图传 receiverId=U999ZZZ 查他人通知
        setUser(new JwtPayload(
                JwtPayload.SubjectType.USER, 100L, null, "患者A",
                List.of("PATIENT"), "PATIENT", null, null, null, RECEIVER_OWN,
                List.of(), "jti-self", 0L));

        // 库里只有 receiver_id=U999ZZZ（他人）的通知
        Notification others = notification(RECEIVER_OTHER, "N002", "他人通知", 0);
        stubSelectPageReturning(others);

        var result = service.list(1, 10, RECEIVER_OTHER, null);

        // IDOR 防护核心断言：入参 receiverId=U999ZZZ 被完全忽略，作用域仍是本人 userNo=U123ABC。
        // 即便库里存在他人通知，由于查询条件强制 receiver_id=U123ABC，真实 DB 不会返回他人数据
        // （此处 stub 无条件返回是为了隔离 Mapper 行为；校验点在 QueryWrapper 的过滤值）。
        String scoped = extractReceiverIdValue(captureWrapper().getValue());
        assertEquals(RECEIVER_OWN, scoped,
                "越权拦截：receiver_id 过滤值必须是本人 userNo=" + RECEIVER_OWN
                        + "，而非客户端传入的 " + RECEIVER_OTHER);
        // 顺带确认确实经过了 Mapper 调用（作用域放行后查库）
        verify(notificationMapper).selectPage(any(Page.class), any(QueryWrapper.class));
        // 结果引用了 stub 返回的通知（说明流程走通），但真实环境下他人通知因 receiver_id 过滤不会出现
        assertNotNull(result);
    }

    // ===== list: userNo 为空拒绝（旧 token 兼容性兜底）=====

    @Test
    void list_userNoAbsent_throwsForbidden() {
        // 旧 token 无 userNo claim（null）
        setUser(new JwtPayload(
                JwtPayload.SubjectType.USER, 100L, null, "患者A",
                List.of("PATIENT"), "PATIENT", null, null, null, null,
                List.of(), "jti-old", 0L));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.list(1, 10, null, null));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        // 不应触达 Mapper（在 resolveReceiverScope 阶段就拒绝）
        verifyNoInteractions(notificationMapper);
    }

    // ===== markRead: 归属不匹配拒绝（越权拦截）=====

    @Test
    void markRead_userNoMismatch_throwsForbidden() {
        // PATIENT 带 userNo=U123ABC，通知 receiver_id=U999ZZZ（非本人）
        setUser(new JwtPayload(
                JwtPayload.SubjectType.USER, 100L, null, "患者A",
                List.of("PATIENT"), "PATIENT", null, null, null, RECEIVER_OWN,
                List.of(), "jti-self", 0L));

        Notification others = notification(RECEIVER_OTHER, "N002", "他人通知", 0);
        when(notificationMapper.selectOne(any(QueryWrapper.class))).thenReturn(others);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.markRead("N002"));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        // 未授权 → 不应写库
        verify(notificationMapper, never()).updateById(any());
    }

    // ===== markRead: 归属匹配放行（正向）=====

    @Test
    void markRead_userNoMatches_passesAndMarksRead() {
        setUser(new JwtPayload(
                JwtPayload.SubjectType.USER, 100L, null, "患者A",
                List.of("PATIENT"), "PATIENT", null, null, null, RECEIVER_OWN,
                List.of(), "jti-self", 0L));

        Notification own = notification(RECEIVER_OWN, "N001", "本人通知", 0);
        when(notificationMapper.selectOne(any(QueryWrapper.class))).thenReturn(own);

        var resp = service.markRead("N001");

        assertEquals("N001", resp.notificationId());
        assertTrue(resp.read());
        assertNotNull(resp.readAt());
        // 已标记 → 写库一次
        verify(notificationMapper, times(1)).updateById(argThat(n ->
                Integer.valueOf(1).equals(((Notification) n).getReadStatus())));
    }

    @Test
    void list_patientIdMatchesReceiver_returnsOwnNotification() {
        setUser(new JwtPayload(
                JwtPayload.SubjectType.USER, 100L, null, "患者A",
                List.of("PATIENT"), "PATIENT", 12345L, null, null, null,
                List.of(), "jti-patient", 0L));

        Notification own = notification("12345", "N010", "本人患者通知", 0);
        stubSelectPageReturning(own);

        var result = service.list(1, 10, null, null);

        assertEquals(1, result.total());
        assertEquals("N010", result.items().get(0).notificationId());
        String scoped = extractReceiverIdValue(captureWrapper().getValue());
        assertEquals("12345", scoped);
    }

    // ===== markRead: userNo 为空拒绝（旧 token 兼容性兜底）=====

    @Test
    void markRead_userNoAbsent_throwsForbidden() {
        setUser(new JwtPayload(
                JwtPayload.SubjectType.USER, 100L, null, "患者A",
                List.of("PATIENT"), "PATIENT", null, null, null, null,
                List.of(), "jti-old", 0L));

        Notification own = notification(RECEIVER_OWN, "N001", "本人通知", 0);
        when(notificationMapper.selectOne(any(QueryWrapper.class))).thenReturn(own);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.markRead("N001"));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        verify(notificationMapper, never()).updateById(any());
    }

    // ===== 助手 =====

    /** 绑定身份到当前请求上下文（模拟 JwtAuthServletFilter 从 X-User-* 头重建 payload 后的效果） */
    private static void setUser(JwtPayload payload) {
        SecurityContext.setPayload(payload);
    }

    /** 构造一条测试通知 */
    private static Notification notification(String receiverId, String no, String title, int readStatus) {
        Notification n = new Notification();
        n.setNotificationNo(no);
        n.setReceiverId(receiverId);
        n.setReceiverRole("PATIENT");
        n.setType("APPOINTMENT");
        n.setTitle(title);
        n.setContent("内容");
        n.setReadStatus(readStatus);
        return n;
    }

    /** stub selectPage：返回含给定通知的单页（注意 selectPage 返回值与入参 Page 是同一对象，records 需 set 回去） */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubSelectPageReturning(Notification... records) {
        when(notificationMapper.selectPage(any(Page.class), any(QueryWrapper.class)))
                .thenAnswer(inv -> {
                    Page<Notification> page = inv.getArgument(0);
                    page.setRecords(List.of(records));
                    page.setTotal(records.length);
                    return page;
                });
    }

    /** 捕获 selectPage 收到的 QueryWrapper，用于断言 receiver_id 绑定值 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<QueryWrapper<Notification>> captureWrapper() {
        ArgumentCaptor<QueryWrapper<Notification>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(notificationMapper).selectPage(any(Page.class), captor.capture());
        return captor;
    }

    /**
     * 从 QueryWrapper 提取 receiver_id 过滤绑定的字符串值。
     * <p>resolveReceiverScope 返回 userNo 后，list 用 qw.eq("receiver_id", scopedReceiver)。
     * QueryWrapper 的 paramNameValuePairs 在 getCustomSqlSegment() 触发片段拼接后才填充，
     * 故先调用一次 getSqlSegment()（等价于 getCustomSqlSegment）强制构建，再取绑定值。
     * <p>list 的过滤条件里 receiver_id 是唯一的 String 字面量值（read_status 走 0/1 整型，
     * created_at 走 orderByDesc 无绑定值），取首个 String 即可稳定定位。
     */
    private static String extractReceiverIdValue(QueryWrapper<?> qw) {
        // 触发片段拼接，让 paramNameValuePairs 填充
        qw.getSqlSegment();
        return qw.getParamNameValuePairs().values().stream()
                .filter(v -> v instanceof String)
                .map(Object::toString)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "QueryWrapper 未找到 receiver_id 的 String 绑定值，SQL 片段: "
                                + qw.getSqlSegment() + "，实际绑定: " + qw.getParamNameValuePairs()));
    }
}
