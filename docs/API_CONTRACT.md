# API 契约 (API Contract)

所有应用业务 API 均返回 `Result<T>` 格式：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

分页 API 在 `data` 内部返回 `PageResult<T>` 格式：

```json
{
  "records": [],
  "total": 0,
  "current": 1,
  "size": 10,
  "pages": 0
}
```

通用错误码包括 `400 BAD_REQUEST`、`401 UNAUTHORIZED`、`403 FORBIDDEN`、`404 NOT_FOUND`、`422 VALIDATE_ERROR`、`429 TOO_MANY_REQUESTS`、`1001 PRICE_PROVIDER_NOT_FOUND`、`1002 PRICE_NOT_AVAILABLE` 以及 `500 SYSTEM_ERROR`。

## 用户认证 API (Auth API)

### 注册 (Register)

- 请求方法：`POST`
- 路径：`/api/auth/register`
- 权限要求：公开 (public)
- 请求体：

```json
{
  "username": "user1",
  "password": "secret123",
  "email": "user1@example.com",
  "nickname": "User One"
}
```

- 成功返回 `data`：`UserVo`，包含 `id`、`username`、`email`、`nickname`、`role`、`status`、`createdAt`、`updatedAt`。
- 常见错误：`400` 用户名重复，`422` 请求体不合法。
- 异步链路：无。

### 登录 (Login)

- 请求方法：`POST`
- 路径：`/api/auth/login`
- 权限要求：公开 (public)
- 请求体：

```json
{
  "username": "user1",
  "password": "secret123"
}
```

- 成功返回 `data`：`LoginVo`，包含 `token`。
- 常见错误：`401` 凭证无效，`403` 用户已被禁用，`422` 请求体不合法。
- 异步链路：无。

### 登出 (Logout)

- 请求方法：`POST`
- 路径：`/api/auth/logout`
- 权限要求：需 JWT 认证
- 请求头：`Authorization: Bearer <token>`
- 成功返回 `data`：`null`
- 常见错误：`401` Token 缺失、无效、已过期或已被吊销。
- 异步链路：无。将 Token 的 JTI 写入 Redis 黑名单，直至 Token 过期。

## 管理员 API (Admin API)

所有管理员 API 均要求携带包含 `ADMIN` 角色的 JWT。

### 查询用户列表 (Query Users)

- 请求方法：`GET`
- 路径：`/api/admin/users`
- 参数：`pageNum` 默认 `1`，`pageSize` 默认 `10`，可选参数 `keyword`。
- 成功返回 `data`：`PageResult<UserVo>`。
- 常见错误：`401`、`403`、`422`。
- 异步链路：无。

### 查询所有商品 (Query All Products)

- 请求方法：`GET`
- 路径：`/api/admin/products`
- 参数：`pageNum` 默认 `1`，`pageSize` 默认 `10`，可选参数 `keyword`。
- 成功返回 `data`：`PageResult<ProductPageVo>`（包含未启用的商品）。
- 常见错误：`401`、`403`、`422`。
- 异步链路：无。

### 更新商品状态 (Update Product Status)

- 请求方法：`PUT`
- 路径：`/api/admin/products/{productId}/status`
- 请求体：

```json
{
  "status": 1
}
```

- 成功返回 `data`：`null`
- 常见错误：`401`、`403`、`404`、`422`。
- 异步链路：无。

### 触发价格刷新 (Trigger Price Refresh)

- 请求方法：`POST`
- 路径：`/api/admin/products/{productId}/refresh-price`
- 请求体：无
- 成功返回 `data`：`null`
- 常见错误：`401`、`403`、`404`、`1001`、`500`。
- 异步链路：有。可能会写入价格历史、插入发件箱 (Outbox) 事件、发布 RabbitMQ 消息、创建应用内通知以及在启用时创建 Webhook 交付。

### 查询 DEAD 状态的发件箱事件 (Query DEAD Outbox Events)

- 请求方法：`GET`
- 路径：`/api/admin/outbox/dead`
- 参数：`limit` 默认 `50`，最小为 `1`。
- 成功返回 `data`：`OutboxEvent` 记录列表，字段包括 `id`、`eventKey`、`eventType`、`payload`、`status`、`attempts`、`nextRetryAt`、`lastError`、`claimOwner`、`claimedAt`、`claimedUntil`、`createdAt`、`updatedAt`。
- 常见错误：`401`、`403`、`422`。
- 异步链路：无。

