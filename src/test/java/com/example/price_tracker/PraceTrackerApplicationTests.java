package com.example.price_tracker;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class PraceTrackerApplicationTests {

    @Autowired
    private Environment environment;

    @Test
    void contextLoads() {
        assertThat(environment.getActiveProfiles()).contains("test");
    }

}
