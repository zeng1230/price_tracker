package com.example.price_tracker.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationLayoutTest {

    private static final Path MIGRATION_DIR = Path.of("src/main/resources/db/migration");

    @Test
    void migrationsKeepSchemaChangesInExpectedVersions() throws IOException {
        String v1 = migration("V1__init_schema.sql");
        String v2 = migration("V2__add_user_role.sql");
        String v3 = migration("V3__add_notification_event_key.sql");
        String v4 = migration("V4__add_query_indexes.sql");
        String v5 = migration("V5__add_outbox_event.sql");

        assertThat(v1).contains("CREATE TABLE tb_user", "CREATE TABLE tb_notification");
        assertThat(v1).doesNotContain("role VARCHAR(20)", "event_key");

        assertThat(v2).contains("ALTER TABLE tb_user", "role VARCHAR(20)", "chk_tb_user_role");
        assertThat(v2).doesNotContain("tb_notification", "event_key");

        assertThat(v3).contains("ALTER TABLE tb_notification", "event_key", "ux_notification_event_key");
        assertThat(v3).doesNotContain("chk_tb_user_role");

        assertThat(v4)
                .contains("idx_notification_user_created_at",
                        "idx_price_history_product_captured_at",
                        "idx_watchlist_user_status_updated_at",
                        "idx_watchlist_product_status_notify",
                        "idx_product_status_id",
                        "idx_product_status_updated_at")
                .doesNotContain("ux_notification_event_key", "ALTER TABLE");

        assertThat(v5)
                .contains("CREATE TABLE tb_outbox_event",
                        "event_key",
                        "PRICE_ALERT_TARGET_REACHED_V1",
                        "ux_outbox_event_key",
                        "idx_outbox_event_status_retry_id")
                .doesNotContain("CREATE TABLE tb_notification", "ALTER TABLE tb_notification");
    }

    private static String migration(String fileName) throws IOException {
        return Files.readString(MIGRATION_DIR.resolve(fileName));
    }
}
