package com.example.price_tracker.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class ProductionConfigValidator implements ApplicationRunner {

    private static final String DEFAULT_JWT_SECRET = "change-me-to-a-secure-secret-key-123456";

    private final Environment environment;
    private final JwtProperties jwtProperties;

    @Override
    public void run(ApplicationArguments args) {
        validate();
    }

    public void validate() {
        if (!isProd()) {
            return;
        }
        requireStrongJwtSecret();
        requireNonBlankProperty("spring.datasource.password");
        requireNonBlankProperty("spring.rabbitmq.password");
    }

    private boolean isProd() {
        return Arrays.asList(environment.getActiveProfiles()).contains("prod");
    }

    private void requireStrongJwtSecret() {
        String secret = jwtProperties.getSecret();
        if (secret == null || secret.isBlank() || DEFAULT_JWT_SECRET.equals(secret) || secret.length() < 32) {
            throw new IllegalStateException("prod profile requires a non-default jwt.secret with at least 32 characters");
        }
    }

    private void requireNonBlankProperty(String propertyName) {
        String value = environment.getProperty(propertyName);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("prod profile requires non-blank " + propertyName);
        }
    }
}
