package com.medconsult.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.medconsult.ai.dto.AiModels.FeedbackItem;
import com.medconsult.ai.dto.AiModels.FeedbackReplyRequest;
import com.medconsult.ai.dto.AiModels.FeedbackReplyResponse;
import com.medconsult.ai.dto.AiModels.FeedbackRequest;
import com.medconsult.ai.dto.AiModels.FeedbackResponse;
import com.medconsult.ai.persistence.entity.AiFeedbackEntity;
import com.medconsult.ai.persistence.mapper.AiFeedbackMapper;
import com.medconsult.ai.util.BusinessIds;
import com.medconsult.common.core.BusinessException;
import com.medconsult.common.core.ErrorCode;
import com.medconsult.common.security.JwtPayload;
import com.medconsult.common.security.SecurityContext;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class FeedbackService {
    private final AiFeedbackMapper feedbackMapper;

    public FeedbackService(AiFeedbackMapper feedbackMapper) {
        this.feedbackMapper = feedbackMapper;
    }

    public FeedbackResponse submit(FeedbackRequest request) {
        JwtPayload actor = SecurityContext.requireUser();
        if (actor.userId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "user identity is incomplete");
        }
        String feedbackNo = BusinessIds.next("FB");
        AiFeedbackEntity entity = new AiFeedbackEntity();
        entity.setFeedbackNo(feedbackNo);
        entity.setAiResultType(request.aiResultType());
        entity.setAiResultId(request.aiResultId());
        entity.setFeedbackBy(actor.userId());
        entity.setRating(request.rating());
        entity.setComment(request.comment());
        entity.setCreatedAt(LocalDateTime.now());
        feedbackMapper.insert(entity);
        return new FeedbackResponse(feedbackNo, request.aiResultId());
    }

    public List<FeedbackItem> list(String aiResultType, String aiResultId) {
        requireFeedbackManager();
        return feedbackMapper.selectList(new LambdaQueryWrapper<AiFeedbackEntity>()
                        .eq(aiResultType != null && !aiResultType.isBlank(), AiFeedbackEntity::getAiResultType, aiResultType)
                        .eq(aiResultId != null && !aiResultId.isBlank(), AiFeedbackEntity::getAiResultId, aiResultId)
                        .orderByDesc(AiFeedbackEntity::getCreatedAt))
                .stream()
                .map(item -> new FeedbackItem(
                        item.getFeedbackNo(),
                        String.valueOf(item.getFeedbackBy()),
                        item.getRating(),
                        item.getComment(),
                        item.getAdminReply()
                ))
                .toList();
    }

    /**
     * 管理员回复反馈（对齐前端 POST /ai/feedback/{id}/reply）。
     */
    public FeedbackReplyResponse reply(String feedbackId, FeedbackReplyRequest request) {
        requireFeedbackManager();
        AiFeedbackEntity entity = feedbackMapper.selectOne(new LambdaQueryWrapper<AiFeedbackEntity>()
                .eq(AiFeedbackEntity::getFeedbackNo, feedbackId)
                .last("limit 1"));
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "feedback not found");
        }
        entity.setAdminReply(request.reply());
        entity.setRepliedAt(LocalDateTime.now());
        feedbackMapper.updateById(entity);
        return new FeedbackReplyResponse(feedbackId, request.reply(), "REPLIED");
    }

    private static JwtPayload requireFeedbackManager() {
        JwtPayload actor = SecurityContext.requireUser();
        if (hasActiveRole(actor, "DOCTOR") || hasActiveRole(actor, "HOSPITAL_ADMIN")) {
            return actor;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "feedback management role is required");
    }

    private static boolean hasActiveRole(JwtPayload actor, String role) {
        if (StringUtils.hasText(actor.primaryRole())) {
            return role.equals(actor.primaryRole());
        }
        return actor.hasRole(role);
    }
}
