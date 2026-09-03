# context.md — Stores-and-Stock HTTP contract

Living contract for this service, driven by `API_REFERENCE_MERCHANT_DASHBOARD.md`
(frontend spec) filtered through `AGENTS.md` scope rules. Update this file
**before** implementing a slice.

## Scope decision for this round

Implemented now: **Slices A–E** (security/Eureka, Store/"Shop" core +
ownership + ACTIVE rules, opening hours + open/closed, categories + product
CRUD, inventory + low stock + manual adjust) **plus a two-level category
taxonomy** (store categories → product subcategories) with admin CRUD, and
**real photo/logo/cover upload via a private S3 bucket** (presigned
PUT/GET, browser talks to S3 directly) — both added after the initial
slices per direct request. Photo upload **reverses** an earlier
documented decision to skip binary storage per AGENTS.md §14; the
frontend confirmed upload is required, not URL-pasting, so this
supersedes that entry. Storage went through two iterations: local disk
first, then S3 once bucket credentials were provided — local disk is
gone, no longer an option.

Explicitly **not** implemented here, per `AGENTS.md` §14 and §10:

- **Orders** (`/merchant/shops/{id}/orders/**`) — Orders domain, owned by a
  future Orders service.
- **Sales summary** (`/merchant/shops/{id}/sales/summary`) — requires the
  Orders/Payments database; would be fake data if built here.
- **Receipts** (`/merchant/shops/{id}/receipts/**`) — Payments domain.
- **Geo "near me" listing** (Slice F) — not requested yet. **Admin
  suspend/search** (Slice G) is still not implemented, but admin category
  management (below) now exists as a first admin-facing surface.

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
  `{ "code", "message", "details": string[], "timestamp" }`. `code` stays
  a machine-readable English identifier (`VALIDATION_ERROR`,
  `SHOP_NOT_FOUND`, …); `message` and `details` are **Portuguese** — this
  is a Portuguese-language product (Mozambique), so any text a user might
  see is written in Portuguese, not just proxied from Jakarta Validation's
  default English messages. Any exception not otherwise mapped is caught,
  **logged server-side with its stack trace**, and answered as
  `500 {"code":"INTERNAL_ERROR", ...}` in the same envelope — previously
  it fell through to Spring's default error page (bare, no logging, no
  consistent shape), which is what produced an unexplained
  `{"message":""}` seen once during frontend testing.
- `ROLE_MERCHANT` for merchant-facing endpoints (§1–§3 below); ownership
  additionally checked per call (`store.owner_user_id == jwt.sub`).
  `ROLE_ADMIN` bypasses the ownership check.
- `ROLE_ADMIN` for admin-facing endpoints (§4 below).

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
`neighborhood`, `phone`, `categoryIds?` (uuid[] — see §2, a store may
belong to several top-level categories), `description?`.
`owner_user_id = jwt.sub`. Status is `ACTIVE` immediately if the
activation minimums (trade name, NUIT, address, city, neighborhood) are
all present, else `DRAFT`. Unknown ids in `categoryIds` → `400
VALIDATION_ERROR`. `201` → full `Shop`.

### `GET /api/v1/merchant/shops/{shopId}` — MERCHANT (owner) | ADMIN

Full shop profile (fiscal + settings fields).

### `PATCH /api/v1/merchant/shops/{shopId}` — MERCHANT (owner)

Partial update of the same field set as create. `categoryIds`, when
present, **replaces** the full set (same semantics as opening hours) —
omit the field to leave categories unchanged, send `[]` to clear them.
Recomputes `ACTIVE` eligibility (trade name, NUIT, address, city,
neighborhood present) and flips status automatically once all are set.

### Logo / cover upload — MERCHANT (owner)