### 重试 DEAD 状态的发件箱事件 (Retry DEAD Outbox Event)

- 请求方法：`POST`
- 路径：`/api/admin/outbox/{id}/retry`
- 请求体：无
- 成功返回 `data`：`null`
- 常见错误：`401`、`403`、`404`、`422`。
- 异步链路：有。将记录重置为可由 `OutboxRelay` 再次中继的状态。

### 查询 DEAD 状态的通知交付任务 (Query DEAD Notification Deliveries)

- 请求方法：`GET`
- 路径：`/api/admin/notification-deliveries/dead`
- 参数：`limit` 默认 `50`，最小为 `1`。
- 成功返回 `data`：`NotificationDelivery` 记录列表，字段包括 `id`、`eventKey`、`channel`、`payload`、`status`、`attempts`、`nextRetryAt`、`lastError`、`claimOwner`、`claimedAt`、`claimedUntil`、`createdAt`、`updatedAt`。
- 常见错误：`401`、`403`、`422`。
- 异步链路：无。

### 重试 DEAD 状态的通知交付任务 (Retry DEAD Notification Delivery)

- 请求方法：`POST`
- 路径：`/api/admin/notification-deliveries/{id}/retry`
- 请求体：无
- 成功返回 `data`：`null`
- 常见错误：`401`、`403`、`404`、`422`。
- 异步链路：有。重置该交付任务以供 `NotificationDeliveryRelay` 处理。

## 商品 API (Product API)

### 创建商品 (Create Product)

- 请求方法：`POST`
- 路径：`/api/products`
- 权限要求：ADMIN
- 请求体：

```json
{
  "productName": "Acceptance Product",
  "productUrl": "https://example.com/product",
  "platform": "amazon",
  "currentPrice": 100.00,
  "currency": "USD",
  "imageUrl": "https://example.com/product.png"
}
```

- 成功返回 `data`：新创建的商品 ID。
- 常见错误：`401`、`403`、`422`。
- 异步链路：无。

### 获取商品详情 (Get Product Detail)

- 请求方法：`GET`
- 路径：`/api/products/{id}`
- 权限要求：需 JWT 认证
- 成功返回 `data`：`ProductDetailVo`。
- 常见错误：`401`、`404`、`422`。
- 异步链路：无。

### 获取当前价格 (Get Current Price)

- 请求方法：`GET`
- 路径：`/api/products/{id}/price`
- 权限要求：需 JWT 认证
- 成功返回 `data`：`ProductPriceVo`，包含 `productId`、`currentPrice`、`currency`、`lastCheckedAt`。
- 常见错误：`401`、`404`、`422`。
- 异步链路：无。

### 查询商品列表 (Query Products)

- 请求方法：`GET`
- 路径：`/api/products`
- 权限要求：需 JWT 认证
- 参数：`pageNum` 默认 `1`，`pageSize` 默认 `10`，可选参数 `keyword`。
- 成功返回 `data`：`PageResult<ProductPageVo>`。
- 常见错误：`401`、`422`。
- 异步链路：无。

### 更新商品信息 (Update Product)

- 请求方法：`PUT`
- 路径：`/api/products/{id}`
- 权限要求：ADMIN
- 请求体：与创建商品字段相同，使用 `ProductUpdateDto`。
- 成功返回 `data`：`null`
- 常见错误：`401`、`403`、`404`、`422`。
- 异步链路：无。

### 删除商品 (Delete Product)

- 请求方法：`DELETE`
- 路径：`/api/products/{id}`
- 权限要求：ADMIN
- 成功返回 `data`：`null`
- 常见错误：`401`、`403`、`404`、`422`。
- 异步链路：无。

### 查询价格历史 (Query Price History)

- 请求方法：`GET`
- 路径：`/api/products/{id}/price-history`
- 权限要求：需 JWT 认证
- 参数：`pageNum` 默认 `1`，`pageSize` 默认 `10`。
- 成功返回 `data`：`PageResult<PriceHistoryVo>`，包含 `id`、`productId`、`oldPrice`、`newPrice`、`capturedAt`、`source`。
- 常见错误：`401`、`404`、`422`。
- 异步链路：无。

