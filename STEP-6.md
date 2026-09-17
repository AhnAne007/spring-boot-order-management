# Step 6 — Plain JUnit 5 / Mockito tests

Exactly six tests now run without a Spring application context, RabbitMQ, PostgreSQL or Docker. Repository and RabbitTemplate dependencies are Mockito mocks. These tests do not use H2 either; its test dependency remains as requested in Step 1.

## Changes

- No auto-generated contextLoads tests existed in the clean project. Verified none remain.
- Removed the Step 5 PublishingCircuitBreakerTest because it started a Spring context.
- Product: StockReservationListenerTest covers sufficient stock, insufficient stock and missing product. It invokes the real listener and real service with a mocked repository. @InjectMocks constructs the service; the listener is wired explicitly in @BeforeEach. Business logic remains in the service.
- Product stock persistence now calls repository.save(product), matching the requested verification and original Step 4 save contract. It previously used saveAndFlush. Runtime transaction ownership stays on the listener; SQL flush/commit may occur after the listener body returns.
- Order: OrderMessagePublisherTest checks one exact publish call and directly invokes the private fallback via Java reflection. Reflection wraps the thrown exception, so the test asserts its underlying cause is MessagingUnavailableException and checks orderId/message/original cause. The private fallback signature remains unchanged.
- Order: OrderServiceTest uses mocked repository/publisher and a real mapper spy. It checks saved content, UUID, response and that save happens before publish.
- All three test classes use @ExtendWith(MockitoExtension.class), @Mock and @InjectMocks. Real mapper instances use @Spy to support injection without stubbing mapping behavior.
- No SpringBootTest or contextLoads remains. No live-service test is included. No CI workflow or future-step files were added.

## Verify (PowerShell or Bash; repository root; Java 21 and Maven installed)

```sh
java -version
mvn -version
mvn -B -ntp -f product-service/pom.xml clean test
mvn -B -ntp -f order-service/pom.xml clean test
```

Expected totals: Product Service 3 tests; Order Service 3 tests; zero failures/errors. Dependencies must be downloadable or cached; no broker/database is required.

Build executable artifacts with the same tests:

```sh
mvn -B -ntp -f product-service/pom.xml clean verify
mvn -B -ntp -f order-service/pom.xml clean verify
```

Run individual test classes:

```sh
mvn -B -ntp -f product-service/pom.xml -Dtest=StockReservationListenerTest test
mvn -B -ntp -f order-service/pom.xml -Dtest=OrderMessagePublisherTest test
mvn -B -ntp -f order-service/pom.xml -Dtest=OrderServiceTest test
```

A future GitHub Actions job needs only source checkout, Java 21, Maven and these commands; do not add RabbitMQ/PostgreSQL service containers for these tests.

## What these tests do not prove

Plain Mockito construction does not apply Spring AOP or @Transactional. These six tests verify business behavior and the fallback itself, not real circuit transitions, transaction commits, HTTP advice routing or RabbitMQ JSON conversion. Keep using the manual Step 5 demonstration for runtime integration behavior.

Static source/configuration checks were performed during preparation. The workspace has Java 17 and no Maven; the tests have not been compiled/executed here. Expected counts above are verification targets, not a claim of passing results.
