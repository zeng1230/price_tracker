# AGENTS.md

This vault is an AI-native engineering memory for a Java/Spring Boot Amazon Price Tracker project. Prefer engineering decisions over educational summaries.

## Project Context

- Core project: Price Tracker, a Spring Boot backend using MySQL, Redis, RabbitMQ, and scheduled crawling.
- Current stage: high-concurrency phase is complete; next priority is Integration and delivery.
- Project positioning: backend engineering system showing cache optimization, async notification, rate limiting, idempotency, retry, and pressure-test reasoning.

## Architecture Principles

- MySQL is source of truth. Redis is derived acceleration.
- RabbitMQ is at-least-once. Consumers must be idempotent.
- External crawling is unreliable. Use timeout, bounded concurrency, retry budget, and persisted attempt state.
- Keep business transaction boundaries short. Never hold DB transactions across external HTTP, MQ waits, or Redis operations.
- Prefer local transaction plus outbox/reconciliation over distributed transactions.
- Design every async workflow around unknown result, duplicate delivery, and partial failure.

## Project Structure Expectations

- Controller: HTTP validation and response mapping only.
- Service: business decisions, transaction boundaries, invariant enforcement.
- Repository: persistence queries and schema-facing access.
- Cache: key design, TTL, invalidation, penetration/breakdown protection.
- MQ producer/consumer: payload contract, idempotency key, ack/retry/DLQ behavior.
- Scheduler/crawler: bounded work creation, timeout, retry, progress persistence.

## Engineering Constraints

- Every high-frequency read path needs a cache/index strategy.
- Every state mutation needs a business invariant and failure semantics.
- Every MQ side effect needs durable idempotency protection.
- Every crawler path needs timeout, rate limit, and retry classification.
- Every cache change must name source of truth and invalidation rule.
- Every concurrency feature must name pool/queue limits and backpressure behavior.

## Testing Philosophy

- Tests are executable architecture contracts.
- Unit tests: pure business rules, validation, idempotency key generation.
- Integration tests: MySQL queries, transaction boundaries, Redis cache behavior, RabbitMQ consumer behavior.
- End-to-end tests: product watch -> crawl/price update -> notification intent -> async send.
- Performance tests must state dataset, concurrency, bottleneck, and acceptance threshold.

## Distributed System Considerations

- Timeout means result unknown.
- Retry only safe operations or operations guarded by idempotency/version constraints.
- Use durable workflow state for recovery; logs are not recovery state.
- Queue lag, retry count, DLQ count, cache hit ratio, and crawl success rate are required operational signals.
- Strong consistency is scoped to business invariants; stale reads are acceptable only when documented.

## Database Design Principles

- Unique constraints enforce idempotency and ownership rules.
- Use append-only price history unless a correction workflow is explicit.
- Composite indexes must match real query shape.
- Avoid unnecessary indexes; each one increases write cost.
- Validate important SQL with realistic data and `EXPLAIN`.

## Concurrency Principles

- Bound thread pools, queues, connection pools, retry loops, and external calls.
- Do not hold locks while doing network I/O.
- Prefer partitioned ownership over global locks.
- Use DB constraints/versioning for race protection.
- Consumer concurrency must match DB capacity and idempotency guarantees.

## Preferred Coding Patterns

- Constructor injection.
- Typed request/response DTOs and message payloads.
- Explicit `BigDecimal` price handling with currency/scale rules.
- Transaction annotations at service-layer business boundaries.
- Explicit cache key builders and idempotency-key builders.
- Small modules with clear reasons to change.

## Anti-Patterns

- Treating Redis as authoritative state.
- Assuming RabbitMQ delivers exactly once.
- Publishing messages before durable DB state exists.
- Holding DB transactions during crawler calls.
- Global distributed locks for scheduler convenience.
- Infinite retries without DLQ and operator visibility.
- SQL that hides indexed columns behind unnecessary expressions.
- Documentation that explains lectures instead of project decisions.

## Knowledge Routing

- Read `engineering-knowledge/project-decisions/knowledge-routing.md` before asking Codex for project-specific context.
- Read the smallest relevant rule file instead of loading raw course notes by default.
