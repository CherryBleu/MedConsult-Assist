package com.medconsult.ai.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("ai_feedback")
public class AiFeedbackEntity {
    @TableId
    private Long id;
    private String feedbackNo;
    private String aiResultType;
    private String aiResultId;
    private Long feedbackBy;
    // 旧字段 useful/adopted 保留兼容历史数据，新写入只写 rating
    private Integer useful;
    private Integer rating;
    private Integer adopted;
    private String comment;
    private String adminReply;
    private LocalDateTime repliedAt;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFeedbackNo() {
        return feedbackNo;
    }

    public void setFeedbackNo(String feedbackNo) {
        this.feedbackNo = feedbackNo;
    }

    public String getAiResultType() {
        return aiResultType;
    }

    public void setAiResultType(String aiResultType) {
        this.aiResultType = aiResultType;
    }

    public String getAiResultId() {
        return aiResultId;
    }

    public void setAiResultId(String aiResultId) {
        this.aiResultId = aiResultId;
    }

    public Long getFeedbackBy() {
        return feedbackBy;
    }

    public void setFeedbackBy(Long feedbackBy) {
        this.feedbackBy = feedbackBy;
    }

    public Integer getUseful() {
        return useful;
    }

    public void setUseful(Integer useful) {
        this.useful = useful;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public Integer getAdopted() {
        return adopted;
    }

    public void setAdopted(Integer adopted) {
        this.adopted = adopted;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getAdminReply() {
        return adminReply;
    }

    public void setAdminReply(String adminReply) {
        this.adminReply = adminReply;
    }

    public LocalDateTime getRepliedAt() {
        return repliedAt;
    }

    public void setRepliedAt(LocalDateTime repliedAt) {
        this.repliedAt = repliedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
