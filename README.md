# konvo-backend

Spring Boot API for **Konvo CRM** — Vulkan Technologies' tenant-aware WhatsApp AI CRM.

This repo is one half of the system; see [`../konvo-frontend`](../konvo-frontend) for the Angular UI and [`../docs`](../docs) for the implementation plan and running log.

## Stack

- **Java 21**, Spring Boot 4.0.6 (Jakarta EE 11 / Spring Framework 7 baseline).
- **Gradle Kotlin DSL** with the wrapper checked in (8.14.4 — Boot 4 requires 8.14+).
- **PostgreSQL 17 + pgvector**, **Flyway** migrations.
- **RabbitMQ** for webhook ingestion, AI replies, outbound sends.
- **Spring Security** with JWT (M2+).
- **springdoc-openapi** at `/swagger-ui.html`.

## Module layout

`src/main/java/com/vulkantechtt/konvo/` follows the bounded contexts in the implementation plan:

| Package | Status | Notes |
|---|---|---|
| `common` | ✅ M1 | BaseEntity, TenantScopedEntity, ApiError, GlobalExceptionHandler, PageResponse, KonvoException, `MetaController` |
| `config` | ✅ M1 | CorsConfig, OpenApiConfig, RabbitConfig |
| `security` | partial | SecurityConfig (open in M1, locks in M2), TenantContext holder |
| `storage` | ✅ M1 | StorageService interface, LocalStorageService (R2 adapter lands later) |
| `whatsapp` | stubbed | WhatsAppProvider interface, StubWhatsAppProvider returning canned IDs |
| `ai` | stubbed | AiProvider interface, StubAiProvider with canned replies |
| `tenants`, `users`, `auth` | placeholder | filled in M2 |
| `conversations`, `customers` | placeholder | filled in M4 |
| `knowledge`, `templates` | placeholder | filled in M5/M6 |
| `usage`, `billing`, `audit`, `notifications` | placeholder | filled in M6+ |

## Run locally

The recommended workflow is via the shared docker-compose stack at the workspace root — it brings up Postgres + RabbitMQ + Redis alongside the API.

```bash
# from workspace root
docker compose -f infra/docker-compose.yml up -d postgres rabbitmq redis
# then
cd konvo-backend
./gradlew bootRun
```

Defaults: API on `http://localhost:8080`, OpenAPI at `/swagger-ui.html`, health at `/actuator/health`.

## Build

```bash
./gradlew bootJar                 # produces build/libs/konvo-backend.jar
./gradlew test                    # runs the smoke test (no DB required)
docker build -t konvo-backend .   # multi-stage image
```

## Configuration

All knobs live in `application.yml` and are overridable via env vars. Stubs vs real providers are chosen by:

- `konvo.whatsapp.provider=stub|meta` (M1 default: `stub`)
- `konvo.ai.default-provider=stub|groq|openai` (M1 default: `stub`)
- `konvo.storage.provider=local|r2` (M1 default: `local`)

Switching to `meta` / `groq` / `r2` requires the corresponding env vars (see `application.yml`).
