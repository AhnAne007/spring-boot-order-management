# Order Management — Steps 1–9

**Step 9:** DLQ and listener error handling are added. Read [STEP-9.md](STEP-9.md) before upgrading an existing queue. GitHub Actions CI is present from Step 8. See [FILE-CHECKLIST.md](FILE-CHECKLIST.md) for all files.

**Current startup: run `docker compose up --build -d` to start all four containers. See [STEP-7.md](STEP-7.md). The local Maven startup examples below describe the earlier workflow; to use them now, start only infrastructure with `docker compose up -d postgres rabbitmq` and keep the application containers stopped.**

**Step 6 replaces the context-based tests with six plain Mockito tests. See [STEP-6.md](STEP-6.md).**

**Step 5 is implemented. See [STEP-5.md](STEP-5.md) for breaker configuration, failure handling and exact outage/recovery verification commands.**

This checkpoint implements Steps 1–9. Two independent Maven projects; Java 21; Spring Boot 3.3.13; Resilience4j 2.2.0 dependency and AOP already present in Order Service. No root POM, shared module, retries, Flyway, Lombok, MapStruct, security features, or order status workflow have been added.

## Implemented requirements

- Step 2 established RabbitMQ and PostgreSQL with requested images/ports/health checks; Step 7 adds the two application containers. init-db.sql contains only `CREATE DATABASE orderdb;`.
- Step 3: Product JPA entity, repository, record DTOs, mapper, service interface/implementation, REST controller, 400/404 JSON exception handler, PostgreSQL configuration.
- Step 4: durable queue in both applications; Jackson2JsonMessageConverter in both; converter explicitly installed on Order Service's RabbitTemplate; duplicated message record; transactional stock listener; persisted PENDING orders and POST /orders returning 201.
- All endpoint DTO conversion is performed by manual mapper classes. Business rules live in service implementations. The product listener owns its transaction and delegates stock work to ProductService.

This is one-way messaging: Order Service publishes and Product Service consumes. No response queue was requested. A successfully published order remains PENDING even after stock changes; it is not a stock confirmation. A caught publishing failure instead saves FAILED.

## Verification status

Both POM XML files, YAML structure, project-local imports/packages, matching message records, requested Compose settings and ZIP integrity were checked during preparation. This environment has Java 17, no Maven and no Docker, so **Java 21 compilation, tests and live container execution were not performed here**. Run the following commands before treating the checkpoint as verified. Eleven tests are present as of Step 9 (five Product, six Order), including standalone MVC validation checks; they remain unexecuted in the preparation environment.

## Step 1 verification — build each independent project

Extract this ZIP into a fresh folder. Do not mix it with the earlier Java 17 project. Open PowerShell at the folder containing docker-compose.yml and the two service directories.

```powershell
java -version
mvn -version
mvn -f product-service/pom.xml clean verify
mvn -f order-service/pom.xml clean verify
```

Java and Maven's Java runtime must both report version 21. Expected Maven result for each project: BUILD SUCCESS. Maven downloads dependencies on the first build.

## Step 2 verification — infrastructure

Make sure Docker Desktop is running. Stop the previous project's infrastructure first if it occupies ports 5432, 5672 or 15672. This checkpoint intentionally uses different database credentials from that earlier project.

```powershell
docker compose config --quiet
docker compose up -d
docker compose ps
docker compose exec rabbitmq rabbitmq-diagnostics -q ping
docker compose exec postgres psql -U app -d productdb -c 'SELECT current_database();'
docker compose exec postgres psql -U app -d orderdb -c 'SELECT current_database();'
```

Wait until both containers report healthy. Database checks should print productdb and orderdb respectively. The PostgreSQL initialization script runs only when the data directory is first initialized. Existing data directories do not rerun it. If orderdb is absent, first confirm this using the commands above, then create it explicitly without deleting existing data:

```powershell
docker compose exec postgres psql -U app -d productdb -c 'CREATE DATABASE orderdb;'
```

Do not run that CREATE command if orderdb already exists.

This minimal Compose file adds no named database volume. The PostgreSQL image uses its data directory volume; use `docker compose stop` / `start` to preserve the current containers reliably. Do not assume the same data is reattached after removing/recreating containers.

