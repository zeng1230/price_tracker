package com.example.price_tracker.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionConfigValidatorTest {

    @Test
    void prodProfileRejectsDefaultJwtSecret() {
        JwtProperties jwtProperties = jwtProperties("change-me-to-a-secure-secret-key-123456");
        MockEnvironment environment = prodEnvironment()
                .withProperty("spring.datasource.password", "db-secret")
                .withProperty("spring.rabbitmq.password", "mq-secret");

        ProductionConfigValidator validator = new ProductionConfigValidator(environment, jwtProperties);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jwt.secret");
    }

    @Test
    void prodProfileAcceptsStrongExternalSecrets() {
        JwtProperties jwtProperties = jwtProperties("1234567890123456789012345678901234567890");
        MockEnvironment environment = prodEnvironment()
                .withProperty("spring.datasource.password", "db-secret")
                .withProperty("spring.rabbitmq.password", "mq-secret");

        ProductionConfigValidator validator = new ProductionConfigValidator(environment, jwtProperties);

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    private MockEnvironment prodEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        return environment;
    }

    private JwtProperties jwtProperties(String secret) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(secret);
        properties.setAccessTokenExpireMinutes(120L);
        properties.setIssuer("price-tracker");
        return properties;
    }
}
