# Order Management Microservices

## 1. Overview

This project contains two Spring Boot services. Order Service stores an order and publishes a stock-reservation message to RabbitMQ. Product Service consumes that message and reduces the product stock when enough stock is available.

```text
Order Service -> RabbitMQ (stock-reservation-queue) -> Product Service
```

## 2. Tech stack

| Area | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3.13 |
| Messaging | RabbitMQ + Spring AMQP |
| Resilience | Resilience4j Circuit Breaker 2.2.0 |
| Database | PostgreSQL 16 |
| Persistence | Spring Data JPA / Hibernate |
| Build | Maven |
| Containers | Docker + Docker Compose |
| CI | GitHub Actions |
| Tests | JUnit 5 + Mockito |

## 3. Run it

Requirements: Docker with Docker Compose.

From the repository root:

```bash
docker compose up --build
```

Wait until PostgreSQL and RabbitMQ are healthy and both Spring Boot services have started.

| Component | Address / Port | Credentials |
|---|---|---|
| Product Service | `http://localhost:8081` | - |
| Order Service | `http://localhost:8082` | - |
| RabbitMQ Management UI | `http://localhost:15672` | `guest` / `guest` |
| PostgreSQL | `localhost:5432` | `app` / `app` |

The PostgreSQL container starts with `productdb` and creates `orderdb` using `init-db.sql`.

## 4. Walkthrough

### Create a product

```bash
curl -i -X POST http://localhost:8081/products \
  -H "Content-Type: application/json" \
  -d '{"name":"Rice","price":150.00,"stock":10}'
```

Example response (`201 Created`):

```json
{
  "id": 1,
  "name": "Rice",
  "price": 150.00,
  "stock": 10
}
```

Use the returned product `id` in the next request.

### Create an order

```bash
curl -i -X POST http://localhost:8082/orders \
  -H "Content-Type: application/json" \
  -d '{"productId":1,"quantity":3}'
```

Example response (`201 Created`):

```json
{
  "id": 1,
  "productId": 1,
  "quantity": 3,
  "orderId": "9d8a55d6-f40c-4897-9c2c-4f7f61e511f3",
  "status": "PENDING"
}
```

The order response is returned after the message is published. Stock processing is asynchronous. Wait about two seconds, then fetch the product again:

```bash
curl -i http://localhost:8081/products/1
```

Expected response (`200 OK`):

```json
{
  "id": 1,
  "name": "Rice",
  "price": 150.00,
  "stock": 7
}
```

If the product does not exist or stock is insufficient, Product Service logs the problem and does not reduce stock.

## 5. How the Circuit Breaker works

`OrderMessagePublisher.publish(...)` is a Spring service method annotated with `@CircuitBreaker`. The breaker wraps the `RabbitTemplate.convertAndSend(...)` call, not the database save.

The breaker uses a count-based sliding window of 5 calls. After at least 3 recorded calls, if at least 50% are failures, the circuit opens. While it is `OPEN`, calls are rejected immediately instead of trying RabbitMQ. The fallback throws `MessagingUnavailableException`, and the REST exception handler returns HTTP `503 Service Unavailable`. After 15 seconds the breaker automatically moves to `HALF_OPEN` and allows 2 probe calls. Successful probes close it again.

Exact configuration from `order-service/src/main/resources/application.yml`:

```yaml
resilience4j:
  circuitbreaker:
    instances:
      stockPublisher:
        slidingWindowType: COUNT_BASED
        slidingWindowSize: 5
        minimumNumberOfCalls: 3
        failureRateThreshold: 50
        waitDurationInOpenState: 15s
        permittedNumberOfCallsInHalfOpenState: 2
        automaticTransitionFromOpenToHalfOpenEnabled: true
        registerHealthIndicator: true
        recordExceptions:
          - org.springframework.amqp.AmqpException
```

Publishing failures are returned in this shape:

```json
{
  "error": "Messaging is temporarily unavailable for order <uuid>. Stock reservation could not be submitted.",
  "orderId": "<uuid>"
}
```

The failed order row is also updated to `FAILED`.

## 6. How to demo the Circuit Breaker

First complete one normal request so the services are known to be working. Then stop only RabbitMQ:

```bash
docker compose stop rabbitmq
```

Send the same order request four times:

```bash
curl -i -X POST http://localhost:8082/orders -H "Content-Type: application/json" -d '{"productId":1,"quantity":1}'
curl -i -X POST http://localhost:8082/orders -H "Content-Type: application/json" -d '{"productId":1,"quantity":1}'
curl -i -X POST http://localhost:8082/orders -H "Content-Type: application/json" -d '{"productId":1,"quantity":1}'
curl -i -X POST http://localhost:8082/orders -H "Content-Type: application/json" -d '{"productId":1,"quantity":1}'
```

Expected: the requests return `503`. Once the configured failure threshold has been reached, the breaker is `OPEN`; later calls are rejected without contacting RabbitMQ.

Check the breaker:

```bash
curl http://localhost:8082/actuator/circuitbreakers
```

Look for `stockPublisher` with state `OPEN`. Because automatic transition is enabled, inspect it promptly; after 15 seconds it may already become `HALF_OPEN`.

Start RabbitMQ again:

```bash
docker compose start rabbitmq
```

Wait until RabbitMQ is healthy, then allow at least 20 seconds from the open state. Send new orders again. The two permitted `HALF_OPEN` probes should succeed and the breaker should return to `CLOSED`.

```bash
curl -i -X POST http://localhost:8082/orders -H "Content-Type: application/json" -d '{"productId":1,"quantity":1}'
curl -i -X POST http://localhost:8082/orders -H "Content-Type: application/json" -d '{"productId":1,"quantity":1}'
curl http://localhost:8082/actuator/circuitbreakers
```

Failed orders are not replayed automatically; the recovery requests are new orders.

## 7. Tests and CI

Run tests locally with Java 21 and Maven:

```bash
mvn -B -f product-service/pom.xml clean verify
mvn -B -f order-service/pom.xml clean verify
```

The tests use JUnit 5, Mockito, standalone MockMvc, and direct object construction. Repositories and `RabbitTemplate` are mocked, so the unit tests do not require RabbitMQ, PostgreSQL, or Docker.

The test suite covers:

- reducing product stock for a valid reservation;
- leaving stock unchanged for insufficient stock or an unknown product;
- malformed-message rejection/DLQ topology;
- correct RabbitMQ publish arguments;
- publisher fallback behavior;
- saving an order before publishing its message;
- HTTP 400 validation for invalid order requests.

`.github/workflows/ci.yml` runs `mvn verify` for both services and then builds each Docker image.

## 8. Design decisions and trade-offs

**Duplicated message DTO.** `StockReservationMessage` exists in both services instead of a shared Maven module. This keeps the take-home project small and the services independently buildable. In a larger system, a shared versioned contract module or schema would reduce accidental contract drift.

**One PostgreSQL container, two databases.** Docker Compose runs one PostgreSQL instance for simplicity, while Product Service uses `productdb` and Order Service uses `orderdb`. This keeps service data logically separated without adding another database container.

**With more time.** I would add a transactional outbox so the order save and event publication cannot diverge, idempotency keys to protect against duplicate order submissions/messages, a shared versioned contract module, and Testcontainers integration tests for real PostgreSQL and RabbitMQ behavior.
