# Step 5 — Publishing circuit breaker

**Test-suite update:** Step 6 replaces the four context-based tests described below with six plain Mockito tests (three per service). See STEP-6.md. The runtime breaker and manual verification steps remain applicable.

## What changed

- OrderMessagePublisher is a @Service. Its public publish(StockReservationMessage msg) has @CircuitBreaker(name="stockPublisher", fallbackMethod="publishFallback").
- The private void fallback takes exactly StockReservationMessage msg, Throwable t. It logs the failure type/reason and throws MessagingUnavailableException containing the orderId.
- OrderServiceImpl calls the separate injected publisher bean, so Spring AOP intercepts publishing. Database calls are outside the breaker.
- On MessagingUnavailableException, OrderServiceImpl saves status FAILED and rethrows. The repository saves use independent transactions; createOrder is intentionally not @Transactional, so the FAILED save is not rolled back when the exception propagates.
- New Order Service advice returns HTTP 503 with exactly error and orderId fields. The existing Product Service advice cannot handle Order Service requests because the services are separate applications; it remains unchanged.
- application.yml contains the exact requested breaker settings, 3000ms connection timeout, disabled template retries, breaker endpoint exposure and health indicator.
- Four focused tests exercise the real Spring AOP proxy with a mocked RabbitTemplate and H2 persistence, including the HTTP response and FAILED record. They are authored but have not run in the preparation workspace.

No retries, service containers, CI workflow or other future-step features were added. The AOP dependency already existed from Step 1.

## 1. Compile and test (PowerShell, repository root)

Use Java 21 and Maven:

```powershell
java -version
mvn -version
mvn -f product-service/pom.xml clean verify
mvn -f order-service/pom.xml clean verify
```

Expected: BUILD SUCCESS for both; Order Service runs four tests. Tests need neither PostgreSQL nor RabbitMQ. The preparation environment has Java 17 and no Maven/Docker, so compilation/test success is not claimed here.

## 2. Start infrastructure and both applications

```powershell
docker compose up -d
docker compose ps
```

Wait for both containers to become healthy. Existing Step 4 infrastructure can be reused. If the previous Java processes are running, stop them before restarting with the updated code.

Terminal A:

```powershell
$env:SERVER_PORT = '8081'
mvn -f product-service/pom.xml spring-boot:run
```

Terminal B:

```powershell
$env:SERVER_PORT = '8082'
mvn -f order-service/pom.xml spring-boot:run
```

## 3. Check a normal order (Terminal C)

```powershell
$product = Invoke-RestMethod -Method Post -Uri 'http://localhost:8081/products' `
  -ContentType 'application/json' -Body '{"name":"Circuit demo rice","price":150.00,"stock":100}'
$payload = @{productId=$product.id; quantity=1} | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri 'http://localhost:8082/orders' `
  -ContentType 'application/json' -Body $payload
Start-Sleep -Seconds 2
Invoke-RestMethod "http://localhost:8081/products/$($product.id)"
Invoke-RestMethod 'http://localhost:8082/actuator/circuitbreakers' | ConvertTo-Json -Depth 10
```

Expected: order returns 201/PENDING; stock becomes 99 after processing. Circuit is CLOSED. Pending is still not updated by the consumer; no response event is implemented.

## 4. Stop RabbitMQ and open the circuit

Keep PostgreSQL and both Java processes running:

```powershell
docker compose stop rabbitmq
1..5 | ForEach-Object {
  try {
    Invoke-RestMethod -Method Post -Uri 'http://localhost:8082/orders' `
      -ContentType 'application/json' -Body $payload
  } catch {
    if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode }
    $_.ErrorDetails.Message
  }
}
Invoke-RestMethod 'http://localhost:8082/actuator/circuitbreakers' | ConvertTo-Json -Depth 10
docker compose exec postgres psql -U app -d orderdb -c 'SELECT id, order_id, status FROM orders ORDER BY id DESC LIMIT 10;'
```

Expected: publishing failures return 503; their order rows have FAILED. From a fresh breaker, three consecutive AMQP failures meet the minimum sample size and open it. With prior success/failure history, the five-call rolling window determines the exact transition. Later OPEN-state calls still create and mark FAILED orders, but skip RabbitTemplate. The logs show AmqpException subclasses for failed broker calls and CallNotPermittedException for blocked calls.

Example response shape (the UUID is different per order):

```json
{"error":"Messaging is temporarily unavailable for order 0c1dce80-7fa9-4c17-842f-a9100fa84202. Stock reservation could not be submitted.","orderId":"0c1dce80-7fa9-4c17-842f-a9100fa84202"}
```

Inspect promptly: the breaker automatically changes OPEN to HALF_OPEN after 15 seconds even if RabbitMQ is still stopped. The next calls then probe the broker; failed probes can reopen it. The overall health endpoint may return 503 while RabbitMQ is down or the breaker is OPEN; that does not mean the HTTP process has stopped.

## 5. Recover

```powershell
docker compose start rabbitmq
docker compose ps
docker compose exec rabbitmq rabbitmq-diagnostics -q ping
```

Wait for the broker to be healthy before continuing:

```powershell
Start-Sleep -Seconds 16
1..2 | ForEach-Object {
  Invoke-RestMethod -Method Post -Uri 'http://localhost:8082/orders' `
    -ContentType 'application/json' -Body $payload
}
Invoke-RestMethod 'http://localhost:8082/actuator/circuitbreakers' | ConvertTo-Json -Depth 10
```

Expected: two 201 responses; two successful HALF_OPEN probes close the breaker. If RabbitMQ was not ready and a probe failed, it can reopen; allow the next 15-second wait and try again after verifying broker health. These are NEW orders, not retries of FAILED rows. Failed orders are not replayed automatically.

## Boundaries to understand

Only org.springframework.amqp.AmqpException and its subclasses count as recorded failures with this configuration. The Throwable fallback is broader: it also handles a blocked call's CallNotPermittedException and other thrown errors, but those other exceptions are not necessarily counted as AMQP failures.

The 3000 setting is a connection timeout in milliseconds, not a guaranteed three-second end-to-end HTTP deadline. OPEN calls bypass publishing but still use the database.

Publishing remains the requested convertAndSend call. No publisher confirms or mandatory-return checks were added. The breaker detects synchronous publishing errors; it does not prove that every sent message reached a queue or that Product Service reserved stock. A send failure can be ambiguous, and FAILED here means the publishing path failed, not proof of non-delivery. Database persistence failures are not covered by the publishing breaker and can prevent the FAILED update.

References: https://resilience4j.readme.io/docs/getting-started-3
