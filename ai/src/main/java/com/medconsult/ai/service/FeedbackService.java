package com.medconsult.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.medconsult.ai.dto.AiModels.FeedbackItem;
import com.medconsult.ai.dto.AiModels.FeedbackRequest;
import com.medconsult.ai.dto.AiModels.FeedbackResponse;
import com.medconsult.ai.persistence.entity.AiFeedbackEntity;
import com.medconsult.ai.persistence.mapper.AiFeedbackMapper;
import com.medconsult.ai.util.BusinessIds;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class FeedbackService {
    private final AiFeedbackMapper feedbackMapper;

    public FeedbackService(AiFeedbackMapper feedbackMapper) {
        this.feedbackMapper = feedbackMapper;
    }

    public FeedbackResponse submit(FeedbackRequest request) {
        String feedbackNo = BusinessIds.next("FB");
        AiFeedbackEntity entity = new AiFeedbackEntity();
        entity.setFeedbackNo(feedbackNo);
        entity.setAiResultType(request.aiResultType());
        entity.setAiResultId(request.aiResultId());
        entity.setFeedbackBy(BusinessIds.numericId(request.feedbackBy()));
        entity.setUseful(Boolean.TRUE.equals(request.useful()) ? 1 : 0);
        entity.setAdopted(Boolean.TRUE.equals(request.adopted()) ? 1 : 0);
        entity.setComment(request.comment());
        entity.setCreatedAt(LocalDateTime.now());
        feedbackMapper.insert(entity);
        return new FeedbackResponse(feedbackNo, request.aiResultId());
    }

    public List<FeedbackItem> list(String aiResultType, String aiResultId) {
        return feedbackMapper.selectList(new LambdaQueryWrapper<AiFeedbackEntity>()
                        .eq(aiResultType != null && !aiResultType.isBlank(), AiFeedbackEntity::getAiResultType, aiResultType)
                        .eq(aiResultId != null && !aiResultId.isBlank(), AiFeedbackEntity::getAiResultId, aiResultId)
                        .orderByDesc(AiFeedbackEntity::getCreatedAt))
                .stream()
                .map(item -> new FeedbackItem(
                        item.getFeedbackNo(),
                        String.valueOf(item.getFeedbackBy()),
                        item.getUseful() != null && item.getUseful() == 1,
                        item.getAdopted() != null && item.getAdopted() == 1,
                        item.getComment()
                ))
                .toList();
    }
}