Two-step presigned flow, same shape for both — see [Uploads](#uploads)
below for the full mechanics:

- `POST /api/v1/merchant/shops/{shopId}/logo/presign` /
  `.../cover/presign` — body `{ "contentType": "image/jpeg" }` → `200`
  `{ uploadUrl, key, expiresAt }`.
- `POST /api/v1/merchant/shops/{shopId}/logo` / `.../cover` — body
  `{ "key": "..." }` (the `key` from the presign step) → `200` → updated
  `Shop`, with `logoUrl`/`coverUrl` set to a fresh presigned GET URL.

### `PATCH /api/v1/merchant/shops/{shopId}/status` — MERCHANT (owner)

Body: `{ "manuallyClosed": boolean, "reason": string? }` — manual
open/pause override, independent of posted hours. `200` → updated `Shop`.

### `GET` / `PUT /api/v1/merchant/shops/{shopId}/hours` — MERCHANT (owner)

`PUT` replaces the full week:

```json
{ "days": [ { "day": "SEGUNDA", "opensAt": "08:00", "closesAt": "18:00", "closed": false } ] }
```

`day` is Portuguese, not `java.time.DayOfWeek`'s English names: one of
`SEGUNDA, TERCA, QUARTA, QUINTA, SEXTA, SABADO, DOMINGO` (Monday-first,
no accents — matches the uppercase-code style already used for category
codes). `GET` returns the same shape. `isOpen` on the `Shop`/list
projections is computed server-side from `hours` + `manuallyClosed`,
evaluated in `Africa/Maputo`.

## 2. Category taxonomy

Two levels, per direct request (this shapes both Shops and Products, so
it's documented before them):

- **Category** — store-level, e.g. `SUPERMERCADO`, `BELEZA`. A store may
  belong to **several** (many-to-many, `store_categories` join table).
- **Subcategory** — product-level, scoped to exactly one parent Category,
  e.g. `LEGUMES_E_FRUTAS` under `SUPERMERCADO`. A product references
  **one** subcategory (nullable).

### Public reads (used by merchant pickers)

- `GET /api/v1/meta/categories` — public, active categories:
  `{ id, code, name, sortOrder, active }[]`.
- `GET /api/v1/meta/categories/{categoryId}/subcategories` — public,
  active subcategories under one category:
  `{ id, categoryId, categoryCode, categoryName, code, name, sortOrder,
  active }[]`. `404 CATEGORY_NOT_FOUND` for an unknown `categoryId`.

### Admin CRUD (new — for the admin dashboard)

`ROLE_ADMIN` only (`403 ACCESS_DENIED` otherwise — verified against a
`MERCHANT` token in tests).

`/api/v1/admin/categories`

| Method & path | Notes |
|---|---|
| `GET` | All categories (active and inactive), sorted. |
| `POST` | Body `{ code*, name*, sortOrder?, active? }`. `code` is uppercased server-side. `409 CATEGORY_CODE_ALREADY_EXISTS` on duplicate. |
| `GET /{categoryId}` | |
| `PATCH /{categoryId}` | Body `{ name?, sortOrder?, active? }` — `code` is immutable after creation (it's referenced by stores/subcategories). |
| `DELETE /{categoryId}` | `204`. `409 CATEGORY_IN_USE` if it has subcategories or is assigned to any store — deactivate (`active: false`) instead. |

`/api/v1/admin/categories/{categoryId}/subcategories`

| Method & path | Notes |
|---|---|
| `GET` | All subcategories under the category. `404 CATEGORY_NOT_FOUND` if the category doesn't exist. |
| `POST` | Body `{ code*, name*, sortOrder?, active? }`. `409 SUBCATEGORY_CODE_ALREADY_EXISTS` if the code is already used **within this category** (codes are unique per-category, not globally). |
| `GET /{subcategoryId}` | `404 SUBCATEGORY_NOT_FOUND` if it doesn't exist or belongs to a different category. |
| `PATCH /{subcategoryId}` | Body `{ name?, sortOrder?, active? }` — `code` immutable. |
| `DELETE /{subcategoryId}` | `204`. `409 SUBCATEGORY_IN_USE` if any product references it. |

Seeded data: `V2__seed_categories.sql` (8 top-level categories) and
`V4__seed_subcategories.sql` (a starter set per category, e.g. 8 under
`SUPERMERCADO`). All admin-manageable from there on — the seed is a
starting point, not a fixed list.

## 3. Products & stock

Base path `/api/v1/merchant/shops/{shopId}/products`, MERCHANT (owner) for
all writes; `GET` also owner-only in this slice (no public catalog yet).

### `GET .../products`

Query: `query`, `categoryId` (uuid — matches any subcategory under that
category), `subcategoryId` (uuid — exact match; takes precedence over
`categoryId` if both are sent), `active`, `lowStock`, `page`, `size`,
`sort`. Response: `PageResponse<Product>` (`content, page, size,
totalElements, totalPages`).

### `POST .../products`

Body: `name*`, `description*`, `subcategoryId?` (uuid, see §2), `price*`
(IVA-inclusive, ≥ 0), `stockQuantity*` (≥ 0), `lowStockThreshold?`
(default 5), `active?` (default true). Unknown `subcategoryId` → `400
VALIDATION_ERROR`. `201` → `Product`. Creates the linked inventory row.

### `GET` / `PATCH .../products/{productId}`

Same fields, partial on `PATCH`. `subcategoryId: null` in the JSON body
is indistinguishable from "field omitted" (both leave it unchanged) —
same limitation as other partial-update fields in this API; there's no
way to explicitly clear a product's subcategory via `PATCH` today.

### `PATCH .../products/{productId}/active?active=true|false`

Soft archive/restore.

### `PATCH .../products/{productId}/stock`

Body: `{ "quantity": integer }` — sets absolute `quantity_available`.
Rejects negative. Records a `MANUAL_ADJUST` stock movement.

### Photos

Two-step presigned flow — see [Uploads](#uploads) below:

- `POST .../products/{productId}/photos/presign` — body
  `{ "contentType": "image/jpeg" }` (JPEG/PNG/WEBP only — `400
  VALIDATION_ERROR` otherwise) → `200` `{ uploadUrl, key, expiresAt }`.
  Client `PUT`s the raw file bytes to `uploadUrl` directly (not through
  this service).
- `POST .../products/{productId}/photos` — body `{ "key": "..." }` (the
  `key` from the presign step). `400 VALIDATION_ERROR` if the key
  doesn't belong to this product (path prefix check) or the object
  isn't actually in the bucket yet (client didn't finish the `PUT`).
  The first photo confirmed for a product is automatically `isPrimary`.
  `201` → `{ id, url, isPrimary }` (`url` is a freshly presigned GET).
- `DELETE .../products/{productId}/photos/{photoId}` — `204`. Deletes
  the S3 object too, not just the DB row. If the deleted photo was
  primary and others remain, the next one (by upload order) is
  auto-promoted — a product is never left with photos but no primary.
- `PATCH .../products/{productId}/photos/{photoId}/primary` — explicit
  set-primary, unsets any other photo's primary flag. `200` →
  `{ id, url, isPrimary }`.

`Product.photos` (see data model) reflects current state; there's no
separate "list photos" endpoint since `GET .../products/{productId}`
already returns them.

## Uploads

**Private S3 bucket, presigned both ways** — the backend never receives
file bytes and never serves them either; it only issues short-lived
signed URLs (`common.storage.S3ObjectStorageService`,
`common.storage.AwsS3Config`):

- Bucket: `konecta-media-564956047797` (`aws.s3.bucket`), region
  `us-east-1` (`aws.region`).
- Keys: `{aws.s3.products-prefix}{productId}/{uuid}.{ext}` for product
  photos, `{aws.s3.stores-prefix}{shopId}/logo|cover/{uuid}.{ext}` for
  shop assets. The backend generates the key, not the client — a client
  never gets to choose where in the bucket its file lands (see
  `S3KeyFactory`).
- `POST .../presign` → a presigned `PUT` URL, valid
  `aws.s3.presign-put-ttl-seconds` (300s default). The client uploads
  directly to S3 with this URL — this service is not in that request
  path at all.
- Every `url` returned in a response (`Product.photos[].url`,
  `Shop.logoUrl`/`coverUrl`) is a **freshly presigned `GET`**, valid
  `aws.s3.presign-get-ttl-seconds` (3600s default) — **never persist
  these URLs client-side**, they expire; re-fetch the parent resource
  if displaying an image after that window.
- Credentials (`AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` in `.env`,
  never committed) are read via Spring's `@Value`, not the AWS SDK's own
  env-var credential chain — `.env` values live in Spring's
  `Environment`, not actual OS environment variables, so
  `EnvironmentVariableCredentialsProvider` wouldn't see them. See
  `AwsS3Config`.
- Verified live against the real bucket: presign → real `PUT` from
  outside this service → confirm (existence-checked via `HeadObject`) →
  fetch via the presigned `GET` → byte-for-byte match → delete → object
  actually gone from the bucket (`HeadObject` 404). Not just tested
  against a fake — the real AWS round trip was exercised.

## 4. Admin

See [§2 Category taxonomy](#2-category-taxonomy) — the only admin surface
implemented so far. `AGENTS.md` §10 Slice G (store suspend/search) is
still open.

## Data models

### `Shop`

`id, name, legalName, nuit, email, phone, address, city, neighborhood,
categories: Category[], description, logoUrl, coverUrl, status, isOpen,
manuallyClosed, activationReady, acceptsPickup, acceptsDelivery,
createdAt, updatedAt`

### `Category`

`id, code, name, sortOrder, active`

### `Subcategory`

`id, categoryId, categoryCode, categoryName, code, name, sortOrder,
active`

### `Product`

`id, shopId, name, description, subcategoryId, subcategoryName,
categoryId, categoryName, price, stockQuantity, lowStockThreshold,
active, lowStock, photos: { id, url, isPrimary }[], createdAt, updatedAt`

(`categoryId`/`categoryName` on `Product` are denormalized from its
subcategory's parent, purely so the frontend doesn't need a second call
to render a breadcrumb — the stored relationship is only
`subcategoryId`.)
