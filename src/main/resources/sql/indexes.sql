-- Price Tracker query indexes.
-- Run after table creation scripts. The MySQL Docker init process executes this file once
-- when the database volume is first created.

-- Supports NotificationServiceImpl.pageMyNotifications:
-- WHERE user_id = ? ORDER BY created_at DESC
CREATE INDEX idx_notification_user_created_at
    ON tb_notification (user_id, created_at)
    COMMENT 'Query current user notifications ordered by created_at';

-- Supports PriceHistoryServiceImpl.pageByProductId:
-- WHERE product_id = ? ORDER BY captured_at DESC
CREATE INDEX idx_price_history_product_captured_at
    ON tb_price_history (product_id, captured_at)
    COMMENT 'Query product price history ordered by captured_at';

-- Supports WatchlistServiceImpl.pageMyWatchlist:
-- WHERE user_id = ? AND status = ? ORDER BY updated_at DESC
CREATE INDEX idx_watchlist_user_status_updated_at
    ON tb_watchlist (user_id, status, updated_at)
    COMMENT 'Query current user active watchlist ordered by updated_at';

-- Supports PriceServiceImpl.refreshProductPriceInternal:
-- WHERE product_id = ? AND status = ? AND notify_enabled = ?
CREATE INDEX idx_watchlist_product_status_notify
    ON tb_watchlist (product_id, status, notify_enabled)
    COMMENT 'Find active notification-enabled watch records for a product';

-- Supports PriceServiceImpl.refreshActiveProducts:
-- WHERE status = ? ORDER BY id ASC
CREATE INDEX idx_product_status_id
    ON tb_product (status, id)
    COMMENT 'Scan active products by id for scheduled refresh';

-- Supports ProductServiceImpl.pageProducts:
-- WHERE status = ? ORDER BY updated_at DESC
CREATE INDEX idx_product_status_updated_at
    ON tb_product (status, updated_at)
    COMMENT 'List active products ordered by updated_at';

-- tb_user.username already has a UNIQUE index in tb_user.sql.
