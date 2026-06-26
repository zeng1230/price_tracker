# SQL Index Explain

This document records the query-driven indexes managed by Flyway under
`src/main/resources/db/migration/`.
The project uses MyBatis Plus `LambdaQueryWrapper`, so each index below is mapped to the
current service-layer query shape instead of indexing every column.

## Added Indexes

| Index | Table | Columns | Query covered | Code location | Design reason |
| --- | --- | --- | --- | --- | --- |
| `idx_notification_user_created_at` | `tb_notification` | `user_id, created_at` | Current user notification page: `WHERE user_id = ? ORDER BY created_at DESC` | `NotificationServiceImpl.pageMyNotifications` | Filters by one user first, then lets MySQL read that user's notifications in time order for pagination. |
| `ux_notification_event_key` | `tb_notification` | `event_key` | Price alert notification idempotency by business event key | `NotificationServiceImpl.consumePriceAlert` | Enforces long-term uniqueness for new notification events while allowing legacy rows with `NULL` event keys. |
| `idx_price_history_product_captured_at` | `tb_price_history` | `product_id, captured_at` | Product history page plus single-product trend aggregation, 7/30-day windows, and first/last sample lookup | `PriceHistoryServiceImpl.pageByProductId`, `PriceHistoryMapper.selectPriceTrendAggregate` | The index narrows reads to one product and supports time ordering. No covering index is added because it would increase history-write cost while the aggregate still needs to inspect selected samples. |
| `idx_watchlist_user_status_updated_at` | `tb_watchlist` | `user_id, status, updated_at` | Current user active watchlist page: `WHERE user_id = ? AND status = ? ORDER BY updated_at DESC` | `WatchlistServiceImpl.pageMyWatchlist` | Keeps the user's active watchlist query selective and supports stable pagination ordering. |
| `idx_watchlist_product_status_notify` | `tb_watchlist` | `product_id, status, notify_enabled` | Price refresh fan-out: `WHERE product_id = ? AND status = 1 AND notify_enabled = 1` | `PriceServiceImpl.refreshProductPriceInternal` | Price refresh needs all active subscribers for one product; this avoids scanning unrelated watch records. |
| `idx_product_status_id` | `tb_product` | `status, id` | Scheduled refresh scan: `WHERE status = 1 ORDER BY id ASC` | `PriceServiceImpl.refreshActiveProducts` | Batch refresh scans active products in id order; the index matches the filter and order. |
| `idx_product_status_updated_at` | `tb_product` | `status, updated_at` | Product list page: `WHERE status = 1 ORDER BY updated_at DESC` | `ProductServiceImpl.pageProducts` | Product listing filters active products and sorts by update time. |

## Existing Indexes Not Duplicated

| Existing index | Table | Source | Reason no new index is added |
| --- | --- | --- | --- |
| `PRIMARY` | All business tables | Existing table DDL | Primary-key lookups such as `selectById` already use the clustered primary key. |
| `username` unique index | `tb_user` | `V1__init_schema.sql` declares `username VARCHAR(50) NOT NULL UNIQUE` | Login and register lookups use `username`; the unique constraint already provides the required index. |
| `uk_user_product` | `tb_watchlist` | `V1__init_schema.sql` | Duplicate-watch checks use `user_id + product_id`; the unique constraint already covers this exact lookup. |

## Indexes Intentionally Not Added

| Not added | Reason |
| --- | --- |
| Single-column `status` indexes on `tb_product` or `tb_watchlist` | `status` is low-cardinality and is only useful here when combined with the query's leading selective column or sort column. |
| Single-column `notify_enabled` index | It is a low-cardinality flag and only appears with `product_id` and `status` in the current alert fan-out query. |
| Index on `tb_notification.is_read` | The current code does not filter notification pages by read state. |
| Index on `tb_notification.product_id` | Current notification listing is user-centered; product details are loaded through `selectBatchIds`, which uses `tb_product` primary keys. |
| Indexes on product keyword search fields | `ProductServiceImpl.pageProducts` uses `LIKE` on `product_name` and `platform`. A normal b-tree index would not reliably optimize contains-style `LIKE`; full-text search is out of scope for this backend resume project. |
| Foreign key constraints | The current schema keeps simple table DDL for local setup. Adding FK constraints would be a separate data-integrity task and may affect delete/update behavior. |

## Verification Suggestions

Start the application against an empty MySQL 8 database so Flyway applies all migrations, then verify `flyway_schema_history`:

```powershell
docker exec price-tracker-mysql mysql -uroot -p123456 price_tracker -e "SELECT installed_rank,version,description,success FROM flyway_schema_history ORDER BY installed_rank;"
```

Example `EXPLAIN` checks:

```sql
EXPLAIN SELECT * FROM tb_notification
WHERE user_id = 1
ORDER BY created_at DESC
LIMIT 10;

EXPLAIN SELECT * FROM tb_price_history
WHERE product_id = 1
ORDER BY captured_at DESC
LIMIT 10;

EXPLAIN ANALYZE
SELECT COUNT(*),
       SUM(new_price),
       MIN(CASE
               WHEN captured_at >= NOW() - INTERVAL 7 DAY
               THEN LEAST(COALESCE(old_price, new_price), new_price)
           END)
FROM tb_price_history
WHERE product_id = 1;

EXPLAIN SELECT * FROM tb_watchlist
WHERE product_id = 1 AND status = 1 AND notify_enabled = 1;

EXPLAIN SELECT * FROM tb_product
WHERE status = 1
ORDER BY id ASC
LIMIT 100;
```
