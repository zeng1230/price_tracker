# Design Specification: Production Candidate v1

## Goal Description
The objective of this phase is to transition the Price Tracker from an "internally runnable MVP" to a "production-ready candidate (v1)" suitable for small-scale deployment. This specification details the implementation of real price scraping using SerpApi, external notifications via Webhooks, multi-instance scheduler safety using db-level claims, production-ready environment configurations, JWT revocation capabilities, Prometheus metrics integration, and administrative运维 APIs.

---

## 1. Multi-Instance Scheduler Safety & DB Migrations

### Database Migrations (`V6__add_relay_claim_and_delivery.sql`)
1. **Alter `tb_outbox_event`**:
   - `claim_owner` VARCHAR(191) NULL
   - `claimed_at` DATETIME NULL
   - `claimed_until` DATETIME NULL
   - Index `idx_outbox_event_claim` on `(status, next_retry_at, claimed_until)` to optimize the claim query.
2. **Create `tb_notification_delivery`**:
   - `id` BIGINT AUTO_INCREMENT PRIMARY KEY
   - `notification_id` BIGINT NOT NULL
   - `channel` VARCHAR(32) NOT NULL
   - `payload` JSON NOT NULL
   - `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING'
   - `attempts` INT NOT NULL DEFAULT 0
   - `next_retry_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
   - `last_error` VARCHAR(1000) NULL
   - `claim_owner` VARCHAR(191) NULL
   - `claimed_at` DATETIME NULL
   - `claimed_until` DATETIME NULL
   - `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
   - `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
   - Foreign key constraint to `tb_notification.id`
   - Index `idx_delivery_status_retry_claim` on `(status, next_retry_at, claimed_until)`
   - Index `idx_delivery_notification_id` on `(notification_id)`

### Scheduler claiming logic
For both `OutboxRelay` and `NotificationDeliveryRelay`:
- Generate a unique `claimOwner` identifier at startup (e.g. `UUID`).
- **Claim Batch**: Run an atomic `UPDATE` to claim a batch of events with a lease time (e.g., 2 minutes):
  ```sql
  UPDATE tb_xxx
  SET claim_owner = #{claimOwner},
      claimed_at = #{now},
      claimed_until = #{claimedUntil}
  WHERE status IN ('PENDING', 'FAILED_RETRYABLE')
    AND next_retry_at <= #{now}
    AND (claimed_until IS NULL OR claimed_until <= #{now})
  ORDER BY next_retry_at ASC, id ASC
  LIMIT #{limit}
  ```
- **Fetch Claimed**: Select all events locked by the current owner:
  ```sql
  SELECT * FROM tb_xxx
  WHERE claim_owner = #{claimOwner}
    AND status IN ('PENDING', 'FAILED_RETRYABLE')
    AND claimed_until > #{now}
  ```
- **Release**: Upon completion, clear the `claim_owner`, `claimed_at`, and `claimed_until` columns. If it fails and is retryable, calculate exponential backoff and update `next_retry_at`.

---

## 2. SerpApi Price Provider & Error Categories

### Properties & Activation
Enabled via `price-provider.serpapi.enabled` (default `false`). When disabled, routing falls back to `MockPriceProvider`.
- `price-provider.serpapi.api-key`
- `price-provider.serpapi.base-url`
- `price-provider.serpapi.timeout` (default 5000ms)

### Query Logic
- Extract ASIN from `Product.productUrl` using regular expressions.
- If ASIN is present: Query using `engine=amazon_product` and `asin=ASIN`.
- If ASIN is not present: Query using `engine=amazon` and search query `k=productName`.
- Domain is parsed from the URL (e.g. `amazon.co.uk`) and mapped to `amazon_domain` parameter, falling back to `amazon.com`.

### Error Mapping
`SerpApiPriceProvider` throws a `PriceProviderException` holding one of the following `PriceProviderFailureType` error categories:
- `TIMEOUT`: HTTP read/connection timeout.
- `RATE_LIMITED`: HTTP status `429` (Quota exceeded).
- `AUTHENTICATION_FAILED`: HTTP status `401` or `403` (Invalid API Key).
- `NOT_FOUND`: Empty organic results list or "product not found" message.
- `INVALID_DATA`: Missing price field in response or invalid price string.
- `UNKNOWN`: Catch-all for network or system errors.

