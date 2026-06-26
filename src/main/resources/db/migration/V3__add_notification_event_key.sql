ALTER TABLE tb_notification
    ADD COLUMN event_key VARCHAR(191) NULL COMMENT 'notification business event key' AFTER watchlist_id;

CREATE UNIQUE INDEX ux_notification_event_key
    ON tb_notification (event_key)
    COMMENT 'Deduplicate notification business events while allowing legacy NULL values';
