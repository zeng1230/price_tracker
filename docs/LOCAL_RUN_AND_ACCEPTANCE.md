# 本地启动与角色验收

本文给出 Windows PowerShell 下可复制的端到端流程。应用地址默认为 `http://localhost:8080`。

## 1. SQL 初始化与旧库升级

Docker Compose 挂载并按顺序执行 `src/main/resources/sql/` 下的数据库、五张业务表和索引脚本。`tb_user.sql` 已包含：

```sql
role VARCHAR(20) NOT NULL DEFAULT 'USER'
```

该初始化仅在空 MySQL volume 首次启动时执行。已有数据库先检查字段：

```powershell
docker exec price-tracker-mysql mysql -uroot -p123456 price_tracker `
  -e "SHOW COLUMNS FROM tb_user LIKE 'role';"
```

如果没有结果，再执行一次：

```powershell
docker exec price-tracker-mysql mysql -uroot -p123456 price_tracker `
  -e "ALTER TABLE tb_user ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER' AFTER nickname, ADD CONSTRAINT chk_tb_user_role CHECK (role IN ('USER','ADMIN'));"
```

数据库角色值统一为大写 `USER`、`ADMIN`。应用解析时会 trim 并转大写，但非法值会被拒绝，不会降级为 `USER`。

升级前签发的 JWT 不含 `role`，会返回 `UNAUTHORIZED`。升级后必须重新登录获取新 token。

## 2. 启动依赖与应用

```powershell
Copy-Item .env.example .env
docker compose up -d
docker compose ps
docker exec price-tracker-mysql mysqladmin ping -uroot -p123456
docker exec price-tracker-redis redis-cli ping
docker exec price-tracker-rabbitmq rabbitmq-diagnostics -q ping
```

在另一个 PowerShell 窗口运行：

```powershell
$env:REDIS_HOST="127.0.0.1"
$env:RABBITMQ_HOST="127.0.0.1"
./mvnw.cmd spring-boot:run
```

健康检查：

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health | ConvertTo-Json -Depth 10
```

## 3. 创建 ADMIN 与 USER

```powershell
$base = "http://localhost:8080"
$stamp = Get-Date -Format "yyyyMMddHHmmssfff"
$adminName = "admin_$stamp"
$userName = "user_$stamp"

Invoke-RestMethod -Method Post -Uri "$base/api/auth/register" -ContentType "application/json" `
  -Body (@{username=$adminName; password="Passw0rd!"; email="$adminName@example.com"} | ConvertTo-Json)

Invoke-RestMethod -Method Post -Uri "$base/api/auth/register" -ContentType "application/json" `
  -Body (@{username=$userName; password="Passw0rd!"; email="$userName@example.com"} | ConvertTo-Json)

docker exec price-tracker-mysql mysql -uroot -p123456 price_tracker `
  -e "UPDATE tb_user SET role='ADMIN' WHERE username='$adminName';"
```

注册响应中的角色都应为 `USER`。数据库提权后必须重新登录：

```powershell
$adminLogin = Invoke-RestMethod -Method Post -Uri "$base/api/auth/login" -ContentType "application/json" `
  -Body (@{username=$adminName; password="Passw0rd!"} | ConvertTo-Json)
$userLogin = Invoke-RestMethod -Method Post -Uri "$base/api/auth/login" -ContentType "application/json" `
  -Body (@{username=$userName; password="Passw0rd!"} | ConvertTo-Json)

$adminHeaders = @{Authorization="Bearer $($adminLogin.data.token)"}
$userHeaders = @{Authorization="Bearer $($userLogin.data.token)"}
```

## 4. 端到端角色链路

流程固定为：ADMIN 创建商品，USER 关注，ADMIN 刷新，USER 查询历史和通知。

```powershell
$product = Invoke-RestMethod -Method Post -Uri "$base/api/products" `
  -Headers $adminHeaders -ContentType "application/json" `
  -Body (@{
    productName="Acceptance Product $stamp"
    productUrl="https://example.com/products/$stamp"
    platform="mock"
    currentPrice=100.00
    currency="CNY"
  } | ConvertTo-Json)
$productId = $product.data

$watch = Invoke-RestMethod -Method Post -Uri "$base/api/watchlist" `
  -Headers $userHeaders -ContentType "application/json" `
  -Body (@{productId=$productId; targetPrice=999999.00; notifyEnabled=1} | ConvertTo-Json)

$history = $null
for ($i = 1; $i -le 20; $i++) {
  Invoke-RestMethod -Method Post -Uri "$base/api/admin/products/$productId/refresh-price" `
    -Headers $adminHeaders | Out-Null
  $history = Invoke-RestMethod -Method Get `
    -Uri "$base/api/products/$productId/price-history?pageNum=1&pageSize=10" `
    -Headers $userHeaders
  if ($history.data.total -gt 0) { break }
}
if ($history.data.total -eq 0) { throw "Mock price did not change after 20 attempts" }

$notifications = $null
for ($i = 1; $i -le 20; $i++) {
  $notifications = Invoke-RestMethod -Method Get `
    -Uri "$base/api/notifications/my?pageNum=1&pageSize=10" -Headers $userHeaders
  if ($notifications.data.total -gt 0) { break }
  Start-Sleep -Milliseconds 500
}
if ($notifications.data.total -eq 0) { throw "No notification consumed within 10 seconds" }

$notificationId = $notifications.data.records[0].id
Invoke-RestMethod -Method Put -Uri "$base/api/notifications/$notificationId/read" `
  -Headers $userHeaders | Out-Null
```

旧 internal 路径仍可用于管理员内部验收：

```powershell
Invoke-RestMethod -Method Post -Uri "$base/api/internal/products/$productId/refresh-price" `
  -Headers $adminHeaders
```

## 5. 权限失败验收

```powershell
$forbidden = Invoke-RestMethod -Method Get -Uri "$base/api/admin/users" -Headers $userHeaders
if ($forbidden.code -ne 403) { throw "Expected FORBIDDEN for USER" }

$unauthorized = Invoke-RestMethod -Method Get -Uri "$base/api/admin/users"
if ($unauthorized.code -ne 401) { throw "Expected UNAUTHORIZED without token" }
```

项目统一使用 HTTP 200 和 `Result.code` 表达业务错误。响应不包含 token、密码或密码摘要。

## 6. 商品停用与重新启用

```powershell
Invoke-RestMethod -Method Put -Uri "$base/api/admin/products/$productId/status" `
  -Headers $adminHeaders -ContentType "application/json" -Body '{"status":0}'

$allProducts = Invoke-RestMethod -Method Get -Uri "$base/api/admin/products" -Headers $adminHeaders
if (-not ($allProducts.data.records | Where-Object { $_.id -eq $productId -and $_.status -eq 0 })) {
  throw "Disabled product is not visible to ADMIN"
}

Invoke-RestMethod -Method Put -Uri "$base/api/admin/products/$productId/status" `
  -Headers $adminHeaders -ContentType "application/json" -Body '{"status":1}'
```

管理员列表和状态更新使用自定义 SQL 绕过 MyBatis-Plus 全局 `status` 逻辑删除过滤，因此停用商品仍可查询并重新启用。

本轮没有用户状态更新接口；未来若新增，必须禁止管理员禁用自己。

## 7. 测试命令

```powershell
./mvnw.cmd -q -DskipTests compile
./mvnw.cmd -q test
```

当前没有复杂 RBAC、角色/权限关联表、菜单权限或后台前端。
