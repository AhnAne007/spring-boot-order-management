# Step 9 — DLQ and validation hardening

Product Service declares durable main queue stock-reservation-queue with x-dead-letter-exchange=stock-dlx and x-dead-letter-routing-key=stock-reservation-dlq. It also declares durable direct exchange stock-dlx and durable stock-reservation-dlq, bound using routing key stock-reservation-dlq. Order Service's main-queue declaration has identical arguments; otherwise RabbitMQ rejects conflicting declarations with PRECONDITION_FAILED.

StockListenerErrorHandler is installed on Product Service's default Rabbit listener factory. It logs delivery/conversion errors and signals AmqpRejectAndDontRequeueException. The container understands this as reject-without-requeue and continues consuming; RabbitMQ routes the rejected message through the DLX. The factory retains Spring Boot's configured settings, including default-requeue-rejected=false. No retry is added. An HTTP RestControllerAdvice cannot handle conversion failures before a listener method is invoked.

Expected business rejections (missing product, insufficient stock) still log and return, so they are acknowledged, not dead-lettered. This change handles errors that reach the container-level handler; the existing listener still catches database-processing RuntimeExceptions and marks rollback-only, so those swallowed exceptions are not automatically DLQed. No broader listener semantics were changed.

## Optimistic locking trade-off

Skipped @Version. For an empty database, adding the field is simple, but existing order rows need an explicit version-column migration/backfill for safe handling of existing data. This exceeds an annotation-only change. Order updates therefore still have no optimistic-lock check. Do not claim lost-update protection in the README.

## Verification

```powershell
mvn -B -f product-service/pom.xml clean verify
mvn -B -f order-service/pom.xml clean verify
```

Expected totals: five tests in Product Service and six in Order Service (eleven total). The three new OrderControllerValidationTest cases use standalone MockMvc and Mockito, not SpringBootTest and not a live application/broker/database. They assert 400 for missing productId, zero quantity and negative quantity, and verify the service is not called. DTO constraints and @Valid already implement this behavior; no validation rules needed changing. Two Product tests check topology and malformed JSON conversion/rejection. These are authored tests, not confirmed passing runs: Java 21/Maven/Docker are unavailable in the preparation workspace.

## Existing queue migration — required once for Step 8 installations

RabbitMQ cannot redeclare an existing queue with new DLX arguments. Do not delete PostgreSQL data or run docker compose down -v.

1. Stop new order publishing:

```powershell
docker compose stop order-service
```

2. Keep Product Service consuming. In RabbitMQ dashboard http://localhost:15672 (guest/guest), wait until stock-reservation-queue has Ready=0 AND Unacked=0. Resolve pending messages first; do not delete a nonempty queue.
3. Stop the consumer, then delete only the empty, unused main queue using server-side safeguards:

```powershell
docker compose stop product-service
$auth = @{ Authorization = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes('guest:guest')) }
Invoke-RestMethod -Method Delete `
  -Uri 'http://localhost:15672/api/queues/%2F/stock-reservation-queue?if-empty=true&if-unused=true' `
  -Headers $auth
```

If deletion is refused because the queue is in use or contains messages, resolve/drain those deliveries before continuing. Do not remove the safeguards. Skip this migration if the old queue does not exist.

4. Rebuild/start the updated applications; they redeclare the queue and DLQ topology:

```powershell
docker compose config --quiet
docker compose up --build -d
docker compose logs --tail=100 product-service order-service
```

Wait for both applications to start without PRECONDITION_FAILED.

## Prove a malformed message reaches the DLQ

REST validation prevents invalid requests from being published, so send malformed JSON directly through RabbitMQ's management API. This is intentionally a test message:

```powershell
$auth = @{ Authorization = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes('guest:guest')) }
$body = @{
  properties = @{ content_type = 'application/json'; delivery_mode = 2 }
  routing_key = 'stock-reservation-queue'
  payload = '{not-json'
  payload_encoding = 'string'
} | ConvertTo-Json -Depth 5
Invoke-RestMethod -Method Post `
  -Uri 'http://localhost:15672/api/exchanges/%2F/amq.default/publish' `
  -Headers $auth -ContentType 'application/json' -Body $body
Start-Sleep -Seconds 5
Invoke-RestMethod -Uri 'http://localhost:15672/api/queues/%2F/stock-reservation-dlq' `
  -Headers $auth | Select-Object name,messages,messages_ready
docker compose logs --tail=100 product-service
docker compose ps
```

Expected: publish reports routed=true; DLQ message count increases by one (management statistics may take a few seconds); Product Service logs a conversion failure and remains running. Then create a valid product/order using the existing Postman normal flow: stock should still reduce, proving consumption continues. DLQ messages remain there; no replay or purge is added.

## Confirm the three 400 responses against the running API

```powershell
$invalidBodies = @('{"quantity":1}', '{"productId":1,"quantity":0}', '{"productId":1,"quantity":-1}')
foreach ($body in $invalidBodies) {
  try {
    Invoke-RestMethod -Method Post -Uri 'http://localhost:8082/orders' `
      -ContentType 'application/json' -Body $body
    Write-Host 'UNEXPECTED: request was accepted'
  } catch {
    if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode }
    else { throw }
  }
}
```

Expected: 400, 400, 400. They must not create orders or publish messages. Static code review and tests target that outcome; live execution has not been verified here.
