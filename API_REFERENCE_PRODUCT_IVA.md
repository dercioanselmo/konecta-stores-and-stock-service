# Per-product IVA rate — PROPOSED API contract

**Status: PROPOSED. Frontend is fully built against this contract;
backend has not implemented any of it yet** — confirmed live: sending
`ivaRate` on `POST /api/v1/merchant/shops/{shopId}/products` is silently
ignored (`200`, product created, field just isn't there and isn't
persisted — not a validation error, so the frontend submit doesn't
break, but the value the merchant typed is lost).

IVA in Mozambique varies by product category (not a flat 17% across the
whole catalog), so the rate needs to live on the product, not be a
platform-wide constant.

---

## 1. `ivaRate` on the product

A percentage (e.g. `17`, `5`), settable per product, defaulting to `17`
when a merchant/staff member doesn't change it. Needed on:

- `KONECTA-STORES-AND-STOCK-SERVICE`'s `POST /api/v1/merchant/shops/{shopId}/products`
  (create) and `PATCH .../products/{productId}` (update) request bodies.
- The product read shape returned by both of those, plus
  `GET .../products/{productId}` and the products list — merchants need
  to see/edit the rate they set, same as `price`.

Frontend already sends this field (`lib/stores/types.ts`'s
`CreateProductPayload.ivaRate` / `UpdateProductPayload.ivaRate`) and
reads `Product.ivaRate` back, falling back to `17` when it's
`null`/absent (both for products created before this existed, and right
now, for every product, since the backend doesn't return it yet).

## 2. `ivaRate` carried onto cart lines

`KONECTA-CART-SERVICE`'s cart read/mutate responses need `ivaRate` per
line item, read from the product at add-time (same place it already
reads `unitPrice`/`name`/etc. from Stores-and-Stock). Needed so
`app/checkout/CheckoutView.tsx` can sum IVA per line instead of assuming
one flat rate for the whole cart.

Frontend: `lib/cart/types.ts`'s `CartItem.ivaRate`, optional/nullable,
already wired into `lib/checkout/moneyBreakdown.ts`'s per-line
computation with a `17` fallback.

## 3. `ivaRate` carried onto order lines

`KONECTA-CHECKOUT-SERVICE` needs to copy each line's `ivaRate` from the
cart onto the order at checkout time (same as it already copies
`unitPrice`, so the rate in effect at purchase time is preserved even
if the product's rate changes later). `KONECTA-ORDERS-SERVICE` needs to
return it on `OrderItem` for both detail reads (customer and merchant) —
not needed on any list endpoint, same reasoning as `qrCode` before it.

Frontend: `lib/checkout/types.ts`'s `OrderItem.ivaRate`, optional/
nullable (`null` for every order placed before this existed — treated
as `17` there, forever, since the order should reflect the rate that
actually applied at purchase time, not be reinterpreted later).

---

## How the frontend uses it (already built, no further changes needed)

`lib/checkout/moneyBreakdown.ts`'s `computeMoneyBreakdown(subtotal, deliveryFee, items)`
sums IVA as `Σ lineTotal_i × rate_i / (100 + rate_i)` across the passed
items (falling back to a flat 17% only when no items are passed, or a
given line has no rate) — used identically by the checkout screen, the
customer/merchant/admin order-detail money summary, and all three
receipt routes, so a mixed-rate cart/order shows a single correct IVA
total everywhere without duplicating the math.

The merchant/staff product form (`NewProductForm.tsx`,
`ProductDetailView.tsx`) has a "Taxa de IVA (%)" field, defaulting to
`17`, submitted alongside price/stock/etc. — it's a no-op today since
the backend drops it, but will start working the moment the backend
implements §1 with no frontend change required.

`tsc --noEmit`, `eslint`, `npm run build` all clean.
