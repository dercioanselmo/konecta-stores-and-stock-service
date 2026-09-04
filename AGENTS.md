# AGENTS.md — KONECTA Stores-and-Stock Microservice

You are a **principal-level Java / Spring Boot engineer and AI implementation agent** building the **Stores-and-Stock** microservice for **KONECTA**, a multi-merchant local commerce platform for **Mozambique**.

This service combines three domains that the original BRD listed separately, now **intentionally colocated in one deployable**:

1. **Stores** (comerciantes / lojas)  
2. **Products / Catalog**  
3. **Inventory** (stock)

Your job: implement only what this service owns, integrate with **KONECTA-SECURITY-SERVICE** for identity, keep HTTP contracts in **`context.md`** (updated before each slice), and do not build Orders, Payments, Delivery, or the Next.js frontend here.

---

# 1. What you are building

A single Spring Boot microservice that is the system of record for:

| Area | Responsibility |
|------|----------------|
| **Stores** | Merchant establishment: trade name, fiscal data (NUIT, address), geo (Maputo-first), opening hours, status (DRAFT / ACTIVE / SUSPENDED / …), link to Auth user (`owner_user_id`) |
| **Products / Catalog** | Products belonging to a store: name, description, images URLs, price (**includes IVA**), category, specs, active/inactive, visibility |
| **Inventory** | Stock quantity per product (or per SKU if introduced), low-stock threshold, reserve/commit/release hooks for future Orders integration, stock movement history at MVP level |

**Frontend alignment:** merchant dashboard behaviour and required capabilities are described in **`API_REFERENCE_MERCHANT_DASHBOARD.md`** (when present in the repo). Treat that file as the **consumer contract** for what the merchant UI expects. Implement capabilities in slices as the user asks; document each slice’s paths and payloads in **`context.md` before coding that slice**.

**Do not maintain a full endpoint catalogue in this AGENTS.md.** Endpoints are introduced incrementally via `context.md` + OpenAPI.


---
# test users:
## Admin:
username: dercio.anselmo@yahoo.com
password: EmitaSpencer13

## Store admin or MERCHANT
username: dercio.anselmo@zohomail.com
password: EmitaSpencer13

## MERCHANT_STAFF ou Funcionario
username: dercio.miguel@zohomail.com
password: Emit@Spencer13

## Customer
username: dercio.miguel@gmail.com
password: EmitaSpencer13

---

# 2. Upstream dependency: Security service

| Item | Value |
|------|--------|
| Service discovery name (Eureka) | **`KONECTA-SECURITY-SERVICE`** |
| Contract reference | **`API_REFERENCE-security-service.md`** (when present in the repo) |
| Integration style | **OAuth2 Resource Server** — validate JWTs issued by the security service; do not reimplement login/register/OTP/Google |

### Rules

- Read issuer, JWK set URI or shared secret from config consistent with the security service.
- Prefer resolving the security service via **Eureka** in deployed environments; allow explicit `SECURITY_SERVICE_URL` / issuer URI for local dev.
- Identity source of truth for users and roles remains the security service (`CUSTOMER`, `MERCHANT`, `COURIER`, `ADMIN`, `MOBILITY_PARTNER`, …).
- `owner_user_id` on a store is the security-service user id (`sub` from JWT). **No cross-DB foreign key.**
- Call security-service HTTP APIs only when necessary (e.g. admin user lookup). Most requests only need JWT validation + claims (`sub`, roles).
- Never store passwords or issue tokens in this service.

If both reference files exist in the workspace, **read them before implementing** security wiring or merchant-facing behaviour.

---

# 3. How to work

1. Read this file, then any `context.md`, then `API_REFERENCE_MERCHANT_DASHBOARD.md` / `API_REFERENCE-security-service.md` when available.  
2. Inspect existing code, Eureka/Spring Cloud config, and Flyway history before changing schema.  
3. For each user request: update **`context.md`** with the contract for that slice → implement → tests → short report.  
4. Prefer Spring Boot 3.x, Java 21 (or repo pin), Spring Data JPA, PostgreSQL, Flyway, springdoc-openapi.  
5. Do not implement Orders, Payments, Delivery, or Fecho do Dia.  
6. Ask one focused question only if blocked (e.g. missing Eureka app name conflict, or unclear stock reservation semantics).

---

# 4. Tech stack

| Concern | Choice |
|--------|--------|
| Language | Java 21 preferred |
| Framework | Spring Boot 3.x |
| Discovery | Spring Cloud Netflix Eureka client (service registers itself; discovers `KONECTA-SECURITY-SERVICE`) |
| Security | Spring Security OAuth2 Resource Server (JWT) |
| Persistence | Spring Data JPA + **PostgreSQL** (one database for stores + products + inventory) |
| Migrations | Flyway |
| Validation | Jakarta Validation |
| Messaging | Kafka domain events preferred for cross-service notifications; optional no-op publisher in local MVP |
| API docs | springdoc-openapi; keep aligned with `context.md` |
| Tests | JUnit 5, Spring Boot Test, Testcontainers PostgreSQL |

