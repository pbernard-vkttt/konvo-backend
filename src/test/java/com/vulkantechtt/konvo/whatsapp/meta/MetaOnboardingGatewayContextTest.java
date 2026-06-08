package com.vulkantechtt.konvo.whatsapp.meta;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.ObjectMapper;

class MetaOnboardingGatewayContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withPropertyValues("konvo.whatsapp.provider=meta");

    @Test
    void loadsWhenMetaProviderIsEnabled() {
        contextRunner.run(context ->
                assertThat(context).hasSingleBean(MetaOnboardingGateway.class));
    }

    @Configuration(proxyBeanMethods = false)
    @Import(MetaOnboardingGateway.class)
    static class TestConfig {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