---

## 3. Webhook Notifications & Delivery Relay

### Triggering
When `consumePriceAlert` successfully persists a new `tb_notification` and `notification.webhook.enabled` is `true`:
- Construct a detailed notification JSON object:
  ```json
  {
    "id": 123,
    "userId": 99,
    "productId": 1,
    "productName": "Laptop",
    "notifyType": "TARGET_PRICE_REACHED",
    "content": "Laptop current price 79.00 reached target 80.00",
    "createdAt": "2026-07-06T19:50:00"
  }
  ```
- Persist it directly into the `payload` column of `tb_notification_delivery` as a `PENDING` job.

### Delivery & Signature
- The `NotificationDeliveryRelay` reads and claims pending deliveries.
- Computes signature: hex-encoded HMAC-SHA256 signature of the raw JSON body using the configured `notification.webhook.secret`.
- Sends POST request via `RestTemplate` with timeout, including headers:
  - `Content-Type: application/json`
  - `X-Signature-256: <signature_hex>`
- Processes responses:
  - 2xx: Success -> Mark `SENT`.
  - 4xx: Client Error -> Mark `DEAD`.
  - 5xx / Timeout / Socket error: Retryable -> Mark `FAILED_RETRYABLE`, increment attempts, update `next_retry_at` using exponential backoff.
  - Exceeded maximum attempts (default 5): Mark `DEAD`.

---

## 4. Production Environment Configuration & Security Validations

### Production Profile Configurations
- `application-prod.yml` created.
- Sensitive values like JWT secret, Database credentials, Redis, and RabbitMQ passwords are bound exclusively to environment variables.

### Startup Security Validation
A Spring `ProdConfigValidator` runs on startup and validates:
- If active profile is `prod`:
  - JWT secret must not be default `change-me-to-a-secure-secret-key-123456` and must be at least 32 characters long.
  - MySQL database and RabbitMQ passwords must not be empty or set to their respective development defaults (`123456` / `guest`).
  - Webhook secret (if enabled) must not be empty.
  - Any violation aborts context startup by throwing `IllegalStateException`.

### JWT Revocation
- In `JwtTokenUtil.generateAccessToken()`, generate and claim a unique `jti` (UUID string) on the token payload.
- In `AuthController.java`, expose `POST /api/auth/logout`:
  - Parse the current token, extract its `jti` and `expirationTime`.
  - Save `jti` to Redis under the key `price-tracker:auth:blacklist:{jti}` with a TTL corresponding to the remaining time until expiration.
- In `AuthInterceptor.java`, retrieve the parsed token payload and verify if `jti` is blacklisted in Redis. Reject the request with HTTP `401 Unauthorized` if Revoked.

---

## 5. Observability & Management APIs

### Metrics Exposure
- Add dependency `micrometer-registry-prometheus`.
- In `application-prod.yml`, expose only Actuator `health` and `prometheus` endpoints.
- Collect metrics for:
  - Price scraping success rates and failure breakdown by `failure_type`.
  - Relay event processing success, failure, and dead counts.
  - Webhook deliveries and response codes.
  - RabbitMQ listener consumption errors.

### Administrative Management APIs (Admin Required)
Expose the following endpoints in `AdminController`:
- `GET /api/admin/outbox/dead?pageNum=1&pageSize=10`: Retrieve a page of DEAD outbox events.
- `POST /api/admin/outbox/{id}/retry`: Reset a dead outbox event back to `PENDING` (resetting attempts to 0, next retry to now, and clearing errors).
- `GET /api/admin/notification-deliveries/dead?pageNum=1&pageSize=10`: Retrieve a page of DEAD webhook notification deliveries.
- `POST /api/admin/notification-deliveries/{id}/retry`: Reset a dead webhook delivery task to `PENDING` (resetting attempts to 0, next retry to now, and clearing errors).

### Operations Runbook
Created under `docs/RUNBOOK.md` covering step-by-step procedures for operators:
- Triaging API timeouts or quota limits from SerpApi.
- Resolving and retrying dead outbox events or dead webhooks.
- Re-routing RabbitMQ Dead Letter Queue (DLQ) messages.
- Performing database backups and restore drills.