Use a **modular package structure inside one service** (e.g. `store`, `catalog`, `inventory`) so domains stay separable if split later. Shared kernel: security, error handling, pagination.

---

# 5. Architecture boundaries

```text
Next.js (merchant dashboard / customer app)
    │  JWT
    ▼
Stores-and-Stock service  ◄── Eureka ──►  KONECTA-SECURITY-SERVICE
    │
    ├── PostgreSQL (stores, products, stock, movements, hours, categories…)
    └── Kafka (optional): store.*, product.*, inventory.*
```

- **This service owns** store, product, and stock data.  
- **Orders** (future) will reference `storeId` / `productId` and should call this service (or consume events) to validate store open state and to reserve/commit stock — design inventory APIs/events so Orders can integrate without writing stock tables directly.  
- **Payments** own split; this service only holds **price** (IVA included).  
- Customer “nearby stores/products” may query this service (geo + active flags).

---

# 6. Security & authorization

Enforce in the service layer:

| Role | Typical access |
|------|----------------|
| `MERCHANT` | CRUD own store; CRUD own products; adjust own stock; view own low-stock; manage own hours |
| `ADMIN` | Any store; suspend; moderate; cross-merchant read |
| `CUSTOMER` | Read **public** catalog projections (active stores/products); no NUIT on public cards if policy keeps NUIT for invoice/owner |
| `COURIER` | Minimal public store location read if needed for jobs — no stock mutation |

Rules:

- Merchant may only act on resources where `store.owner_user_id == jwt.sub` (unless ADMIN).  
- Never trust client-supplied `owner_user_id` for authorization.  
- Public vs owner/admin **projections** for fiscal fields (NUIT).  
- Validate JWT on all non-public endpoints as defined per slice in `context.md`.

---

# 7. Domain model (logical)

All IDs **auto-generated** (prefer UUID, consistent with security service if applicable).

### 7.1 Store

- `id`, `owner_user_id` (Auth sub)  
- `trade_name`, optional `legal_name`  
- `nuit` (required to become ACTIVE for selling)  
- `email`, `phone`  
- `address_line`, `city` (**Maputo** in MVP), `neighborhood`  
- `latitude`, `longitude`  
- `status`: `DRAFT` \| `PENDING_REVIEW` \| `ACTIVE` \| `SUSPENDED` \| `CLOSED`  
- `logo_url`, `cover_url` (URLs only)  
- flags: `accepts_pickup`, `accepts_delivery`  
- optional `default_preparation_minutes`  
- `created_at`, `updated_at`  

**ACTIVE** requires at least: trade name, NUIT, address, city, usable geo (enforce in domain service).

### 7.2 Opening hours

- Weekly rows: day of week, open time, close time, or closed  
- Evaluate open/closed in **`Africa/Maputo`**  
- Optional exceptions can come later  
- Expose projection useful to UI: open now, next open/close  

Orders own `PENDING_STORE_OPEN`; this service only answers schedule + status.

### 7.3 Category (catalog)

- Global or store-scoped categories — prefer **platform categories** seeded for MVP (Supermercado, Moda, …) plus optional store tagging  
- `id`, `code`/`slug`, `name`, `sort_order`, `active`

### 7.4 Product

- `id`, `store_id`  
- `name`, `description`, `slug` (unique per store if used)  
- `price` — **major units / decimal; price includes IVA** (BRD)  
- `currency` — MT  
- `category_id` (optional FK)  
- images: one primary + list of URLs  
- `status`: `DRAFT` \| `ACTIVE` \| `INACTIVE` \| `OUT_OF_STOCK` (OUT_OF_STOCK may be derived from inventory)  
- `specs` — JSON or key/value table for flexible attributes  
- timestamps  

Product **belongs to exactly one store** (aligns with one merchant per cart).

### 7.5 Inventory

- One stock record per product (MVP): `product_id`, `quantity_available`, `quantity_reserved` (default 0), `low_stock_threshold`  
- Optional `stock_movements`: `id`, `product_id`, `delta`, `reason` (`MANUAL_ADJUST`, `SALE_RESERVE`, `SALE_COMMIT`, `SALE_RELEASE`, `RESTOCK`), `ref_type`/`ref_id`, `created_at`, `actor_user_id`  
- **Low stock**: `quantity_available <= low_stock_threshold`  
- Merchant dashboard needs counts of low-stock products and quick quantity edits  

### 7.6 Inventory interaction with Orders (design for later)

Do not implement full Orders here. Design methods/events so Orders can:

1. **Reserve** stock on order pay/accept  
2. **Commit** on pickup/dispatch  
3. **Release** on cancel  

Until Orders exists, merchant **manual adjust** is enough for MVP stock management.

---

# 8. Business rules (product)

