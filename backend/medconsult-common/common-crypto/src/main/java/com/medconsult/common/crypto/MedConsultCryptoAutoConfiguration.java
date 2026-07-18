package com.medconsult.common.crypto;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * common-crypto 自动装配（《修改建议》§5.3）。
 *
 * <p>仅当配置 {@code medconsult.crypto.id-no.key} 存在时装配（参照 MedConsultSecurityAutoConfiguration
 * 的 jwtCodec 模式）。装配后通过 {@link CryptoHolder#set} 把 CryptoService 暴露给
 * {@link EncryptedStringTypeHandler}（MyBatis TypeHandler 无法 @Autowired）。
 *
 * <p>未配置 key 时不装配 → CryptoHolder.get() 返回 null → TypeHandler 走明文兼容路径
 * （不影响未启用加密的服务/测试）。
 */
@Slf4j
@AutoConfiguration
@ConditionalOnProperty(name = "medconsult.crypto.id-no.key")
public class MedConsultCryptoAutoConfiguration {

    /**
     * 创建 CryptoService 并同步绑定到静态 holder（供 MyBatis TypeHandler 使用）。
     * <p>在 @Bean 方法内 set holder，避免单独注入方法引发的"自配置类循环依赖"。
     */
    @Bean
    public CryptoService cryptoService(@Value("${medconsult.crypto.id-no.key}") String base64Key) {
        CryptoService cs = new CryptoService(base64Key);
        CryptoHolder.set(cs);
        log.info("[common-crypto] CryptoService 已装配并绑定到 TypeHandler holder，id_no 字段加密启用");
        return cs;
    }
}


