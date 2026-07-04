CREATE TABLE tb_outbox_event (
    id BIGINT AUTO_INCREMENT COMMENT 'Outbox event ID' PRIMARY KEY,
    event_key VARCHAR(191) NOT NULL COMMENT 'Business event key, e.g. PRICE_ALERT_TARGET_REACHED_V1 payload id',
    event_type VARCHAR(100) NOT NULL COMMENT 'Event type, currently PRICE_ALERT_TARGET_REACHED_V1',
    payload JSON NOT NULL COMMENT 'Serialized event payload',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, SENT, FAILED_RETRYABLE, DEAD',
    attempts INT NOT NULL DEFAULT 0 COMMENT 'Publish attempts',
    next_retry_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Next relay retry time',
    last_error VARCHAR(1000) NULL COMMENT 'Last relay error',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    CONSTRAINT ux_outbox_event_key UNIQUE (event_key),
    CONSTRAINT chk_outbox_event_status CHECK (status IN ('PENDING', 'SENT', 'FAILED_RETRYABLE', 'DEAD'))
) COMMENT='Transactional outbox events';

CREATE INDEX idx_outbox_event_status_retry_id
    ON tb_outbox_event (status, next_retry_at, id);