### 查询价格趋势 (Query Price Trend)

- 请求方法：`GET`
- 路径：`/api/products/{id}/price-trend`
- 权限要求：需 JWT 认证
- 成功返回 `data`：`PriceTrendVo`，包含当前价格、7 天最低价、30 天最低价、历史最低/最高价、平均价格、变动次数、与最低价的差额以及上次变动时间。
- 常见错误：`401`、`404`、`1002`、`422`。
- 异步链路：无。

## 监听列表 API (Watchlist API)

### 创建监听项 (Create Watchlist Entry)

- 请求方法：`POST`
- 路径：`/api/watchlist`
- 权限要求：需 JWT 认证，受 Redis 限流控制
- 请求体：

```json
{
  "productId": 1,
  "targetPrice": 80.00,
  "notifyEnabled": 1
}
```

- 成功返回 `data`：新创建的监听项 ID。
- 常见错误：`401`、`404`、`422`、`429`。
- 异步链路：无即时异步链路。该操作会配置后续的价格刷新警报行为。

### 查询我的监听列表 (Query My Watchlist)

- 请求方法：`GET`
- 路径：`/api/watchlist/my`
- 权限要求：需 JWT 认证
- 参数：`pageNum` 默认 `1`，`pageSize` 默认 `10`。
- 成功返回 `data`：`PageResult<WatchlistVo>`。
- 常见错误：`401`、`422`。
- 异步链路：无。

### 更新监听项 (Update Watchlist Entry)

- 请求方法：`PUT`
- 路径：`/api/watchlist/{id}`
- 权限要求：需 JWT 认证，所有者 (owner)，受 Redis 限流控制
- 请求体：

```json
{
  "targetPrice": 75.00,
  "notifyEnabled": 1
}
```

- 成功返回 `data`：`null`
- 常见错误：`401`、`404`、`422`、`429`。
- 异步链路：无即时异步链路。该操作会更改后续的警报行为。

### 删除监听项 (Delete Watchlist Entry)

- 请求方法：`DELETE`
- 路径：`/api/watchlist/{id}`
- 权限要求：需 JWT 认证，所有者 (owner)，受 Redis 限流控制
- 成功返回 `data`：`null`
- 常见错误：`401`、`404`、`422`、`429`。
- 异步链路：无。

## 通知 API (Notification API)

### 查询我的通知 (Query My Notifications)

- 请求方法：`GET`
- 路径：`/api/notifications/my`
- 权限要求：需 JWT 认证
- 参数：`pageNum` 默认 `1`，`pageSize` 默认 `10`。
- 成功返回 `data`：`PageResult<NotificationVo>`，包含 `id`、`productId`、`watchlistId`、`productName`、`notifyType`、`content`、`isRead`、`sendStatus`、`createdAt`、`sentAt`。
- 常见错误：`401`、`422`。
- 异步链路：无。

### 标记通知为已读 (Mark Notification Read)

- 请求方法：`PUT`
- 路径：`/api/notifications/{id}/read`
- 权限要求：需 JWT 认证，所有者 (owner)
- 成功返回 `data`：`null`
- 常见错误：`401`、`404`、`422`。
- 异步链路：无。

## 运维 / Actuator API (Ops / Actuator API)

Actuator 路径不属于 `/api/**` 前缀下，不受应用程序 JWT 拦截器的处理。

### 健康检查 (Health)

- 请求方法：`GET`
- 路径：`/actuator/health`
- 权限要求：取决于部署的网络暴露情况。
- 成功返回：Spring Boot Actuator 的健康状态 JSON。
- 常见错误：取决于具体部署。
- 异步链路：无。

### Prometheus 指标 (Prometheus)

- 请求方法：`GET`
- 路径：`/actuator/prometheus`
- 权限要求：取决于部署的网络暴露情况。已在 `prod` 配置文件的配置中启用暴露。
- 成功返回：Prometheus 文本展示格式。
- 常见错误：`404`（如果当前启用的 profile/配置未暴露该端点）。
- 异步链路：无。

## 内部兼容性端点

`POST /api/internal/products/{id}/refresh-price` 存在且需要 ADMIN 权限。在验收和运维流程中，更推荐使用 `POST /api/admin/products/{productId}/refresh-price`。