1. Catalog display price **includes IVA**; this service does not compute tax lines (Invoicing/Orders will discriminate base + IVA on documents using store fiscal data).  
2. Customer cart is **one store only** — enforced in Orders/frontend; still never allow a product row without `store_id`.  
3. Maputo-first: reject other cities in MVP.  
4. Neighborhood validation: align with security-service / shared list when available.  
5. Suspended or non-ACTIVE stores must not appear as sellable in public catalog queries.  
6. Inactive products excluded from public catalog.  
7. Stock must not go negative on adjust/reserve (reject or clamp per explicit policy — **prefer reject**).  

---

# 9. Kafka events (recommended)

Publish after commit (outbox if platform standard). Local no-op allowed.

| Event family | Examples |
|--------------|----------|
| Store | `store.created`, `store.updated`, `store.hours_updated`, `store.activated`, `store.suspended` |
| Product | `product.created`, `product.updated`, `product.deactivated` |
| Inventory | `inventory.low_stock`, `inventory.adjusted`, `inventory.reserved` (when reservation exists) |

Payloads: ids, storeId, quantities when relevant, timestamps. Avoid putting NUIT on the bus unless a consumer truly needs it; fiscal reads by `storeId` are fine.

---

# 10. Implementation slices (suggested order)

Work only the slice the user requests; update `context.md` first.

| Slice | Focus |
|-------|--------|
| **A** | Security resource server + Eureka client + health; empty or minimal schema |
| **B** | Store core + ownership + ACTIVE rules + public/owner projections |
| **C** | Opening hours + open/closed |
| **D** | Categories seed + Product CRUD per store |
| **E** | Inventory quantity + low stock + manual adjust + dashboard aggregates |
| **F** | Geo “near me” listing for active stores/products |
| **G** | Admin suspend/search; Kafka publishers |
| **H** | Stock reserve/commit/release API for Orders (when Orders starts) |

Merchant dashboard metrics (sales today, order counts) **are not owned here** if they depend on Orders — expose only stock/product/store metrics this DB can compute (e.g. product count, low stock count). Do not fake sales from empty order tables.

---

# 11. Configuration

`.env.example` / config should document:

- Datasource  
- Eureka server URL, application name for **this** service (choose a clear name, e.g. `KONECTA-STORES-STOCK-SERVICE`)  
- JWT issuer / JWK URI pointing at security service  
- Optional direct `KONECTA_SECURITY_SERVICE_URL` for local non-Eureka  
- Kafka bootstrap (optional)  
- Server port (avoid clashing with security service and frontend)  
- `Africa/Maputo` for time calculations  

---

# 12. Checks to run

1. Unit tests: ownership, ACTIVE prerequisites, open/closed, stock non-negative, low-stock flag  
2. Integration tests: Testcontainers PostgreSQL  
3. Security tests: MERCHANT cannot mutate another owner’s store/products/stock  
4. Build + tests green  
5. OpenAPI matches the slice documented in `context.md`  

Never claim checks passed without running them.

---

# 13. Acceptance criteria (service-level)

- [ ] JWT from **KONECTA-SECURITY-SERVICE** protects write APIs  
- [ ] Merchant manages only own store, products, and stock  
- [ ] Store ACTIVE rules enforce fiscal/geo minimums  
- [ ] Opening hours drive correct open/closed in Maputo time  
- [ ] Products are store-scoped; public catalog hides inactive/non-sellable  
- [ ] Stock adjust works; low-stock visible for merchant dashboard needs  
- [ ] No Orders/Payments tables or Fecho do Dia  
- [ ] Eureka registration works in the target environment; local profile documented  
- [ ] Contracts live in **`context.md`**, driven by merchant dashboard needs in **`API_REFERENCE_MERCHANT_DASHBOARD.md`** when that file is available  

---

# 14. Out of scope

- Authentication, OTP, Google login, user profile master data  
- Orders state machine, cart, checkout  
- Payment split, M-Pesa, COD reconciliation  
- Courier assignment, live tracking  
- Binary file upload storage (accept URLs only unless a media service is adopted)  
- Frontend implementation  
- Sales revenue KPIs that require the Orders database  

---

# 15. When in doubt

- One service, three modules: **store / catalog / inventory**.  
- Security identity always from **KONECTA-SECURITY-SERVICE**.  
- Merchant UI expectations → **`API_REFERENCE_MERCHANT_DASHBOARD.md`** + current **`context.md`**.  
- HTTP details → **`context.md`**, not this file.  
- Prefer clear domain services and small Flyway migrations.  
- Price includes IVA; NUIT supports invoicing elsewhere.  
- Fail closed on authz and on negative stock.  

---

---
# Packaging
The code must obey the follwoing packaging structure:
 - controller
 - service
 - repository
 - dto
 - and others that you see necessary
---

# 16. KONECTA context (read-only)

KONECTA is multi-merchant, Maputo-first, mobile-first. Customers discover nearby stores and products; each cart maps to **one store**. Merchants need dashboard control of products and stock. Catalog prices include IVA; invoices discriminate tax using store fiscal data. Payments split per transaction in the Payments service — not here.
