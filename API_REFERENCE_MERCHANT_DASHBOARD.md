# Merchant Dashboard — API requirements

Frontend-authored spec: this backend **does not exist yet**. Everything
below is what the KONECTA Merchant dashboard (Phase 1, per `AGENTS.md` §6)
needs — write the real service against this, not the other way around.
Once endpoints are live, tell the frontend and it'll be wired up and tested
against them (same flow as `API_REFERENCE-security-service.md`, which
started the same way as an Auth spec before the real service existed).

Auth for every endpoint below: `Authorization: Bearer <accessToken>`,
role `MERCHANT` (same JWT/session already issued by the Auth service — no
new auth mechanism needed here). A non-merchant token should get `403`; no
token, `401` — reuse the Auth service's own envelope for both
(`{code, message, details, timestamp}`, `UNAUTHENTICATED`/`ACCESS_DENIED`)
so the frontend's existing error handling works unchanged.

---

## The one big model decision this spec assumes

**One Merchant user can own multiple shops; every resource below (products,
orders, hours, fiscal data, sales, receipts) belongs to a shop, not
directly to the merchant.** This comes from `AGENTS.md` §6: *"One Merchant
can create and manage multiple shops/store. His dashboard will be by
shop."* Every endpoint after the shops list is scoped under
`/merchant/shops/{shopId}/...`, and the backend must verify `{shopId}`
belongs to the authenticated merchant on every call (`403` otherwise — same
enforcement style as the Auth service's admin endpoints).

If this turns out to be wrong (e.g. backend wants one-shop-per-merchant for
Phase 1 and multi-shop later), say so — it's the single decision that
reshapes this whole contract the most, better to confirm before building.

---

## 1. Shops

### `GET /api/v1/merchant/shops` — list the caller's shops

Powers the top-level `/merchant` dashboard (shop picker + at-a-glance
cards) — return enough per shop to render a card without a follow-up call
per shop.

**Response `200 OK`**

```json
[
  {
    "id": "uuid",
    "name": "Loja Central",
    "logoUrl": "https://.../logo.png",
    "isOpen": true,
    "todaySalesTotal": 12500.0,
    "pendingOrdersCount": 3,
    "lowStockCount": 2
  }
]
```

### `POST /api/v1/merchant/shops` — create a shop

**Request body**

| Field | Type | Notes |
|---|---|---|
| `name` | string | Required |
| `nuit` | string | Mozambican tax id, required for invoices |
| `address` | string | Required |
| `city` | string | `"Maputo"` only, same constraint as user profiles |
| `neighborhood` | string | Must match a seeded bairro (reuse `GET /api/v1/meta/neighborhoods` from the Auth service) |
| `phone` | string | Same MZ mobile format as Auth |
| `category` | string | See [Categories](#optional-get-apiv1metacategories) below |
| `description` | string, optional | |

**Response `201 Created`** — a `Shop` (see [data model](#shop)).

**Errors**: `400 VALIDATION_ERROR`.

### `GET /api/v1/merchant/shops/{shopId}` — full shop profile

Fiscal + settings fields for `/merchant/settings/fiscal`.

### `PATCH /api/v1/merchant/shops/{shopId}` — edit fiscal/profile fields

Same field set as create, all optional/partial.

### `PATCH /api/v1/merchant/shops/{shopId}/status` — manual open/pause override

Lets a merchant temporarily close a shop even during posted hours (e.g.
"paused, back in 30 min") — independent of the opening-hours schedule.

**Request body**: `{ "manuallyClosed": boolean, "reason": string? }`

**Response `200 OK`** — updated `Shop`.

### `GET` / `PUT /api/v1/merchant/shops/{shopId}/hours` — opening hours

**`PUT` request body** — full week, replace-all semantics (simpler than
per-day PATCH for a settings-form UI):

```json
{
  "days": [
    { "day": "MONDAY", "opensAt": "08:00", "closesAt": "18:00", "closed": false },
    { "day": "SUNDAY", "opensAt": null, "closesAt": null, "closed": true }
  ]
}
```

`GET` returns the same shape. The computed "is this shop open right now"
value is what powers `isOpen` in the shops list above and (later) the
Customer app's open/closed badge — compute it server-side from `hours` +
the manual override, don't make every client reimplement that logic.

---

## 2. Products & stock

All under `/api/v1/merchant/shops/{shopId}/products`.

### `GET .../products` — list/search/paginate

**Query params**: `query` (name contains), `category`, `active`
(true/false), `lowStock` (true → only items at/under their threshold),
`page`, `size`, `sort` — reuse the Auth service's `PageResponse<T>` shape
for the response envelope, for consistency across services.

### `POST .../products` — create

**Request body**

| Field | Type | Notes |
|---|---|---|
| `name` | string | Required |
| `description` | string | Required |
| `category` | string | See [Categories](#optional-get-apiv1metacategories) |
| `price` | number | **IVA-inclusive shelf price** — per `AGENTS.md` §5.4, catalog prices always include IVA; the base/IVA breakdown is a receipt/invoice-time concern, not a product field |
| `stockQuantity` | integer | Required, ≥ 0 |
| `lowStockThreshold` | integer, optional | Default server-side (e.g. 5) if omitted — drives `lowStock` filter and the dashboard's low-stock count |
| `active` | boolean | Default `true` — an inactive product is hidden from the Customer catalog but keeps its order history |

**Response `201 Created`** — a `Product` (see [data model](#product)). Photos
are attached separately (see below) since this is a JSON body, not
multipart.

### `GET` / `PATCH .../products/{productId}` — detail / edit

Same field set as create, partial on `PATCH`.

### `PATCH .../products/{productId}/active` — archive/restore

Query param `active=true|false`. Soft delete, not a hard `DELETE` — a
product may be referenced by historical orders/receipts and must stay
resolvable there even after being pulled from sale.

### `PATCH .../products/{productId}/stock` — adjust stock

**Request body**: `{ "quantity": integer }` — sets the **absolute** stock
level (simplest for a stock-edit form; if the backend also wants
delta-based adjustment for an audit trail, that's a nice-to-have, not a
blocker — flag if you'd rather do it that way instead).

### Photos

### `POST .../products/{productId}/photos` — upload

`multipart/form-data`, field name `file`. Returns the created photo
so the UI can render it immediately.

**Response `201 Created`**: `{ "id": "uuid", "url": "https://...", "isPrimary": boolean }`

### `DELETE .../products/{productId}/photos/{photoId}` — remove

### `PATCH .../products/{productId}/photos/{photoId}/primary` — set cover photo

**Backend decision needed**: object storage target (S3-compatible bucket,
CDN URL shape, max file size/format) — the frontend just needs a URL back
per photo, doesn't care how it's stored.

---

## 3. Orders (merchant-facing)

All under `/api/v1/merchant/shops/{shopId}/orders`. Orders themselves are a
platform-wide concept (Customer creates them, Courier fulfills delivery
ones) — this section only covers what the **Merchant** dashboard needs to
read/act on for orders belonging to its shop. The status enum is the
platform-wide one from `AGENTS.md` §9:

```
CREATED → PAID → [PENDING_STORE_OPEN] → STORE_CONFIRMED → PREPARING → READY_FOR_PICKUP
  → [COURIER_ASSIGNED → PICKED_UP → IN_TRANSIT] → DELIVERED
(also: CANCELLED, REFUNDED)
```

Pickup orders skip the courier states entirely.

### `GET .../orders` — list/filter

**Query params**: `status`, `fulfillmentType` (`PICKUP`/`DELIVERY`),
`from`/`to` (date range), `page`, `size`, `sort`.

### `GET .../orders/{orderId}` — full detail

Items, quantities, unit price, customer name + phone (merchant sees this
immediately — the courier-phone-after-accept privacy rule in `AGENTS.md`
§10 is courier-specific, not merchant-specific), fulfillment type, delivery
address if applicable, payment method + status, totals (subtotal, delivery
fee, total), and a `statusHistory: [{status, at}]` array so the UI can
render a timeline the same way the Customer order-tracking screen will.

### Status transitions — one endpoint per action (mirrors the Admin API's style)

| Endpoint | Effect |
|---|---|
| `PATCH .../orders/{orderId}/accept` | → `STORE_CONFIRMED` |
| `PATCH .../orders/{orderId}/reject` | → `CANCELLED`. Body: `{ "reason": string? }` |
| `PATCH .../orders/{orderId}/prepare` | → `PREPARING` |
| `PATCH .../orders/{orderId}/ready` | → `READY_FOR_PICKUP` |
| `POST .../orders/{orderId}/validate-pickup` | Body: `{ "code": string }` — validates the customer's pickup QR/code (shown on the Customer app's order confirmation), marks the order `DELIVERED`. **Pickup orders only** — a `DELIVERY` order reaches `DELIVERED` via the (not-yet-built) Courier flow instead. |

All should `400`/`409` on an illegal transition (e.g. `accept` on an
already-`CANCELLED` order) — reuse `VALIDATION_ERROR`/a dedicated
`ILLEGAL_STATUS_TRANSITION` code, backend's call which reads better.

---

## 4. Dashboard summary

### `GET /api/v1/merchant/shops/{shopId}/dashboard/summary`

One aggregate call for the per-shop dashboard screen (avoids the frontend
making 4+ calls to assemble one view).

```json
{
  "salesTodayTotal": 12500.0,
  "ordersTodayCount": 14,
  "ordersByStatus": { "STORE_CONFIRMED": 2, "PREPARING": 1, "READY_FOR_PICKUP": 1 },
  "lowStockCount": 2,
  "isOpen": true
}
```

---

## 5. Sales summary

### `GET /api/v1/merchant/shops/{shopId}/sales/summary`

**Query params**: `from`, `to` (dates), `granularity` (`day`/`week`/`month`).

**Response `200 OK`**

```json
{
  "totalRevenue": 87500.0,
  "totalOrders": 62,
  "averageTicket": 1411.29,
  "series": [
    { "period": "2026-08-27", "revenue": 12500.0, "orders": 14 }
  ]
}
```

---

## 6. Receipts — "Recebimentos por transação"

Per-transaction ledger, distinct from the aggregate Sales summary above.
Explicitly **not** a day-close/manual-payout screen — per `AGENTS.md` §5.6
and §6, there is no "Fecho do Dia" UI, this is read-only reporting of
splits the Payments API already computed.

### `GET /api/v1/merchant/shops/{shopId}/receipts`

**Query params**: `from`, `to`, `status` (`PAID`/`PENDING`/`REFUNDED`),
`page`, `size`.

**Response item shape**

| Field | Type |
|---|---|
| `orderId` | uuid |
| `date` | timestamp |
| `grossAmount` | number |
| `commissionAmount` | number (KONECTA's cut) |
| `netAmount` | number (what the merchant actually receives) |
| `paymentMethod` | `M_PESA` \| `E_MOLA` \| `VISA` \| `COD` |
| `status` | `PAID` \| `PENDING` \| `REFUNDED` |

### `GET .../receipts/{receiptId}` — single receipt detail

Same shape as one list item, plus enough for a printable/downloadable
receipt (shop fiscal fields, order line items, IVA breakdown — base +
IVA amount, derived from `grossAmount` and the platform's fixed IVA rate,
not something the merchant configures).

---

## Optional: `GET /api/v1/meta/categories`

Product `category` needs a fixed taxonomy so the (not-yet-built) Customer
catalog can browse/filter by category consistently — same pattern as
`GET /api/v1/meta/neighborhoods` already does for bairros. If this doesn't
exist yet, `category` can temporarily be a free string on `Product`, but
flag that as tech debt — it'll need to become a real lookup before the
Customer catalog ships.

---

## Data models

### `Shop`

| Field | Type |
|---|---|
| `id` | uuid |
| `name` | string |
| `nuit` | string |
| `address` | string |
| `city` | string |
| `neighborhood` | string |
| `phone` | string |
| `category` | string |
| `description` | string? |
| `logoUrl` | string? |
| `isOpen` | boolean (computed: hours + manual override) |
| `manuallyClosed` | boolean |
| `createdAt` | timestamp |

### `Product`

| Field | Type |
|---|---|
| `id` | uuid |
| `shopId` | uuid |
| `name` | string |
| `description` | string |
| `category` | string |
| `price` | number (IVA-inclusive) |
| `stockQuantity` | integer |
| `lowStockThreshold` | integer |
| `active` | boolean |
| `photos` | `{ id, url, isPrimary }[]` |
| `createdAt` | timestamp |

### `MerchantOrder`

| Field | Type |
|---|---|
| `id` | uuid |
| `shopId` | uuid |
| `status` | see enum above |
| `fulfillmentType` | `PICKUP` \| `DELIVERY` |
| `items` | `{ productId, name, quantity, unitPrice }[]` |
| `subtotal` / `deliveryFee` / `total` | number |
| `customerName` / `customerPhone` | string |
| `deliveryAddress` | string? (delivery only) |
| `paymentMethod` | `M_PESA` \| `E_MOLA` \| `VISA` \| `COD` |
| `paymentStatus` | `PAID` \| `PENDING` \| `REFUNDED` |
| `statusHistory` | `{ status, at }[]` |
| `createdAt` | timestamp |

---

## Open questions for the backend team

1. **Multi-shop confirmed?** See the model decision at the top — this is
   the one thing worth a second look before implementing.
2. **Which service(s) own this?** Presented here as one logical surface
   (`/merchant/...`), but Products/Orders/Payments are plausibly separate
   microservices already on your roadmap — the contract above doesn't
   assume either way, split it however makes sense operationally.
3. **Object storage for product photos** — no opinion here, just need a
   URL back per uploaded photo.
4. **Category taxonomy** — fixed list via a meta endpoint (preferred, for
   Customer-catalog consistency later) vs. free string for now.
5. **Stock adjustment** — absolute set (`PATCH .../stock`) vs. delta-based
   with an audit trail. Either works for the frontend; flag if you'd rather
   do the latter.
6. **IVA rate** — assumed fixed/platform-wide, not merchant-configurable.
   Confirm, since it affects whether `Shop` needs an `ivaRate` field.
