# Unified Document Viewer

A **Spring Boot 3** backend service that exposes a single REST endpoint for querying all
vehicle-related documents by VIN, aggregated in parallel from two mocked dealership systems:
the **Sales System** and the **Service System**.

> This repository is a solution for the *Keyloop — Unified Document Viewer* scenario.  
> See [system-design.md](./system-design.md) for the full architecture document.

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Quick Start (Docker)](#quick-start-docker)
3. [Build & Run (Local JVM)](#build--run-local-jvm)
4. [Test](#test)
5. [API Reference](#api-reference)
6. [Project Structure](#project-structure)
7. [Reasoning & Design Narrative](#reasoning--design-narrative)

---

## Prerequisites

| Tool | Minimum version | Notes |
|---|---|---|
| Docker + Docker Compose | 24+ | Required for the one-command demo |
| Java (JDK) | 21 | Required only for local JVM run / test |
| Gradle | 8.x (wrapper included) | `./gradlew` — no separate install needed |

---

## Quick Start (Docker)

The fastest way to run a full demo:

```bash
# 1. Clone / enter the repo
cd scenario-d

# 2. Build the backend JAR first (Docker uses a multi-stage build)
cd backend && ./gradlew bootJar --no-daemon && cd ..

# 3. Start PostgreSQL + WireMock + the app
docker-compose up --build
```

The stack is ready when you see:

```
docviewer-app  | Started DocViewerApplication in X.XXX seconds
```

**Try it:**

```bash
# Fetch aggregated documents for a VIN
curl -s "http://localhost:8080/api/v1/documents?vin=1HGCM82633A123456" | jq .

# Health check
curl -s "http://localhost:8080/actuator/health" | jq .

# Open Swagger UI in your browser
open http://localhost:8080/swagger-ui/index.html
```

**Stop:**

```bash
docker-compose down -v   # -v also removes the postgres volume
```

---

## Build & Run (Local JVM)

Use this when you want faster iteration without rebuilding Docker images.

### 1. Start infrastructure only

```bash
docker-compose up postgres wiremock -d
```

### 2. Run the application

```bash
cd backend
./gradlew bootRun
```

The app connects to `localhost:5432` (PostgreSQL) and `localhost:8089` (WireMock) by default.

### Environment variables

All settings have sensible defaults for local development. Override via env vars:

| Variable | Default | Description |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/docviewer` | PostgreSQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | `docviewer` | DB username |
| `SPRING_DATASOURCE_PASSWORD` | `docviewer` | DB password |
| `SALES_API_BASE_URL` | `http://localhost:8089/sales` | Sales System base URL |
| `SERVICE_API_BASE_URL` | `http://localhost:8089/service` | Service System base URL |

---

## Test

```bash
cd backend

# Run all tests (unit + integration)
./gradlew test

# View HTML test report
open build/reports/tests/test/index.html
```

Tests use:
- **H2 in-memory database** (PostgreSQL-mode) — no external DB required
- **WireMock** (`WireMockServer` with `@DynamicPropertySource`) — no running WireMock container required

### Test coverage

| Test class | Type | What is validated |
|---|---|---|
| `DocumentAggregatorServiceTest` | Unit (Mockito) | Parallel aggregation logic, partial failure, sorting, DB persistence call |
| `DocumentControllerIntegrationTest` | Integration (full Spring context + WireMock + H2) | HTTP 200/400, VIN validation, partial results, Swagger UI availability |

---

## API Reference

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/documents?vin={vin}` | Fetch all documents for a vehicle |
| `GET` | `/actuator/health` | Health check |
| `GET` | `/swagger-ui/index.html` | Interactive API documentation |
| `GET` | `/v3/api-docs` | OpenAPI 3 JSON spec |

See [openapi.yaml](./openapi.yaml) for the full contract.

### Example request / response

```bash
curl "http://localhost:8080/api/v1/documents?vin=1HGCM82633A123456"
```

```json
{
  "vin": "1HGCM82633A123456",
  "totalCount": 5,
  "documents": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "title": "Annual Safety Inspection",
      "type": "INSPECTION",
      "source": "SERVICE",
      "createdAt": "2024-10-01T11:00:00Z",
      "url": "https://service.example.com/docs/svc-003.pdf"
    },
    {
      "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "title": "Purchase Agreement",
      "type": "CONTRACT",
      "source": "SALES",
      "createdAt": "2024-01-15T10:30:00Z",
      "url": "https://sales.example.com/docs/sale-001.pdf"
    }
  ],
  "sources": {
    "SALES":   { "status": "OK", "count": 2 },
    "SERVICE": { "status": "OK", "count": 3 }
  }
}
```

### Validation errors

```bash
curl "http://localhost:8080/api/v1/documents?vin=INVALID"
# HTTP 400
{
  "title": "Validation Error",
  "status": 400,
  "detail": "getDocumentsByVin.vin: VIN must be exactly 17 characters (alphanumeric, excluding I, O, Q)"
}
```

---

## Project Structure

```
scenario-d/
├── system-design.md          ← Architecture document (5-step framework)
├── openapi.yaml              ← OpenAPI 3 spec (frontend contract)
├── docker-compose.yml        ← One-command local demo
├── wiremock/
│   └── mappings/
│       ├── sales-documents.json    ← WireMock stub: Sales System API
│       └── service-documents.json  ← WireMock stub: Service System API
└── backend/
    ├── Dockerfile
    ├── build.gradle.kts
    ├── settings.gradle.kts
    └── src/
        ├── main/java/com/keyloop/docviewer/
        │   ├── DocViewerApplication.java
        │   ├── controller/
        │   │   ├── DocumentController.java      ← GET /api/v1/documents
        │   │   └── GlobalExceptionHandler.java  ← RFC 7807 error mapping
        │   ├── service/
        │   │   ├── DocumentAggregatorService.java  ← Core orchestration
        │   │   ├── SalesSystemClient.java           ← WebClient for Sales API
        │   │   └── ServiceSystemClient.java         ← WebClient for Service API
        │   ├── repository/
        │   │   └── DocumentRepository.java
        │   ├── model/
        │   │   ├── Document.java               ← JPA entity
        │   │   ├── DocumentSource.java          ← SALES | SERVICE enum
        │   │   └── dto/
        │   │       ├── SalesApiResponse.java
        │   │       ├── ServiceApiResponse.java
        │   │       ├── DocumentDto.java
        │   │       └── DocumentsResponse.java
        │   └── config/
        │       ├── WebClientConfig.java
        │       └── OpenApiConfig.java
        └── test/java/com/keyloop/docviewer/
            ├── service/DocumentAggregatorServiceTest.java
            └── controller/DocumentControllerIntegrationTest.java
```

---

## Reasoning & Design Narrative

### High-Level Strategy

The problem is a **data aggregation API**: accept a VIN, fan-out to two heterogeneous systems, merge their responses, handle partial failures gracefully, and return a uniform representation to consumers. The core complexity is in:

1. **Parallel execution** — sequential calls would double latency unnecessarily.
2. **Failure isolation** — if one system is down, the service must still return what it can.
3. **Schema normalisation** — two APIs with different field names / structures mapped to one canonical model.

I selected these as the three quality gates for the implementation and designed tests specifically to validate each.

### Key Design Decisions

**`Mono.zip()` over `CompletableFuture`**  
Spring WebClient is reactive-native. Using `Mono.zip()` to compose two concurrent calls is idiomatic, composable, and avoids thread pool overhead. The blocking `.block()` at the service boundary keeps the MVC controller layer synchronous (no reactive plumbing in the controller), which keeps the code simple and testable.

**`FetchResult<T>` wrapper**  
`Mono.zip()` cancels if either mono errors. Wrapping each call's error into a `FetchResult` using `.onErrorResume()` ensures `zip` always completes, enabling partial-result responses without complex error-merging logic.

**Deterministic UUID as PK**  
`UUID.nameUUIDFromBytes("SALES-sale-001")` produces the same UUID every time for the same external document ID. This makes `saveAll()` an upsert by PK — safe to re-fetch the same VIN repeatedly without creating duplicate rows.

**ECS Fargate over Lambda**  
Lambda cold starts introduce unpredictable latency for a demo scenario. Fargate runs the same Docker image used locally, making the demo-to-production path a straight lift-and-shift (swap docker-compose for ECS task definitions, add RDS and ALB).

**WireMock for both tests and local demo**  
Using WireMock in two contexts (embedded in integration tests via `WireMockServer`, standalone in docker-compose) ensures the same mock behaviour is validated by tests and observable in the live demo. No divergence between test mocks and runtime mocks.

### Process for Verifying and Refining Output

1. **Plan review** — Produced a plan artifact and had it approved before writing a single line of code. This aligned the architecture approach upfront.
2. **Outside-in design** — Defined the API contract (`openapi.yaml`) and the external API contracts (WireMock stubs) before writing service logic. The contracts drove the DTO shapes.
3. **Layered implementation** — Built from the inside out: models → repository → clients → aggregator → controller. Each layer is independently testable.
4. **Test-first validation** — `DocumentAggregatorServiceTest` covers the six key scenarios (happy path, both-fail, each-fail, empty, sort order, DB persistence) before integration tests were written.
5. **Observability baked in** — Structured logging with `vin`, doc counts, and durations added at the time of implementation, not as an afterthought.

### Quality Assurance

- Bean validation (`@Pattern`, `@NotBlank`) enforced at the controller boundary with RFC 7807 error responses.
- `@JsonIgnoreProperties(ignoreUnknown = true)` on DTOs makes the service resilient to additive changes in upstream APIs.
- Null-safe timestamp parsing — a malformed date string logs a warning and sets `createdAt` to null rather than throwing.
- Integration tests use `@DynamicPropertySource` so WireMock runs on a random port — no port conflicts in CI.

