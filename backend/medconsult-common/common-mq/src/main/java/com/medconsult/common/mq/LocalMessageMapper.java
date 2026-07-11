package com.medconsult.common.mq;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

/**
 * LocalMessage 的 Mapper。
 *
 * <p>除 BaseMapper 通用方法外，提供基于状态条件的 CAS 更新——避免 confirm/dispatch
 * 并发读改写同一行导致"丢失更新"（retryCount 不自增、状态翻转）。
 */
@Mapper
public interface LocalMessageMapper extends BaseMapper<LocalMessage> {

    /**
     * CAS：仅当当前状态在 expectedStatuses 内时，更新状态为 newStatus。
     * <p>用于 confirm ack（→CONFIRMED）和 dispatch 重投（→SENT），WHERE 条件保证
     * 已是终态（CONFIRMED/FAILED）的消息不会被并发改回。
     *
     * @param id              消息主键
     * @param expectedStatuses 允许的前置状态（如 ['PENDING','SENT']）
     * @param newStatus       目标状态
     * @param nextRetryAt     新的下次重试时间（null 不更新）
     * @return 受影响行数（1=成功，0=状态已变未更新）
     */
    default int casUpdateStatus(Long id, java.util.Collection<String> expectedStatuses,
                                String newStatus, LocalDateTime nextRetryAt) {
        LambdaUpdateWrapper<LocalMessage> uw = new LambdaUpdateWrapper<LocalMessage>()
                .eq(LocalMessage::getId, id)
                .in(LocalMessage::getStatus, expectedStatuses)
                .set(LocalMessage::getStatus, newStatus)
                .set(LocalMessage::getUpdatedAt, LocalDateTime.now());
        if (nextRetryAt != null) {
            uw.set(LocalMessage::getNextRetryAt, nextRetryAt);
        }
        return this.update(null, uw);
    }

    /**
     * CAS 重试：仅当状态在 expectedStatuses 内时，retry_count 原子自增 + 状态/退避更新。
     * <p><b>retry_count 用 SQL 自增（retry_count = retry_count + 1）</b>，不是读后写——
     * 这是消除"丢失更新"的关键：并发 confirm nack 与 dispatch 重投各自的自增互不覆盖。
     *
     * @param id               消息主键
     * @param expectedStatuses 允许的前置状态
     * @param newStatus        重试后的状态（SENT 继续重试 / FAILED 终态）
     * @param nextRetryAt      新的退避重试时间
     * @return 受影响行数（1=成功，0=状态已变未自增）
     */
    default int casRetry(Long id, java.util.Collection<String> expectedStatuses,
                         String newStatus, LocalDateTime nextRetryAt) {
        LambdaUpdateWrapper<LocalMessage> uw = new LambdaUpdateWrapper<LocalMessage>()
                .eq(LocalMessage::getId, id)
                .in(LocalMessage::getStatus, expectedStatuses)
                .setSql("retry_count = retry_count + 1")
                .set(LocalMessage::getStatus, newStatus)
                .set(LocalMessage::getNextRetryAt, nextRetryAt)
                .set(LocalMessage::getUpdatedAt, LocalDateTime.now());
        return this.update(null, uw);
    }
}
