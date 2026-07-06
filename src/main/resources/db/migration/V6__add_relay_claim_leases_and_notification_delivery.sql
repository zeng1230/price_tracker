ALTER TABLE tb_outbox_event
    ADD COLUMN claim_owner VARCHAR(100) NULL COMMENT 'Relay instance that claimed this event',
    ADD COLUMN claimed_at DATETIME NULL COMMENT 'Claim start time',
    ADD COLUMN claimed_until DATETIME NULL COMMENT 'Claim lease expiry time';

CREATE INDEX idx_outbox_event_claim_ready
    ON tb_outbox_event (status, next_retry_at, claimed_until, id);

CREATE TABLE tb_notification_delivery (
    id BIGINT AUTO_INCREMENT COMMENT 'Notification delivery ID' PRIMARY KEY,
    event_key VARCHAR(191) NOT NULL COMMENT 'Business event key',
    channel VARCHAR(32) NOT NULL COMMENT 'Delivery channel, e.g. WEBHOOK',
    payload JSON NOT NULL COMMENT 'Delivery payload',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, SENT, FAILED_RETRYABLE, DEAD',
    attempts INT NOT NULL DEFAULT 0 COMMENT 'Delivery attempts',
    next_retry_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Next delivery retry time',
    last_error VARCHAR(1000) NULL COMMENT 'Last delivery error',
    claim_owner VARCHAR(100) NULL COMMENT 'Relay instance that claimed this delivery',
    claimed_at DATETIME NULL COMMENT 'Claim start time',
    claimed_until DATETIME NULL COMMENT 'Claim lease expiry time',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    CONSTRAINT ux_notification_delivery_event_channel UNIQUE (event_key, channel),
    CONSTRAINT chk_notification_delivery_status CHECK (status IN ('PENDING', 'SENT', 'FAILED_RETRYABLE', 'DEAD'))
) COMMENT='External notification delivery tasks';

CREATE INDEX idx_notification_delivery_claim_ready
    ON tb_notification_delivery (status, next_retry_at, claimed_until, id);
