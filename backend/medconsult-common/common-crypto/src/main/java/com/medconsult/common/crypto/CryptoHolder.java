package com.medconsult.common.crypto;

/**
 * {@link CryptoService} 静态 holder，桥接 Spring Bean 与 MyBatis TypeHandler。
 *
 * <p>MyBatis TypeHandler 由 MyBatis 实例化，无法用 Spring @Autowired 注入 CryptoService。
 * 用静态字段 holder：{@link MedConsultCryptoAutoConfiguration} 装配时调用 {@link #set}，
 * {@link EncryptedStringTypeHandler} 通过 {@link #get} 获取。
 *
 * <p>线程安全：CryptoService 无状态（仅持 keySpec + SecureRandom），静态引用安全共享。
 */
public final class CryptoHolder {

    private static volatile CryptoService instance;

    private CryptoHolder() {}

    public static void set(CryptoService crypto) {
        instance = crypto;
    }

    /** 返回当前 CryptoService；未配置加密 key 时返回 null（TypeHandler 据此走明文兼容） */
    public static CryptoService get() {
        return instance;
    }
}
