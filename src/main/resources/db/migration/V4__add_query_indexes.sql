CREATE INDEX idx_notification_user_created_at
    ON tb_notification (user_id, created_at)
    COMMENT 'Query current user notifications ordered by created_at';

CREATE INDEX idx_price_history_product_captured_at
    ON tb_price_history (product_id, captured_at)
    COMMENT 'Query product price history ordered by captured_at';

CREATE INDEX idx_watchlist_user_status_updated_at
    ON tb_watchlist (user_id, status, updated_at)
    COMMENT 'Query current user active watchlist ordered by updated_at';

CREATE INDEX idx_watchlist_product_status_notify
    ON tb_watchlist (product_id, status, notify_enabled)
    COMMENT 'Find active notification-enabled watch records for a product';

CREATE INDEX idx_product_status_id
    ON tb_product (status, id)
    COMMENT 'Scan active products by id for scheduled refresh';

CREATE INDEX idx_product_status_updated_at
    ON tb_product (status, updated_at)
    COMMENT 'List active products ordered by updated_at';
