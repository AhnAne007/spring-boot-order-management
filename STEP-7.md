# Step 7 — Containerize both services

Both services now have byte-identical multi-stage Dockerfiles. Each builds independently with Maven/Java 21 and runs its executable JAR in a Java 21 JRE image. Dependencies are downloaded before copying src, so source-only changes can reuse the dependency layer. Each service .dockerignore contains target/ and .git.

Compose now starts exactly four services: postgres, rabbitmq, product-service and order-service. Both applications wait for both infrastructure health checks. Product maps host 8081 to container 8080; Order maps host 8082 to container 8080. PostgreSQL and RabbitMQ are addressed by Docker service names. All application.yml files are unchanged. No CI files or future-step features were added.

## Build and run (PowerShell or Bash, repository root)

Stop any Java processes previously started on your computer at ports 8081/8082 using Ctrl+C. Leave existing infrastructure running if it belongs to this same Compose project. If using a fresh extraction at a new path, stop the old Compose project first to avoid port conflicts.

```sh
docker compose config --quiet
docker compose build
docker compose up -d
docker compose ps
docker compose logs --tail=100 product-service order-service
```

Or build and start in one command:

```sh
docker compose up --build -d
```

Wait for both applications to log Started ...Application. Infrastructure should report healthy. Application containers have no added healthcheck, so their Up state alone does not prove readiness. Maven and Java need not be installed on your host to build these Docker images. Docker must have internet access to obtain images and Maven dependencies.

The required -DskipTests skips test execution, not test compilation. Run Step 6's Maven test commands separately when you have Java 21/Maven; the Docker build is not a passing test run.

## Verify APIs (PowerShell)

```powershell
Invoke-RestMethod 'http://localhost:8081/actuator/health'
Invoke-RestMethod 'http://localhost:8082/actuator/health'
$product = Invoke-RestMethod -Method Post -Uri 'http://localhost:8081/products' `
  -ContentType 'application/json' -Body '{"name":"Docker rice","price":150.00,"stock":10}'
$payload = @{productId=$product.id; quantity=3} | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri 'http://localhost:8082/orders' `
  -ContentType 'application/json' -Body $payload
Start-Sleep -Seconds 2
Invoke-RestMethod "http://localhost:8081/products/$($product.id)"
```

Expected: healthy services, 201 product creation, 201 order creation, then stock 7 after the asynchronous consumer runs. If processing takes longer, repeat the GET and inspect logs.

Check data:

```sh
docker compose exec postgres psql -U app -d productdb -c 'SELECT * FROM products;'
docker compose exec postgres psql -U app -d orderdb -c 'SELECT * FROM orders;'
```

RabbitMQ dashboard: http://localhost:15672, guest/guest. Database credentials remain app/app. The existing rabbitmq:3-management image supplies its development guest-account configuration; application.yml still defaults to guest/guest as requested.

## Circuit breaker demonstration

Step 5's outage/recovery requests still apply, but do not start local Maven application processes. Use docker compose logs to inspect application logs instead. Stop only RabbitMQ:

```sh
docker compose stop rabbitmq
```

Submit the Step 5 failure requests and inspect http://localhost:8082/actuator/circuitbreakers. To recover:

```sh
docker compose start rabbitmq
docker compose exec rabbitmq rabbitmq-diagnostics -q ping
```

After broker readiness and the 15-second breaker wait, submit the two recovery probes. depends_on controls startup ordering; it does not shut down the applications when RabbitMQ later stops.

## Stop / resume

```sh
docker compose stop
docker compose start
```

The earlier PostgreSQL initialization rule remains: init-db.sql runs only for a new database data directory. If orderdb is missing from an existing directory, use the non-destructive check/create instructions in README.md.

## Verification status

Static checks confirm identical Dockerfiles, exact build instructions, four Compose services, requested port mappings, dependency conditions, container hostnames, unchanged application.yml bytes and valid ZIP contents. Docker is unavailable in the preparation workspace, so image builds and live Compose startup are not claimed as verified.

Docker references:
- https://docs.docker.com/build/building/multi-stage/
- https://docs.docker.com/compose/how-tos/startup-order/
