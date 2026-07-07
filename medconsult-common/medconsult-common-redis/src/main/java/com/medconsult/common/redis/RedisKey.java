package com.medconsult.common.redis;

/**
 * Redis 键命名约定（架构文档 §7.1）。
 *
 * <p>统一前缀与分隔符，便于检索、批量清理、权限隔离。
 * 命名规则：{@code 业务域:资源:标识[:子标识]}，冒号分层。
 */
public final class RedisKey {

    private RedisKey() {}

    /** 通用前缀，避免与其他系统 Redis 冲突（若共享集群） */
    public static final String PREFIX = "medconsult";

    // ===== 鉴权 =====
    public static final String JWT_BLACKLIST = PREFIX + ":jwt:black:";          // + jti
    public static final String REFRESH_TOKEN = PREFIX + ":auth:refresh:";       // + userId
    public static final String ROLE_PERMS = PREFIX + ":role:";                  // + roleId + ":perms"

    // ===== 业务锁 =====
    public static final String DRUG_STOCK_LOCK = PREFIX + ":lock:drug:";        // + drugId + ":stock"
    public static final String PAYMENT_LOCK = PREFIX + ":lock:payment:";        // + paymentNo
    public static final String SCHEDULE_QUOTA_LOCK = PREFIX + ":lock:schedule:";// + scheduleId

    // ===== 业务计数/缓存 =====
    public static final String SCHEDULE_QUOTA = PREFIX + ":schedule:";          // + id + ":quota"
    public static final String SCHEDULE_AVAILABLE = PREFIX + ":schedule:available:"; // + date
    public static final String MEDICAL_RECORD = PREFIX + ":medical:record:";    // + id

    // ===== 限流 =====
    public static final String RATELIMIT_AI = PREFIX + ":ratelimit:ai:";        // + userId

    // ===== SSE 多实例广播 =====
    public static final String SSE_CHANNEL = PREFIX + ":pubsub:sse:";           // + userId

    // ===== 验证码 =====
    public static final String VERIFY_CODE = PREFIX + ":verify:code:";          // + phone/email

    /**
     * 构造 SSE 广播 channel（架构文档 §9.2）。
     */
    public static String sseChannel(Long userId) {
        return SSE_CHANNEL + userId;
    }
}
