package com.medconsult.common.feign;

import com.medconsult.common.security.JwtCodec;
import com.medconsult.common.security.JwtPayload;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MedConsultFeignAutoConfigurationTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(JwtCodec.class, () -> new JwtCodec(SECRET))
            .withInitializer(ctx -> ctx.getBeanFactory()
                    .setConversionService(ApplicationConversionService.getSharedInstance()))
            .withPropertyValues(
                    "spring.application.name=drug-service",
                    "medconsult.feign.service-token.scope=drug:read,drug:write")
            .withUserConfiguration(MedConsultFeignAutoConfiguration.class);

    @Test
    void defaultServiceTokenProviderSignsCurrentServiceJwt() {
        contextRunner.run(ctx -> {
            AuthRelayInterceptor.ServiceTokenProvider provider =
                    ctx.getBean(AuthRelayInterceptor.ServiceTokenProvider.class);

            String token = provider.get();

            assertThat(token).isNotBlank();
            JwtPayload payload = ctx.getBean(JwtCodec.class).parse(token);
            assertThat(payload.subjectType()).isEqualTo(JwtPayload.SubjectType.SERVICE);
            assertThat(payload.serviceCode()).isEqualTo("drug-service");
            assertThat(payload.scope()).isEqualTo(List.of("drug:read", "drug:write"));
        });
    }
}
