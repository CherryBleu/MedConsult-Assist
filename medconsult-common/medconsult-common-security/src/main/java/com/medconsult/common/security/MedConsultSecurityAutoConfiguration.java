package com.medconsult.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * common-security 自动装配（架构文档 §4）。
 *
 * <p>业务服务引入 {@code medconsult-common-security} 后：
 * <ul>
 *   <li>{@link JwtCodec} Bean 创建（密钥从 {@code medconsult.security.jwt.secret} 配置注入）</li>
 *   <li>{@link PermissionAspect} Bean 创建（切 @Permission 注解）</li>
 * </ul>
 *
 * <p>密钥配置约束：≥ 32 字节；生产环境由 Nacos + KMS 注入，不入仓库（架构文档 §7.3）。
 * 业务服务可在 application.yml 写：
 * <pre>
 *   medconsult:
 *     security:
 *       jwt:
 *         secret: ${JWT_SECRET}   # 从环境变量/KMS 注入
 * </pre>
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "io.jsonwebtoken.Jwts")
public class MedConsultSecurityAutoConfiguration {

    /**
     * 仅当显式配置了 medconsult.security.jwt.secret 才创建 JwtCodec。
     * 这样只依赖 @Permission 切面（不需要签发 Token）的服务（如通过 common-feign 传递引入本模块）
     * 不会因 secret 为空而启动失败。签发/解析 Token 的服务（auth-service 等）必须配置该属性。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "medconsult.security.jwt.secret")
    public JwtCodec jwtCodec(@Value("${medconsult.security.jwt.secret}") String secret) {
        return new JwtCodec(secret);
    }

    @Bean
    @ConditionalOnMissingBean
    public PermissionAspect permissionAspect() {
        return new PermissionAspect();
    }
}
