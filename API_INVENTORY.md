# Stores-and-Stock — API inventory

Base URL: `http://localhost:8092` · Eureka name: `KONECTA-STORES-AND-STOCK-SERVICE`
Auth: `Authorization: Bearer <JWT>` from KONECTA-SECURITY-SERVICE (HS256, shared secret). Roles: `MERCHANT`, `MERCHANT_STAFF`, `ADMIN`, or none (public).
Errors: `{ code, message, details[], timestamp }` — `code` machine-readable, `message`/`details` in Portuguese.
Uploads: presigned S3, two calls — `POST .../presign` → `{ uploadUrl, key, expiresAt }` → client `PUT`s the file to `uploadUrl` directly → `POST` the sibling endpoint with `{ key }` to confirm.

---

## Shops — `/api/v1/merchant/shops`

| Method & path | Role | Body | Returns |
|---|---|---|---|
| `GET /` | MERCHANT, MERCHANT_STAFF | — | `ShopCardResponse[]` |
| `POST /` | MERCHANT | `CreateShopRequest` | `Shop` |
| `GET /{shopId}` | MERCHANT (owner), MERCHANT_STAFF (assigned), ADMIN | — | `Shop` |
| `PATCH /{shopId}` | MERCHANT (owner), ADMIN | `UpdateShopRequest` | `Shop` |
| `POST /{shopId}/logo/presign` | MERCHANT (owner), ADMIN | `{ contentType }` | `{ uploadUrl, key, expiresAt }` |
| `POST /{shopId}/logo` | MERCHANT (owner), ADMIN | `{ key }` | `Shop` |
| `POST /{shopId}/cover/presign` | MERCHANT (owner), ADMIN | `{ contentType }` | `{ uploadUrl, key, expiresAt }` |
| `POST /{shopId}/cover` | MERCHANT (owner), ADMIN | `{ key }` | `Shop` |
| `PATCH /{shopId}/location` | MERCHANT (owner), ADMIN | `{ latitude, longitude }` | `Shop` |
| `PATCH /{shopId}/status` | MERCHANT (owner), ADMIN | `{ manuallyClosed, reason? }` | `Shop` |
| `GET /{shopId}/hours` | MERCHANT (owner), MERCHANT_STAFF (assigned), ADMIN | — | `Hours` |
| `PUT /{shopId}/hours` | MERCHANT (owner), ADMIN | `Hours` | `Hours` |

`ShopCardResponse`: `id, name, logoUrl, isOpen, lowStockCount, categories: Category[]`

`Shop`: `id, name, legalName, nuit, email, phone, address, city, neighborhood, latitude, longitude, categories: Category[], description, logoUrl, coverUrl, status (DRAFT|PENDING_REVIEW|ACTIVE|SUSPENDED|CLOSED), isOpen, manuallyClosed, activationReady, acceptsPickup, acceptsDelivery, createdAt, updatedAt`

`CreateShopRequest`: `name*, nuit, address*, city* ("Maputo" only), neighborhood, phone, categoryIds: uuid[], description`
`UpdateShopRequest`: same fields, all optional, plus `acceptsPickup, acceptsDelivery` (booleans)

`Hours`: `{ days: [{ day (SEGUNDA..DOMINGO), opensAt, closesAt, closed }] }` — full-week replace on `PUT`

`{shopId}` not owned/assigned/found → `404 SHOP_NOT_FOUND` (not `403`).

---

## Products & stock — `/api/v1/merchant/shops/{shopId}/products`

Role: MERCHANT (owner), MERCHANT_STAFF (assigned shop, full read/write), ADMIN (any shop).

| Method & path | Body | Returns |
|---|---|---|
| `GET /?query&categoryId&subcategoryId&active&lowStock&page&size&sort` | — | `Page<Product>` |
| `POST /` | `CreateProductRequest` | `Product` |
| `GET /{productId}` | — | `Product` |
| `PATCH /{productId}` | `UpdateProductRequest` | `Product` |
| `PATCH /{productId}/active?active=true\|false` | — | `Product` |
| `PATCH /{productId}/stock` | `{ quantity }` (absolute, ≥0) | `Product` |
| `POST /{productId}/photos/presign` | `{ contentType }` | `{ uploadUrl, key, expiresAt }` |
| `POST /{productId}/photos` | `{ key }` | `{ id, url, isPrimary }` |
| `DELETE /{productId}/photos/{photoId}` | — | `204` |
| `PATCH /{productId}/photos/{photoId}/primary` | — | `{ id, url, isPrimary }` |

`Product`: `id, shopId, name, description, subcategoryId, subcategoryName, categoryId, categoryName, price, stockQuantity, lowStockThreshold, active, lowStock, photos: [{id,url,isPrimary}], createdAt, updatedAt`

`CreateProductRequest`: `name*, description*, subcategoryId, price* (≥0), stockQuantity* (≥0), lowStockThreshold (default 5), active (default true)`
`UpdateProductRequest`: same fields, all optional/partial

---

## Dashboard — `/api/v1/merchant/shops/{shopId}/dashboard`

Role: MERCHANT (owner), MERCHANT_STAFF (assigned), ADMIN.

| Method & path | Returns |
|---|---|
| `GET /summary` | `{ isOpen, productCount, activeProductCount, lowStockCount }` |

---

## Category taxonomy (public reads) — `/api/v1/meta/categories`

| Method & path | Returns |
|---|---|
| `GET /` | `Category[]` — active only |
| `GET /{categoryId}/subcategories` | `Subcategory[]` — active only |

`Category`: `id, code, name, sortOrder, active, imageUrl`
`Subcategory`: `id, categoryId, categoryCode, categoryName, code, name, sortOrder, active, imageUrl`