## Step 3 verification — Product REST API

Both application.yml files intentionally default to 8080 as requested. To run both applications simultaneously on one computer, override their ports in separate terminals.

Terminal A, from the repository root:

```powershell
$env:SERVER_PORT = '8081'
mvn -f product-service/pom.xml spring-boot:run
```

Wait for ProductApplication to start. This final checkpoint includes the Step 4 listener, so RabbitMQ should be running too. If you specifically want to isolate Step 3 REST testing, start Product Service with:

```powershell
mvn -f product-service/pom.xml spring-boot:run '-Dspring-boot.run.arguments=--spring.rabbitmq.listener.simple.auto-startup=false'
```

Use the normal startup for Step 4. In another PowerShell terminal, create a product and retain its real ID:

```powershell
$product = Invoke-RestMethod -Method Post -Uri 'http://localhost:8081/products' `
  -ContentType 'application/json' `
  -Body (@{name='Rice'; price=150.00; stock=10} | ConvertTo-Json)
$product
Invoke-RestMethod 'http://localhost:8081/products'
Invoke-RestMethod "http://localhost:8081/products/$($product.id)"
```

POST returns 201 and an object containing id/name/price/stock. GET returns 200. In Postman you can use these same addresses and a raw JSON body:

```json
{"name":"Rice","price":150.00,"stock":10}
```

Missing product and invalid product checks (PowerShell displays their 404/400 error responses):

```powershell
try { Invoke-RestMethod 'http://localhost:8081/products/9223372036854775807' } catch { $_.ErrorDetails.Message }
try {
  Invoke-RestMethod -Method Post -Uri 'http://localhost:8081/products' `
    -ContentType 'application/json' -Body '{"name":"","price":0,"stock":-1}'
} catch { $_.ErrorDetails.Message }
```

Expected JSON error codes: PRODUCT_NOT_FOUND and VALIDATION_ERROR.

## Step 4 verification — order to stock through RabbitMQ

Terminal B, from the repository root:

```powershell
$env:SERVER_PORT = '8082'
mvn -f order-service/pom.xml spring-boot:run
```

Leave both application terminals running. In the same test terminal where you created `$product`:

```powershell
$order = Invoke-RestMethod -Method Post -Uri 'http://localhost:8082/orders' `
  -ContentType 'application/json' `
  -Body (@{productId=$product.id; quantity=3} | ConvertTo-Json)
$order
Start-Sleep -Seconds 2
Invoke-RestMethod "http://localhost:8081/products/$($product.id)"
```

Expected: POST /orders returns 201 with id, productId, quantity, UUID orderId and status PENDING. Product stock becomes 7 after asynchronous processing. If it is still 10, allow another moment and inspect the Product Service terminal. There should be an INFO stock reservation log.

Insufficient stock:

```powershell
Invoke-RestMethod -Method Post -Uri 'http://localhost:8082/orders' `
  -ContentType 'application/json' `
  -Body (@{productId=$product.id; quantity=99} | ConvertTo-Json)
Start-Sleep -Seconds 2
Invoke-RestMethod "http://localhost:8081/products/$($product.id)"
```

Expected: order creation returns 201/PENDING, stock remains 7, Product Service logs WARN showing available 7 and requested 99.

Unknown product:

```powershell
Invoke-RestMethod -Method Post -Uri 'http://localhost:8082/orders' `
  -ContentType 'application/json' -Body '{"productId":9223372036854775807,"quantity":1}'
```

Expected: order is saved and returns 201/PENDING; consumer logs WARN and returns without throwing. A zero quantity or missing productId request instead returns HTTP 400 from REST validation.

Watch the queue:

- Open http://localhost:15672 and sign in using guest / guest.
- Open Queues and streams, then stock-reservation-queue.
- Fast consumers can leave the queue empty almost immediately; that is normal.
- To see a message wait: stop Product Service with Ctrl+C, submit one valid order, observe Ready increase, then restart Product Service. Check stock after it processes the queued request. Restarting for this demonstration causes another deduction for that new order; use a fresh product if repeating the exact stock assertions above.

