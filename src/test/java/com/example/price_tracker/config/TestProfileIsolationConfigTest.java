package com.example.price_tracker.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class TestProfileIsolationConfigTest {

    @Test
    void testProfileDisablesExternalMiddlewareConnections() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application-test.yml"));

        Properties properties = yaml.getObject();

        assertThat(properties).isNotNull();
        assertThat(properties.getProperty("spring.flyway.enabled")).isEqualTo("false");
        assertThat(properties.getProperty("spring.rabbitmq.listener.simple.auto-startup")).isEqualTo("false");
        assertThat(properties.getProperty("spring.rabbitmq.listener.direct.auto-startup")).isEqualTo("false");
        assertThat(properties.getProperty("management.health.rabbit.enabled")).isEqualTo("false");
        assertThat(properties.getProperty("spring.task.scheduling.enabled")).isEqualTo("false");
    }
}
