# context.md — Stores-and-Stock HTTP contract

Living contract for this service, driven by `API_REFERENCE_MERCHANT_DASHBOARD.md`
(frontend spec) filtered through `AGENTS.md` scope rules. Update this file
**before** implementing a slice.

## Scope decision for this round

Implemented now: **Slices A–E** (security/Eureka, Store/"Shop" core +
ownership + ACTIVE rules, opening hours + open/closed, categories + product
CRUD, inventory + low stock + manual adjust).

Explicitly **not** implemented here, per `AGENTS.md` §14 and §10:

- **Orders** (`/merchant/shops/{id}/orders/**`) — Orders domain, owned by a
  future Orders service.
- **Sales summary** (`/merchant/shops/{id}/sales/summary`) — requires the
  Orders/Payments database; would be fake data if built here.
- **Receipts** (`/merchant/shops/{id}/receipts/**`) — Payments domain.
- **Product photo upload** (`POST .../photos` multipart) — binary object
  storage is out of scope per AGENTS.md §14 ("accept URLs only unless a
  media service is adopted"). Product images are instead a plain list of
  URL strings on the product payload (`imageUrls`, `primaryImageUrl`).
- **Geo "near me" listing** (Slice F) and **Admin suspend/search** (Slice
  G) — not requested yet.

`dashboard/summary` is implemented with only the fields this DB can
compute honestly: `isOpen`, `lowStockCount`, `productCount`,
`activeProductCount`. `salesTodayTotal`, `pendingOrdersCount`,
`ordersByStatus` are omitted (Orders-dependent) rather than faked.

## Auth

- `Authorization: Bearer <accessToken>` — JWT issued by
  KONECTA-SECURITY-SERVICE, validated locally (HS256 shared secret,
  `security.jwt.secret` / env `JWT_SECRET`, consistent with the security
  service's signing key).
- `roles` claim is a single string, already `ROLE_<CODE>` (e.g.
  `ROLE_MERCHANT`). Mapped straight to a Spring `GrantedAuthority`.
- No token → `401 {"code":"UNAUTHENTICATED", ...}`.
- Wrong role / not the resource owner → `403 {"code":"ACCESS_DENIED", ...}`.
- Error envelope (matches the security service's):
  `{ "code", "message", "details": string[], "timestamp" }`.
- Merchant-only endpoints require `ROLE_MERCHANT`; ownership is
  additionally checked per call (`store.owner_user_id == jwt.sub`).
  `ROLE_ADMIN` bypasses the ownership check (not yet exposed via a
  dedicated admin route in this slice — same controllers, `ADMIN` just
  passes the ownership gate).

## 1. Shops (Store)

Base path `/api/v1/merchant/shops`.

### `GET /api/v1/merchant/shops` — MERCHANT

List the caller's shops (by `owner_user_id`). Card projection:

```json
[{ "id", "name", "logoUrl", "isOpen", "lowStockCount" }]
```

(`todaySalesTotal`, `pendingOrdersCount` omitted — Orders-dependent, see
Scope decision above.)

### `POST /api/v1/merchant/shops` — MERCHANT

Body: `name*`, `nuit`, `address*`, `city*` (must be `"Maputo"`),
`neighborhood`, `phone`, `category`, `description?`.
`owner_user_id = jwt.sub`. Status is `ACTIVE` immediately if the
activation minimums (trade name, NUIT, address, city, neighborhood) are
all present, else `DRAFT`. `201` → full `Shop`.

### `GET /api/v1/merchant/shops/{shopId}` — MERCHANT (owner) | ADMIN

Full shop profile (fiscal + settings fields).

### `PATCH /api/v1/merchant/shops/{shopId}` — MERCHANT (owner)

Partial update of the same field set as create. Recomputes `ACTIVE`
eligibility (trade name, NUIT, address, city, neighborhood present) —
does **not** auto-flip status, only exposes an `activationReady: boolean`.
A merchant activates explicitly via the status endpoint below once ready.

### `PATCH /api/v1/merchant/shops/{shopId}/status` — MERCHANT (owner)

Body: `{ "manuallyClosed": boolean, "reason": string? }` — manual
open/pause override, independent of posted hours. `200` → updated `Shop`.

### `GET` / `PUT /api/v1/merchant/shops/{shopId}/hours` — MERCHANT (owner)

`PUT` replaces the full week:

```json
{ "days": [ { "day": "MONDAY", "opensAt": "08:00", "closesAt": "18:00", "closed": false } ] }
```

`GET` returns the same shape. `isOpen` on the `Shop`/list projections is
computed server-side from `hours` + `manuallyClosed`, evaluated in
`Africa/Maputo`.

## 2. Categories

### `GET /api/v1/meta/categories` — Public

Seeded platform categories: `{ "code", "name", "sortOrder" }[]`.

## 3. Products & stock

Base path `/api/v1/merchant/shops/{shopId}/products`, MERCHANT (owner) for
all writes; `GET` also owner-only in this slice (no public catalog yet).

### `GET .../products`

Query: `query`, `category`, `active`, `lowStock`, `page`, `size`, `sort`.
Response: `PageResponse<Product>` (`content, page, size, totalElements,
totalPages`).

### `POST .../products`

Body: `name*`, `description*`, `category`, `price*` (IVA-inclusive, ≥ 0),
`stockQuantity*` (≥ 0), `lowStockThreshold?` (default 5), `active?`
(default true). `201` → `Product`. Creates the linked inventory row.

### `GET` / `PATCH .../products/{productId}`

Same fields, partial on `PATCH`.

### `PATCH .../products/{productId}/active?active=true|false`

Soft archive/restore.

### `PATCH .../products/{productId}/stock`

Body: `{ "quantity": integer }` — sets absolute `quantity_available`.
Rejects negative. Records a `MANUAL_ADJUST` stock movement.

## Data models

### `Shop`

`id, name, nuit, address, city, neighborhood, phone, category,
description, logoUrl, status, isOpen, manuallyClosed, activationReady,
acceptsPickup, acceptsDelivery, createdAt, updatedAt`

### `Product`

`id, shopId, name, description, category, price, stockQuantity,
lowStockThreshold, active, lowStock, imageUrls, primaryImageUrl,
createdAt, updatedAt`
