package com.medconsult.ai.service;

import com.medconsult.ai.dto.AiModels.FeedbackItem;
import com.medconsult.ai.dto.AiModels.FeedbackReplyRequest;
import com.medconsult.ai.dto.AiModels.FeedbackRequest;
import com.medconsult.ai.persistence.entity.AiFeedbackEntity;
import com.medconsult.ai.persistence.mapper.AiFeedbackMapper;
import com.medconsult.common.core.BusinessException;
import com.medconsult.common.core.ErrorCode;
import com.medconsult.common.security.JwtPayload;
import com.medconsult.common.security.SecurityContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeedbackServiceSecurityTest {
    private AiFeedbackMapper feedbackMapper;
    private FeedbackService service;

    @BeforeEach
    void setUp() {
        feedbackMapper = mock(AiFeedbackMapper.class);
        service = new FeedbackService(feedbackMapper);
    }

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void submitShouldUseJwtUserIdInsteadOfForgedBodyIdentity() {
        bindPayload(userPayload("PATIENT", List.of("PATIENT"), 7L));

        service.submit(new FeedbackRequest(
                "SUMMARY", "SUM-1", "999", true, false, "helpful"));

        ArgumentCaptor<AiFeedbackEntity> entityCaptor = ArgumentCaptor.forClass(AiFeedbackEntity.class);
        verify(feedbackMapper).insert(entityCaptor.capture());
        AiFeedbackEntity entity = entityCaptor.getValue();
        assertEquals(7L, entity.getFeedbackBy());
        assertEquals(1, entity.getUseful());
        assertEquals(0, entity.getAdopted());
    }

    @Test
    void submitShouldRejectServiceActorBeforeInsert() {
        bindPayload(servicePayload("medical-record-service"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.submit(new FeedbackRequest(
                        "SUMMARY", "SUM-1", "7", true, true, "bad identity")));

        assertEquals(ErrorCode.UNAUTHORIZED, error.getErrorCode());
        verify(feedbackMapper, never()).insert(any());
    }

    @Test
    void listShouldRejectPatientBeforeQueryingFeedback() {
        bindPayload(userPayload("PATIENT", List.of("PATIENT"), 7L));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.list(null, null));

        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
        verify(feedbackMapper, never()).selectList(any());
    }

    @Test
    void listShouldAllowDoctorAndMapFeedbackItems() {
        bindPayload(userPayload("DOCTOR", List.of("DOCTOR"), 33L));
        AiFeedbackEntity stored = feedback("FB-1", 7L, 1, 0, "useful", "thanks");
        when(feedbackMapper.selectList(any())).thenReturn(List.of(stored));

        List<FeedbackItem> items = service.list("SUMMARY", "SUM-1");

        assertEquals(1, items.size());
        assertEquals("FB-1", items.get(0).feedbackId());
        assertEquals("7", items.get(0).feedbackBy());
        assertTrue(items.get(0).useful());
        assertFalse(items.get(0).adopted());
        assertEquals("thanks", items.get(0).adminReply());
    }

    @Test
    void replyShouldRejectPatientBeforeLookingUpFeedback() {
        bindPayload(userPayload("PATIENT", List.of("PATIENT"), 7L));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.reply("FB-1", new FeedbackReplyRequest("handled")));

        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
        verify(feedbackMapper, never()).selectOne(any());
    }

    @Test
    void replyShouldReturnNotFoundForManagerWhenFeedbackDoesNotExist() {
        bindPayload(userPayload("HOSPITAL_ADMIN", List.of("HOSPITAL_ADMIN"), 77L));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.reply("FB-MISSING", new FeedbackReplyRequest("handled")));

        assertEquals(ErrorCode.NOT_FOUND, error.getErrorCode());
    }

    private static AiFeedbackEntity feedback(String feedbackNo, Long feedbackBy,
                                             Integer useful, Integer adopted,
                                             String comment, String reply) {
        AiFeedbackEntity entity = new AiFeedbackEntity();
        entity.setFeedbackNo(feedbackNo);
        entity.setFeedbackBy(feedbackBy);
        entity.setUseful(useful);
        entity.setAdopted(adopted);
        entity.setComment(comment);
        entity.setAdminReply(reply);
        entity.setCreatedAt(LocalDateTime.now());
        return entity;
    }

    private static void bindPayload(JwtPayload payload) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(SecurityContext.PAYLOAD_ATTR_KEY, payload);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static JwtPayload userPayload(String primaryRole, List<String> roles, Long userId) {
        return new JwtPayload(
                JwtPayload.SubjectType.USER, userId, null, "user", roles, primaryRole,
                null, null, null, "U" + userId, List.of(), "jti", Long.MAX_VALUE);
    }

    private static JwtPayload servicePayload(String serviceCode) {
        return new JwtPayload(
                JwtPayload.SubjectType.SERVICE, null, serviceCode, serviceCode, List.of(), null,
                null, null, null, null, List.of(), "jti", Long.MAX_VALUE);
    }
}