The producer uses RabbitMQ's default exchange: `convertAndSend(QUEUE, message)` routes to the queue with that name. Each service declares the same durable queue. JSON conversion is registered on both sides. At the method-level @RabbitListener, Spring infers the consumer's concrete StockReservationMessage type; the records have identical fields but different Java packages. No shared module or cross-service imports are required.

## Inspect both databases

From the repository root:

```powershell
docker compose exec postgres psql -U app -d productdb -c '\dt'
docker compose exec postgres psql -U app -d productdb -c 'SELECT * FROM products;'
docker compose exec postgres psql -U app -d orderdb -c '\dt'
docker compose exec postgres psql -U app -d orderdb -c 'SELECT * FROM orders;'
```

Hibernate creates/updates tables when each service starts (`ddl-auto: update`). Before that, the databases exist but the application tables may not.

For IntelliJ's database tool: host localhost, port 5432, username app, password app. Add productdb and orderdb as separate connections. The Order entity is explicitly mapped to `orders` because ORDER is an SQL keyword. Product data stays in productdb; Order Service does not read Product Service's tables.

## Connections and environment overrides

| Client/component | Host | Port | Username | Password |
|---|---|---|---|---|
| Product Service database | localhost, database productdb | 5432 | app | app |
| Order Service database | localhost, database orderdb | 5432 | app | app |
| Applications to RabbitMQ | localhost | 5672 | guest | guest |
| RabbitMQ management UI | localhost | 15672 | guest | guest |

These are the requested local development defaults, not hidden secrets or production security. The Compose port mappings publish on host interfaces; use this on a trusted development machine. No secret manager or authentication layer has been introduced.

Override Spring settings in the terminal that launches each service, for example:

```powershell
$env:SPRING_DATASOURCE_URL = 'jdbc:postgresql://localhost:5432/productdb'
$env:SPRING_DATASOURCE_USERNAME = 'app'
$env:SPRING_DATASOURCE_PASSWORD = 'app'
$env:SPRING_RABBITMQ_HOST = 'localhost'
$env:SPRING_RABBITMQ_PORT = '5672'
$env:SPRING_RABBITMQ_USERNAME = 'guest'
$env:SPRING_RABBITMQ_PASSWORD = 'guest'
$env:SERVER_PORT = '8081'
```

Use orderdb and 8082 in the Order Service terminal. Later containerized applications will override hosts with postgres and rabbitmq. Environment settings do not change credentials already stored by the infrastructure.

Health/metrics checks:

```powershell
Invoke-RestMethod 'http://localhost:8081/actuator/health'
Invoke-RestMethod 'http://localhost:8082/actuator/health'
Invoke-RestMethod 'http://localhost:8081/actuator/info'
Invoke-RestMethod 'http://localhost:8082/actuator/info'
Invoke-RestMethod 'http://localhost:8081/actuator/metrics'
Invoke-RestMethod 'http://localhost:8082/actuator/metrics'
```

## Deliberate limits at this checkpoint

- The publisher now has the Step 5 circuit breaker. Publishing failures handled by its fallback save FAILED and return 503. Publisher confirms are still absent; see STEP-5.md for the precise guarantees.
- The listener logs/returns for unknown products and insufficient stock. It also rejects nonpositive message quantities without changing stock.
- Runtime failures inside listener processing are logged and the transaction marked rollback-only. Conversion, transaction-start and commit errors can occur outside the method; `default-requeue-rejected: false` prevents the normal endless requeue behavior for unhandled listener failures. No retry is configured. Step 9 adds a DLQ for messages rejected by the container; failures swallowed by the listener can still be lost. A transactional method cannot guarantee that infrastructure never raises an exception.
- The requested stock logic does not implement duplicate detection or concurrent-consumer stock locking. Keep the single-consumer default for this exercise; exactly-once processing is not claimed.
- There is no stock-result message back to Order Service, no GET /orders endpoint and no reservation-status endpoint at this step. The old Postman collection was for a different project version; use the commands here.

Stop the Java processes using Ctrl+C, then preserve infrastructure containers with:

```powershell
docker compose stop
```

Resume them with `docker compose start` and start the Java processes again.
