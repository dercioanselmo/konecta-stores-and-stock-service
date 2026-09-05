# Stores-and-Stock Service

Part of **KONECTA**, a multi-merchant local commerce platform for Mozambique.

This microservice is the system of record for three colocated domains:

- **Stores** — merchant shops: profile, fiscal data, geolocation, opening hours, status, ownership.
- **Catalog** — products and the two-level category/subcategory taxonomy.
- **Inventory** — stock quantity per product, low-stock threshold, stock movement history.

It also serves the **public, unauthenticated customer-browsing surface** (proximity shop search,
shop pages, product pages) that the customer-facing app reads from directly.

## What this service does *not* own

Orders, Payments/Cart, Delivery, and staff-account management (creating/editing `MERCHANT_STAFF`
users) are **not** here — those belong to the Cart service and KONECTA-SECURITY-SERVICE
respectively. This service only reads a `shopId` claim off staff JWTs; it has no staff table.

## Tech stack

- Java 21, Spring Boot 4.1.1
- PostgreSQL + Flyway migrations
- Spring Security (OAuth2 resource server) — validates JWTs issued by KONECTA-SECURITY-SERVICE
  (HS256, shared secret), no login of its own
- Spring Cloud Netflix Eureka client — service discovery
- AWS S3 (SDK v2) — private bucket, all photo/logo/cover access via presigned URLs
- springdoc-openapi (Swagger UI)
- Testcontainers (Postgres) for integration tests

## Project layout

```
src/main/java/com/konecta/stores_stock_service/
├── store/          shops: profile, hours, geolocation, admin & public shop endpoints
├── catalog/         products, categories, subcategories
├── inventory/       stock quantity, low-stock, stock movement history
├── user/            user profile photo (S3 plumbing only, no persistence)
├── security/        JWT role parsing, security config
└── common/          error envelope, pagination, S3 upload plumbing
```

Each domain package follows the same sub-layout: `controller/ service/ repository/ model/ dto/`.

## Getting started

### Prerequisites

- Java 21, Maven (or use the bundled `./mvnw`)
- A local PostgreSQL instance
- Docker (only needed to run the test suite — Testcontainers spins up its own Postgres)
- A running KONECTA-SECURITY-SERVICE instance sharing the same JWT signing secret

### Configure

```
cp .env.example .env
```

Fill in real values — database credentials, `JWT_SECRET` (must match the security service),
and AWS credentials for the S3 bucket if you need photo/logo uploads to work locally.
`.env` is never committed; `.env.example` documents every variable with safe defaults where one exists.

### Run

```
./scripts/run-local.sh
```

This sources `.env` into the shell and runs `./mvnw spring-boot:run`. (Spring Boot doesn't
read `.env` files itself — `application.properties`'s
`spring.config.import=optional:file:.env[.properties]` is what actually wires the values in;
the script just also exports them as real env vars for anything outside Spring's `Environment`.)

The app starts on `http://localhost:8092` (configurable via `SERVER_PORT`).

### Test

```
./mvnw clean test
```

Integration tests spin up a real Postgres via Testcontainers and drive the full HTTP stack
through `MockMvc` with synthetic JWTs — no mocking of the database or security layer.

## Key concepts

- **Roles**: `MERCHANT` (shop owner), `MERCHANT_STAFF` (scoped to one shop via a `shopId` JWT
  claim — full read/write on that shop's products, but not shop *settings*), `ADMIN` (same
  capabilities as the owning `MERCHANT`, on any shop), or no role at all for the public
  customer-browsing endpoints.
- **Uploads**: every photo/logo/cover is a two-step presigned S3 flow — `POST .../presign` gets
  `{ uploadUrl, key, expiresAt }`, the client `PUT`s the file straight to S3, then
  `POST` the sibling endpoint with `{ key }` to confirm. This service never touches file bytes.
- **Category taxonomy**: `Category` (top-level, store-facing — a shop can belong to several) →
  `Subcategory` (product-facing — a product belongs to exactly one). Both admin-managed.
- **Error envelope**: every error is `{ code, message, details[], timestamp }` — `code` is a
  machine-readable English identifier, `message`/`details` are Portuguese (this is a
  Portuguese-language product).
- **Non-existence privacy**: a resource that exists but isn't yours (wrong owner, wrong shop, or
  not `ACTIVE`) returns `404`, not `403` — consistent across the whole API so a caller can't tell
  the difference between "doesn't exist" and "exists but isn't yours."

## API documentation

- **Swagger UI**: `http://localhost:8092/swagger-ui.html` (public, no token needed to view —
  reflects Java method signatures only, no usage notes)
- **`API_INVENTORY.md`** — terse endpoint-by-endpoint reference (paths, roles, request/response
  fields), meant for other services/teams to consume this API without wading through prose
- **`API_REFERENCE_MERCHANT_DASHBOARD.md`** — the frontend-facing contract doc, with usage notes,
  breaking-change call-outs, and what's verified live vs. only test-covered
- **`context.md`** — the internal engineering log: implementation decisions, bugs found and
  fixed, and *why* things are shaped the way they are
- **`AGENTS.md`** — scope document for AI-assisted development on this service (what's in/out of
  scope, conventions to follow)
