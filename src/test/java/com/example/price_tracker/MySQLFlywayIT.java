package com.example.price_tracker;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ActiveProfiles("it")
class MySQLFlywayIT {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("price_tracker")
            .withUsername("price_tracker")
            .withPassword("price_tracker");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void verifyDatabaseMigrationAndSchema() {
        // 1. Verify Flyway migrations executed and succeeded
        assertThat(flywayMigrationSucceeded()).isTrue();

        // 2. Verify key tables exist
        assertThat(tableExists("tb_user")).isTrue();
        assertThat(tableExists("tb_product")).isTrue();
        assertThat(tableExists("tb_price_history")).isTrue();
        assertThat(tableExists("tb_watchlist")).isTrue();
        assertThat(tableExists("tb_notification")).isTrue();

        // 3. Verify key indexes exist
        assertThat(indexExists("tb_notification", "ux_notification_event_key")).isTrue();
        assertThat(indexExists("tb_price_history", "idx_price_history_product_captured_at")).isTrue();
    }

    private boolean tableExists(String tableName) {
        String sql = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, tableName);
        return count != null && count > 0;
    }

    private boolean indexExists(String tableName, String indexName) {
        String sql = "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, tableName, indexName);
        return count != null && count > 0;
    }

    private boolean flywayMigrationSucceeded() {
        // Assert that the migration history table itself exists
        if (!tableExists("flyway_schema_history")) {
            return false;
        }

        // Count successful and failed migrations
        String countFailedSql = "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 0";
        Integer failedCount = jdbcTemplate.queryForObject(countFailedSql, Integer.class);
        if (failedCount == null || failedCount > 0) {
            return false;
        }

        String countSuccessSql = "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1";
        Integer successCount = jdbcTemplate.queryForObject(countSuccessSql, Integer.class);
        return successCount != null && successCount >= 1;
    }
}
