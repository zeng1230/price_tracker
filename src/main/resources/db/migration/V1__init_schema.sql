CREATE TABLE tb_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'User ID',
    username VARCHAR(50) NOT NULL UNIQUE COMMENT 'Username',
    password VARCHAR(100) NOT NULL COMMENT 'Password hash',
    email VARCHAR(100) DEFAULT NULL COMMENT 'Email',
    nickname VARCHAR(50) DEFAULT NULL COMMENT 'Nickname',
    status TINYINT NOT NULL DEFAULT 1 COMMENT 'Status: 1 active, 0 disabled',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time'
) COMMENT='User table';

CREATE TABLE tb_product (
    id BIGINT AUTO_INCREMENT COMMENT 'Product ID' PRIMARY KEY,
    product_name VARCHAR(255) NOT NULL COMMENT 'Product name',
    product_url VARCHAR(500) NOT NULL COMMENT 'Product URL',
    platform VARCHAR(50) DEFAULT 'amazon' NOT NULL COMMENT 'Platform',
    current_price DECIMAL(10, 2) NULL COMMENT 'Current price',
    currency VARCHAR(10) DEFAULT 'USD' NOT NULL COMMENT 'Currency',
    image_url VARCHAR(500) NULL COMMENT 'Product image URL',
    status TINYINT DEFAULT 1 NOT NULL COMMENT 'Status: 1 active, 0 inactive',
    last_checked_at DATETIME NULL COMMENT 'Last checked time',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT 'Created time',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time'
) COMMENT='Product table';

CREATE TABLE tb_price_history (
    id BIGINT AUTO_INCREMENT COMMENT 'Price history ID' PRIMARY KEY,
    product_id BIGINT NOT NULL COMMENT 'Product ID',
    old_price DECIMAL(10, 2) NULL COMMENT 'Old price',
    new_price DECIMAL(10, 2) NOT NULL COMMENT 'New price',
    captured_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT 'Captured time',
    source VARCHAR(50) DEFAULT 'mock' NOT NULL COMMENT 'Price source'
) COMMENT='Price history table';

CREATE TABLE tb_watchlist (
    id BIGINT AUTO_INCREMENT COMMENT 'Watchlist ID' PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT 'User ID',
    product_id BIGINT NOT NULL COMMENT 'Product ID',
    target_price DECIMAL(10, 2) NULL COMMENT 'Target price',
    notify_enabled TINYINT DEFAULT 1 NOT NULL COMMENT 'Notification enabled: 1 enabled, 0 disabled',
    last_notified_price DECIMAL(10, 2) NULL COMMENT 'Last notified price',
    status TINYINT DEFAULT 1 NOT NULL COMMENT 'Status: 1 active, 0 cancelled',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT 'Created time',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    CONSTRAINT uk_user_product UNIQUE (user_id, product_id)
) COMMENT='User watchlist table';

CREATE TABLE tb_notification (
    id BIGINT AUTO_INCREMENT COMMENT 'Notification ID' PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT 'User ID',
    product_id BIGINT NOT NULL COMMENT 'Product ID',
    watchlist_id BIGINT NOT NULL COMMENT 'Watchlist ID',
    notify_type VARCHAR(50) NOT NULL COMMENT 'Notification type',
    content VARCHAR(500) NOT NULL COMMENT 'Notification content',
    is_read TINYINT DEFAULT 0 NOT NULL COMMENT 'Read flag: 0 unread, 1 read',
    send_status TINYINT DEFAULT 0 NOT NULL COMMENT 'Send status: 0 pending, 1 sent',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT 'Created time',
    sent_at DATETIME NULL COMMENT 'Sent time'
) COMMENT='Notification table';
