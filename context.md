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

**Swagger/OpenAPI is live** (springdoc, already on the classpath):
`/swagger-ui.html` and `/v3/api-docs`, both `permitAll` in
`SecurityConfig`. Reflects Java method signatures only — no presign-flow
or Portuguese-error-text context, so this file stays the source of truth
for *how* to call things, springdoc for *whether an endpoint exists*.

**Fixed bug**: `PATCH .../shops/{id}` with `categoryIds` and `PUT
.../hours` both do delete-then-insert (`replaceCategories`,
`replaceHours`) inside one `@Transactional` method. Hibernate flushes
all pending inserts before any pending deletes **regardless of Java call
order** — so re-adding a row with the same unique key the store already
had (the *normal* case: re-saving the same hours, or a `PATCH` that
doesn't actually change categories) inserted before the old row was
deleted, violating the unique constraint and surfacing as an unhandled
`500`. Fixed with an explicit `repository.flush()` between the delete
and the insert in both places. Caught via the new catch-all handler's
logging (see above) during live frontend testing — regression-tested in
`MerchantFlowIntegrationTest#reSubmittingUnchangedCategoriesAndHours_doesNotViolateUniqueConstraint`
since this is real Hibernate flush-ordering behavior a mock can't
reproduce, only a real Postgres via Testcontainers catches it.

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
- `ROLE_MERCHANT` for every merchant-facing endpoint (§1–§3 below);
  ownership additionally checked per call (`store.owner_user_id == jwt.sub`).
  `ROLE_ADMIN` bypasses the ownership check entirely on every
  `/merchant/shops/{shopId}/**` endpoint (profile, hours, status,
  logo/cover, products) — same capabilities as the owning `MERCHANT`, on
  any shop. Two exceptions stay `MERCHANT`-only even for Admin: creating
  a brand-new shop (`POST /merchant/shops`) and the "list my shops"
  endpoint (`GET /merchant/shops`, which would just be empty for an
  Admin anyway — see `GET /api/v1/admin/shops` in §4 for the
  Admin-scoped listing).
- `ROLE_MERCHANT_STAFF` — reads everywhere a `MERCHANT` can, **plus full
  write access under `/merchant/shops/{shopId}/products/**`** (create,
  edit, stock adjust, active toggle, photos) for their one assigned
  shop — same permissions as `MERCHANT` there. Staff tokens carry a
  `shopId` claim (issued by KONECTA-SECURITY-SERVICE); the backend reads
  that claim directly (no callback to the Security service) and uses it
  as the authorization check. Attempting any other `{shopId}` returns
  `404 SHOP_NOT_FOUND` (same as a non-owner MERCHANT — avoids confirming
  the shop's existence). Staff **cannot** touch shop-level settings —
  `PATCH /merchant/shops/{shopId}`, logo/cover, `PATCH .../status`,
  `PUT .../hours` all stay `ROLE_MERCHANT`-only, `403 ACCESS_DENIED`
  otherwise.
  **Fixed bug** (caught via live verification with a real staff JWT, not
  the test suite — the unit/integration tests had encoded the wrong
  expectation and were passing against broken behavior): `ProductController`
  had `@PreAuthorize("hasRole('MERCHANT')")` on every write method,
  overriding the class-level `hasAnyRole('MERCHANT','MERCHANT_STAFF')`
  and silently blocking staff from all product writes — the opposite of
  the intended design. Removed the per-method overrides; product writes
  now correctly inherit the class-level rule. Regression-tested in
  `MerchantFlowIntegrationTest#merchantStaff_canWriteProductsButNotShopSettings`.
- `ROLE_ADMIN` for admin-facing endpoints (§4 below).

## 1. Shops (Store)

Base path `/api/v1/merchant/shops`.

### `GET /api/v1/merchant/shops` — MERCHANT | MERCHANT_STAFF

For `MERCHANT`: lists all shops owned by the caller (by `owner_user_id`).
For `MERCHANT_STAFF`: returns a single-item list containing only the shop
matching the `shopId` claim in their JWT.

Card projection:

```json
[{ "id", "name", "logoUrl", "isOpen", "lowStockCount" }]
```

(`todaySalesTotal`, `pendingOrdersCount` omitted — Orders-dependent, see
Scope decision above.)

### `POST /api/v1/merchant/shops` — MERCHANT only

Body: `name*`, `nuit`, `address*`, `city*` (must be `"Maputo"`),
`neighborhood`, `phone`, `categoryIds?` (uuid[] — see §2, a store may
belong to several top-level categories), `description?`.
`owner_user_id = jwt.sub`. Status is `ACTIVE` immediately if the
activation minimums (trade name, NUIT, address, city, neighborhood) are
all present, else `DRAFT`. Unknown ids in `categoryIds` → `400
VALIDATION_ERROR`. `201` → full `Shop`.

### `GET /api/v1/merchant/shops/{shopId}` — MERCHANT (owner) | MERCHANT_STAFF (assigned) | ADMIN

Full shop profile (fiscal + settings fields).

### `PATCH /api/v1/merchant/shops/{shopId}` — MERCHANT (owner) | ADMIN (any shop)

Partial update of the same field set as create. `categoryIds`, when
present, **replaces** the full set (same semantics as opening hours) —
omit the field to leave categories unchanged, send `[]` to clear them.
Recomputes `ACTIVE` eligibility (trade name, NUIT, address, city,
neighborhood present) and flips status automatically once all are set.

### Logo / cover upload — MERCHANT (owner) | ADMIN (any shop)

Two-step presigned flow, same shape for both — see [Uploads](#uploads)
below for the full mechanics:

- `POST /api/v1/merchant/shops/{shopId}/logo/presign` /
  `.../cover/presign` — body `{ "contentType": "image/jpeg" }` → `200`
  `{ uploadUrl, key, expiresAt }`.
- `POST /api/v1/merchant/shops/{shopId}/logo` / `.../cover` — body
  `{ "key": "..." }` (the `key` from the presign step) → `200` → updated
  `Shop`, with `logoUrl`/`coverUrl` set to a fresh presigned GET URL.

### `PATCH /api/v1/merchant/shops/{shopId}/location` — MERCHANT (owner) | ADMIN (any shop)

Body: `{ "latitude": number, "longitude": number }`, both required
together. `200` → updated `Shop`. `400 VALIDATION_ERROR` if either field
is missing, or the point falls outside a loose Maputo-area bounding box
(`lat ∈ [-26.3, -25.7]`, `lon ∈ [32.3, 32.8]` — covers the municipality
plus Matola/KaTembe with margin; a sanity check against a wildly wrong
pin, not a precise city-limits check, same spirit as `neighborhood` not
being validated against a fixed list either). `Store.latitude`/
`longitude` columns already existed in `V1__init.sql` unused since the
original schema design — no new migration needed, just exposed them.

Dedicated endpoint (not folded into the profile `PATCH`) per frontend
request, matching the `.../hours` pattern of one `PUT`/`PATCH` per
settings tab. `latitude`/`longitude` are `null` on `Shop` until this is
called; **does not gate `activationReady`** — explicit product decision
(proximity search isn't live in this phase) — flagged as something to
revisit if/when proximity search ships, since that would be a breaking
change to the `activationReady` contract for shops that never set a
location. Regression-tested in
`MerchantFlowIntegrationTest#shopLocation_persistsAndRejectsOutsideMaputo`
(bounding-box rejection, persistence round-trip, `MERCHANT_STAFF` `403`,
`ADMIN` bypass) — not yet re-verified against a live security-service
JWT.

### `PATCH /api/v1/merchant/shops/{shopId}/status` — MERCHANT (owner) | ADMIN (any shop)

Body: `{ "manuallyClosed": boolean, "reason": string? }` — manual
open/pause override, independent of posted hours. `200` → updated `Shop`.

### `GET /api/v1/merchant/shops/{shopId}/hours` — MERCHANT (owner) | MERCHANT_STAFF (assigned) | ADMIN

Returns the weekly schedule. Same shape as the `PUT` body below.

### `PUT /api/v1/merchant/shops/{shopId}/hours` — MERCHANT (owner) | ADMIN (any shop)

Replaces the full week:

```json
{ "days": [ { "day": "SEGUNDA", "opensAt": "08:00", "closesAt": "18:00", "closed": false } ] }
```

`day` is Portuguese, not `java.time.DayOfWeek`'s English names: one of
`SEGUNDA, TERCA, QUARTA, QUINTA, SEXTA, SABADO, DOMINGO` (Monday-first,
no accents — matches the uppercase-code style already used for category
codes). `isOpen` on the `Shop`/list projections is computed server-side
from `hours` + `manuallyClosed`, evaluated in `Africa/Maputo`.

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
  `{ id, code, name, sortOrder, active, imageUrl }[]`. `imageUrl` is a
  presigned GET (same TTL/re-fetch rule as every other photo in this
  doc), `null` until an Admin sets one.
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
| `POST /{categoryId}/image/presign` | Body `{ contentType }` → `200` `{ uploadUrl, key, expiresAt }`. New S3 key namespace `aws.s3.categories-prefix` (`categories/`, `S3KeyFactory.categoryImageKey`), separate from stores/products/users. |
| `POST /{categoryId}/image` | Body `{ key }` → `200` updated `Category` (with `imageUrl`). `400 VALIDATION_ERROR` if not yet in S3 or `key` doesn't belong to this category (`S3KeyFactory.requireOwnedKey`, same pattern as shop logo/cover). |

`categories.image_key` column added in `V7__category_images.sql`
(nullable `varchar(500)`, no backfill needed — every existing category
starts with no image).

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

Base path `/api/v1/merchant/shops/{shopId}/products`.

Every endpoint here — read and write — is accessible to `MERCHANT`
(owner), `MERCHANT_STAFF` (assigned to that shop via JWT `shopId`
claim), and `ADMIN`. This is the one place `MERCHANT_STAFF` gets real
write access (see the Auth section above) — verified live.

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
  shop assets, `{aws.s3.users-prefix}{userId}/{uuid}.{ext}` for user
  profile photos (§5 below). The backend generates the key, not the
  client — a client never gets to choose where in the bucket its file
  lands (see `S3KeyFactory`).
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

See [§2 Category taxonomy](#2-category-taxonomy) for category/subcategory
admin CRUD.

**Shop management** — Admin gets the same capabilities as the owning
`MERCHANT` on any shop (§1's `ADMIN`-tagged endpoints: profile, hours,
status, logo/cover, plus all of §3 Products), ownership check bypassed.
Two things stay `MERCHANT`-only even for Admin: creating a brand-new
shop, and the "list my shops" endpoint (Admin uses the listing below
instead).

### `GET /api/v1/admin/shops` — ROLE_ADMIN only

Every shop on the platform, not scoped to any owner. Query params:
`query` (trade-name search, case-insensitive substring), `status` (one
of `StoreStatus`), `categoryId` (**new** — uuid; filters to shops whose
`store_categories` includes this top-level category, via a `Specification`
subquery against `StoreCategory` — `StoreService.listForAdmin`), `page`,
`size`, `sort`. `200` → standard `PageResponse` envelope, rows:

```json
{ "id", "name", "logoUrl", "status", "isOpen", "ownerId", "ownerName", "ownerEmail", "createdAt" }
```

`ownerName`/`ownerEmail` are always `null` — this service has no local
user data and no HTTP client to KONECTA-SECURITY-SERVICE wired up to
resolve them by id yet. `ownerId` (the JWT `sub` the shop was created
under) is populated and sufficient to deep-link into
`/admin/shops/{shopId}`. Wiring up the owner-lookup is a follow-up if the
Admin UI needs it rendered.

Backed by `StoreRepository` now extending `JpaSpecificationExecutor`;
service method `StoreService.listForAdmin`.

Regression-tested in
`MerchantFlowIntegrationTest#admin_canManageAnyShopButNotCreateOne`
(synthetic `ROLE_ADMIN` JWT against the Testcontainers Postgres stack —
not yet re-verified against a live security-service-issued token, unlike
the `MERCHANT_STAFF` fix above).

`AGENTS.md` §10 Slice G store-suspend is still open (there's no explicit
"suspend" action beyond `PATCH .../status`'s manual-close toggle and
directly setting `status` via `PATCH /merchant/shops/{shopId}` — no
dedicated admin suspend/reinstate endpoint with its own audit trail yet).

## 5. User profile photo — not merchant-scoped, no persistence here

`/api/v1/users/me/photo/presign` and `/api/v1/users/me/photo` —
**any authenticated role** (not `@PreAuthorize("hasRole('MERCHANT')")`
like every other controller here), since a profile photo isn't a
merchant-business concept. Added per direct request alongside the same
S3 bucket the shop/product assets use, under a new `aws.s3.users-prefix`
(`users/`) — deliberately a **new** prefix, not a repurposing of
`aws.s3.products-prefix`, to avoid colliding with product photo keys.

This service has **no user table** — KONECTA-SECURITY-SERVICE owns user
profiles. `UserPhotoController` is pure S3 plumbing: presign, confirm
(existence check + presigned GET back), nothing persisted. The frontend
is responsible for then sending the resulting URL/key to
KONECTA-SECURITY-SERVICE to actually save it on the profile — this
service has nowhere to save it even if it wanted to.

- `POST /api/v1/users/me/photo/presign` — body `{ contentType }` → `200`
  `{ uploadUrl, key, expiresAt }`. Key is scoped to the caller's own
  `jwt.sub` (`users/{sub}/{uuid}.ext`) — can't presign into another
  user's folder.
- `POST /api/v1/users/me/photo` — body `{ key }` → `200` `{ url }` (a
  presigned GET). `400 VALIDATION_ERROR` if the object isn't in S3 yet
  or `key` doesn't belong to the caller.

## Data models

### `Shop`

`id, name, legalName, nuit, email, phone, address, city, neighborhood,
latitude, longitude, categories: Category[], description, logoUrl,
coverUrl, status, isOpen, manuallyClosed, activationReady, acceptsPickup,
acceptsDelivery, createdAt, updatedAt`

### `Category`

`id, code, name, sortOrder, active, imageUrl`

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