---

## Category/subcategory admin CRUD — `/api/v1/admin/categories`

Role: ADMIN only.

| Method & path | Body | Returns |
|---|---|---|
| `GET /` | — | `Category[]` (all, incl. inactive) |
| `POST /` | `{ code*, name*, sortOrder?, active? }` | `Category` |
| `GET /{categoryId}` | — | `Category` |
| `PATCH /{categoryId}` | `{ name?, sortOrder?, active? }` | `Category` (code immutable) |
| `DELETE /{categoryId}` | — | `204` (`409` if in use) |
| `POST /{categoryId}/image/presign` | `{ contentType }` | `{ uploadUrl, key, expiresAt }` |
| `POST /{categoryId}/image` | `{ key }` | `Category` |
| `GET /{categoryId}/subcategories` | — | `Subcategory[]` (all) |
| `POST /{categoryId}/subcategories` | `{ code*, name*, sortOrder?, active? }` | `Subcategory` |
| `GET /{categoryId}/subcategories/{id}` | — | `Subcategory` |
| `PATCH /{categoryId}/subcategories/{id}` | `{ name?, sortOrder?, active? }` | `Subcategory` |
| `DELETE /{categoryId}/subcategories/{id}` | — | `204` (`409` if in use) |
| `POST /{categoryId}/subcategories/{id}/image/presign` | `{ contentType }` | `{ uploadUrl, key, expiresAt }` |
| `POST /{categoryId}/subcategories/{id}/image` | `{ key }` | `Subcategory` |

---

## Admin shops — `/api/v1/admin/shops`

Role: ADMIN only.

| Method & path | Returns |
|---|---|
| `GET /?query&status&categoryId&page&size&sort` | `Page<AdminShopSummary>` |

`AdminShopSummary`: `id, name, logoUrl, status, isOpen, ownerId, ownerName (always null — no Security-service lookup wired up), ownerEmail (always null), createdAt`

---

## Public shop browse — `/api/v1/shops`

No auth.

| Method & path | Returns |
|---|---|
| `GET /?categoryId*&lat*&lng*&page&size` | `Page<PublicShop>` — active shops with `categoryId`, nearest-first (Haversine); unlocated shops excluded |
| `GET /{shopId}` | `PublicShopDetail` |
| `GET /{shopId}/products?subcategoryId&page&size` | `Page<PublicProduct>` — active products only |
| `GET /{shopId}/products/{productId}` | `PublicProductDetail` |

`PublicShop`: `id, name, logoUrl, coverUrl, isOpen, distanceKm`
`PublicShopDetail`: `id, name, logoUrl, coverUrl, isOpen, latitude, longitude, categories: Category[]` (`latitude`/`longitude` nullable — `null` until the merchant sets a location)
`PublicProduct`: `id, name, photoUrl, price, inStock` (boolean, not raw quantity)
`PublicProductDetail`: `id, shopId, name, description, photoUrl, price, inStock, categoryName, subcategoryId, subcategoryName`

Unknown or non-`ACTIVE` `{shopId}` → `404 SHOP_NOT_FOUND` on the shop-detail and shop-products endpoints.
Unknown/wrong-shop/inactive-shop `{productId}` → `404 PRODUCT_NOT_FOUND` on the product-detail endpoint (never `SHOP_NOT_FOUND` there).

---

## Stock commit — `/api/v1/shops/{shopId}/stock/commit`

For KONECTA-CHECKOUT-SERVICE. Role: **any authenticated user** (not merchant-scoped — the
caller is the customer placing the order). This is the one path under `/api/v1/shops/**` that
is *not* public; `SecurityConfig` carves it out ahead of that prefix's `permitAll` rule.

| Method & path | Body | Returns |
|---|---|---|
| `POST /` | `{ orderId: uuid, items: [{ productId: uuid, quantity: int (≥1) }] }` | `200 { orderId, items: [{ productId, stockQuantity }] }` |

Atomically decrements `stockQuantity` for every line. **All-or-nothing**: if any line's
`quantity` exceeds current stock, no line is decremented. **Idempotent on `orderId`**: a retried
call with an `orderId` already committed returns the same success response (current stock
levels) without decrementing again — proven by checking for an existing `stock_movements` row
with `ref_type='ORDER', ref_id=<orderId>` (all lines of one commit are written in a single
transaction, so any row implies the whole commit already succeeded).

**Errors**:

| Status | Code | When |
|---|---|---|
| `401` | `UNAUTHENTICATED` | No token |
| `404` | `SHOP_NOT_FOUND` | Unknown or non-`ACTIVE` `{shopId}` |
| `404` | `PRODUCT_NOT_FOUND` | A `productId` doesn't exist or doesn't belong to `{shopId}` |
| `409` | `INSUFFICIENT_STOCK` | Any line exceeds current stock. Body: `{ code, message, failedItems: [{ productId, requested, available }], timestamp }` — note this is a **different envelope shape** than every other error in this API (no `details: string[]`), since a flat string list can't carry structured per-line data. |

---

## User profile photo — `/api/v1/users/me/photo`

Role: any authenticated user (not merchant-specific). **This service does not persist the result** — no user table here; caller must separately save the URL via KONECTA-SECURITY-SERVICE.

| Method & path | Body | Returns |
|---|---|---|
| `POST /presign` | `{ contentType }` | `{ uploadUrl, key, expiresAt }` |
| `POST /` | `{ key }` | `{ url }` |

---

## Ops

| Method & path | Auth |
|---|---|
| `GET /actuator/health` | public |
| `GET /v3/api-docs`, `GET /swagger-ui.html` | public |
