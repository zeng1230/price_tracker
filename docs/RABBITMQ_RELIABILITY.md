# RabbitMQ Reliability

## 1. Scope

This document describes the minimal RabbitMQ P0 failure-closure path for price-alert
notifications. The business flow remains:

```text
PriceServiceImpl
  -> PriceAlertProducer
  -> RabbitMQ
  -> PriceAlertConsumer
  -> NotificationServiceImpl
  -> tb_notification
```

## 2. Topology

| Item | Value |
| --- | --- |
| Main exchange | `price.alert.exchange` |
| Main exchange type | durable `DirectExchange` |
| Main queue | `price.alert.queue` |
| Main routing key | `price.alert` |
| Dead-letter exchange | `price.alert.dlx` |
| Dead-letter exchange type | durable `DirectExchange` |
| Dead-letter queue | `price.alert.dlq` |
| Dead-letter routing key | `price.alert.dlq` |

The main queue declares:

```text
x-dead-letter-exchange = price.alert.dlx
x-dead-letter-routing-key = price.alert.dlq
```

## 3. Consumer Retry

Spring AMQP simple listener retry is enabled in `application.yml`.

| Setting | Default |
| --- | --- |
| Maximum attempts | `3` |
| Initial interval | `1s` |
| Multiplier | `2` |
| Maximum interval | `10s` |
| Requeue after retry exhaustion | `false` |

When `NotificationService.consumePriceAlert(...)` throws an exception, the consumer
deletes its Redis idempotency key and rethrows the exception. Spring AMQP retries the
listener call with the configured bound. After the retry budget is exhausted, the
message is rejected without requeue and RabbitMQ routes it from the main queue to
`price.alert.dlx`, then to `price.alert.dlq`.

Invalid messages with missing required business fields follow the same bounded retry
and DLQ path because `NotificationServiceImpl` rejects them with an exception.

## 4. Idempotency Keys

Producer-side key:

```text
price-tracker:idempotent:notify:{userId}:{productId}:{targetPrice}
```

Consumer-side key when `messageId` is present:

```text
price-tracker:idempotent:notify:mq:{messageId}
```

Consumer-side fallback key:

```text
price-tracker:idempotent:notify:mq:{userId}:{productId}:{targetPrice}:{currentPrice}:{triggeredAt}
```

Lifecycle:

| Scenario | Key handling | Message result |
| --- | --- | --- |
| Producer publish succeeds | Keep producer key until TTL | Publish completes |
| Producer publish throws synchronously | Delete producer key | Rethrow to caller |
| Consumer succeeds | Keep consumer key until TTL | ACK |
| Consumer sees duplicate key | Keep existing consumer key | Skip and ACK |
| Consumer business handling fails | Delete consumer key | Rethrow for bounded retry |
| Watchlist is inactive or notification is disabled | Consumer handling returns normally | ACK |
| Price no longer meets the target | Consumer handling returns normally | ACK |

## 5. Management UI Verification

Start RabbitMQ and open:

```text
http://localhost:15672
```

Using the configured RabbitMQ credentials:

Before deploying this topology over an existing broker, inspect the ready and
unacknowledged counts of `price.alert.queue`. RabbitMQ queue arguments are immutable.
If the queue was created before the dead-letter arguments were added, drain or handle
existing messages, delete the old queue, and let the application declare it again.

1. Open **Exchanges** and verify `price.alert.exchange` and `price.alert.dlx`.
2. Open **Queues and Streams** and verify `price.alert.queue` and `price.alert.dlq`.
3. Open `price.alert.queue` and verify its dead-letter arguments reference
   `price.alert.dlx` and `price.alert.dlq`.
4. Open `price.alert.dlx` and verify its binding routes `price.alert.dlq` to
   `price.alert.dlq`.
5. Publish or trigger a message that consistently fails consumer validation. After
   the listener retry budget is exhausted, verify that the DLQ ready count increases.

## 6. Current Boundaries

This P0 change does not implement:

- transactional outbox
- publisher confirm or return callbacks
- `mq_message_log`
- RabbitMQ transactional messages
- delayed retry queues
- automated DLQ replay or operator tooling

The application now has bounded consumer retry and a DLQ for failed deliveries, but
database updates and MQ publication are still not one atomic transaction.
