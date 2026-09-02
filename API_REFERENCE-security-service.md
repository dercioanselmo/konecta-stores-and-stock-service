# KONECTA Auth API

Identity, sessions, and roles for every KONECTA client — Customer, Merchant, Courier, and Mobility dashboards alike.

**Base URL (local):** `http://localhost:8091`

---

## Overview & auth model

Tokens are stateless JWTs; sessions (refresh tokens) are tracked server-side so they can be revoked.

**Sending a token:**

```
Authorization: Bearer <accessToken>
```

**Access token claims:**

```json
{
  "sub": "<userId>",
  "email": "ana@example.com",
  "roles": "ROLE_CUSTOMER",
  "type": "access",
  "iat": 1788295559,
  "exp": 1788296459
}
```

> Access tokens expire in **15 minutes** by default (`expiresInSeconds` in the login/refresh response tells you the real value). Refresh tokens last up to 14 days and **rotate on every use** — the old one is revoked the instant a new one is issued, so store the new pair every time.

---

## Registration flow

1. `POST /auth/register` — create the account. Response has `emailVerified: false`.
2. An OTP is emailed automatically. User enters the 6-digit code.
3. `POST /auth/verify-otp` with `purpose: "REGISTER"` — flips `emailVerified` to `true`.
4. `POST /auth/login` — exchange email + password for an access/refresh token pair.
5. Store both tokens. Attach the access token as a Bearer header on every request.
6. When a request 401s with `INVALID_REFRESH_TOKEN` or the access token nears expiry, call `POST /auth/refresh` to rotate.
7. `POST /auth/logout` when the user signs out, to revoke the refresh token server-side.

> Self-registration always assigns `CUSTOMER` immediately. A customer can additionally *request* to become a Merchant, Courier, or Mobility Partner at registration time (`requestedRole` field) — that role is only granted once an admin approves it. See [Role upgrade requests](#role-upgrade-requests) below. Admin can also be granted directly via [Assign role](#patch-apiv1adminusersidrole) — there is no self-request path to `ADMIN`.

---

## Auth

### `POST /api/v1/auth/register` — Public

Creates a customer account and fires off a registration OTP by email. City is locked to Maputo; neighborhood must be one of the seeded bairros (see [Meta](#get-apiv1metaneighborhoods)).

**Request body**

| Field | Type | Notes |
|---|---|---|
| `firstName` | string | Required |
| `lastName` | string | Required |
| `email` | string | Required, unique, normalized to lowercase |
| `password` | string | Required, 8–100 chars |
| `birthDate` | date | `YYYY-MM-DD`, must be in the past |
| `phone` | string | Mozambican mobile: `+258` optional, then `8[2-7]` + 7 digits |
| `address` | string | Required |
| `city` | string | Must be `"Maputo"` |
| `neighborhood` | string | Must match a seeded bairro for the given city |
| `requestedRole` | string, optional | If present, one of `MERCHANT`, `COURIER`, `MOBILITY_PARTNER` — submits a role-upgrade request for admin review. Omit entirely for a plain customer signup. |

**Response `201 Created`**

```json
{
  "id": "96f589ac-c8d5-4bf3-bd2b-6336ab323cc5",
  "firstName": "Dercio",
  "lastName": "Anselmo",
  "email": "dercio.anselmo@gmail.com",
  "birthDate": "1995-05-20",
  "phone": "+258841234567",
  "address": "Av. Julius Nyerere",
  "city": "Maputo",
  "neighborhood": "Central",
  "role": "CUSTOMER",
  "status": "ACTIVE",
  "requestedRole": null,
  "emailVerified": false,
  "phoneVerified": false,
  "enabled": true,
  "createdAt": "2026-09-01T21:35:22.489Z"
}
```

If `requestedRole` was supplied, the response instead has `status: "PENDING"` and `requestedRole` set to what was requested — `role` stays `CUSTOMER` until approved.

**Errors**

| Status | Code | Meaning |
|---|---|---|
| 409 | `EMAIL_ALREADY_REGISTERED` | Email already exists |
| 400 | `VALIDATION_ERROR` | See `details[]` for the failing field(s), incl. bad city / neighborhood / phone |
| 400 | `ROLE_NOT_REQUESTABLE` | `requestedRole` was something other than `MERCHANT`, `COURIER`, or `MOBILITY_PARTNER` (e.g. `ADMIN`, or `CUSTOMER`) |

---

### `POST /api/v1/auth/verify-otp` — Public

Confirms a one-time code. Currently wired up for `purpose: "REGISTER"`, which marks the account's email as verified and returns the updated profile.

**Request body**

| Field | Type | Notes |
|---|---|---|
| `target` | string | The email the OTP was sent to |
| `code` | string | 6-digit code |
| `purpose` | enum | `REGISTER` · `LOGIN` · `VERIFY_PHONE` |

**Response `200 OK`** — same shape as the register response, with `emailVerified: true`.

**Errors**

| Status | Code | Meaning |
|---|---|---|
| 400 | `OTP_NOT_FOUND` | No pending OTP for that target/purpose |
| 400 | `OTP_EXPIRED` | Code has passed its TTL (5 min default) |
| 400 | `OTP_INVALID` | Code doesn't match |
| 429 | `OTP_LOCKED` | Too many wrong attempts — request a new code |
| 400 | `UNSUPPORTED_OTP_PURPOSE` | `LOGIN` / `VERIFY_PHONE` aren't wired to an action yet |

---

### `POST /api/v1/auth/set-password` — Public

Completes an admin-issued invite (see [Create staff user](#post-apiv1adminusers)) — sets a password on an account that had none, and logs the user in. The `code` is the token embedded in the "Set up your KONECTA account" email link.

**Request body**

| Field | Type | Notes |
|---|---|---|
| `email` | string | The invited account's email |
| `code` | string | The token from the invite email/link |
| `newPassword` | string | 8–100 chars |

**Response `200 OK`** — same shape as [Login](#post-apiv1authlogin) (logs the user straight in).

**Errors:** same OTP errors as [Verify OTP](#post-apiv1authverify-otp) (`OTP_NOT_FOUND`, `OTP_EXPIRED`, `OTP_INVALID`, `OTP_LOCKED`), plus `404 USER_NOT_FOUND`.

---

### `POST /api/v1/auth/login` — Public

Email + password → a fresh access/refresh pair. Works whether or not the email has been verified.

**Request body**

| Field | Type |
|---|---|
| `email` | string |
| `password` | string |

**Response `200 OK`**

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresInSeconds": 900
}
```

**Errors**

| Status | Code | Meaning |
|---|---|---|
| 401 | `INVALID_CREDENTIALS` | Wrong email or password (never reveals which) |
| 403 | `ACCOUNT_DISABLED` | Admin has disabled this account |

---

### `POST /api/v1/auth/refresh` — Public

Trades a refresh token for a brand new access/refresh pair. The old refresh token is revoked immediately — a second call with the same one always fails.

**Request body**

| Field | Type |
|---|---|
| `refreshToken` | string |

**Response `200 OK`** — same shape as [Login](#post-apiv1authlogin).

**Errors**

| Status | Code | Meaning |
|---|---|---|
| 401 | `INVALID_REFRESH_TOKEN` | Expired, revoked, already-used, or malformed |
| 403 | `ACCOUNT_DISABLED` | Account was disabled since the token was issued |

---

### `POST /api/v1/auth/logout` — Public

Revokes one refresh token / session. No Bearer header required — the refresh token in the body is the credential. Always returns 204, even if the token was already revoked, so it's safe to call on sign-out unconditionally.

**Request body**

| Field | Type |
|---|---|
| `refreshToken` | string |

**Response:** `204 No Content`

---

### `POST /api/v1/auth/otp/request` — Public

Issues a fresh OTP for any purpose/channel — use this to resend a code, or to kick off phone verification. Rate-limited per target/purpose (5 requests/hour by default).

**Request body**

| Field | Type | Notes |
|---|---|---|
| `target` | string | Email or phone, matching the channel |
| `channel` | enum | `EMAIL` · `SMS` |
| `purpose` | enum | `REGISTER` · `LOGIN` · `VERIFY_PHONE` |

**Response:** `202 Accepted`, empty body. Assume the code was sent; don't reveal target existence either way.

**Errors**

| Status | Code | Meaning |
|---|---|---|
| 429 | `OTP_RATE_LIMITED` | Too many requests for this target this hour |

---

### `GET /oauth2/authorization/google` — Public

Google sign-in isn't a JSON call — it's a browser redirect. Point the "Continue with Google" button's `href` straight at this URL.

1. Browser navigates to `GET /oauth2/authorization/google`.
2. Google handles consent, then redirects back to the API's callback.
3. On success, the API creates or links the account (email must be Google-verified) and 302-redirects the browser to `OAUTH_FRONTEND_REDIRECT_URI` with tokens attached: `?accessToken=...&refreshToken=...`.

> Read the tokens off the query string on that landing page, store them, then strip them from the URL. There's no JSON response to parse for this flow.

---

## Users (self)

Everything here operates on the caller's own account, resolved from the Bearer token — there's no `{id}` in the path.

### `GET /api/v1/users/me` — JWT

Current profile and role — call this once after login/refresh to hydrate the app's user state.

**Response `200 OK`** — same shape as [the register response](#post-apiv1authregister).

---

### `PATCH /api/v1/users/me` — JWT

Updates the editable profile fields. Name, phone, and address change freely; city/neighborhood still validate against the same Maputo bairro list. Email, password, and role are *not* editable here.

**Request body**

| Field | Type | Notes |
|---|---|---|
| `firstName` | string | Required |
| `lastName` | string | Required |
| `phone` | string | Same MZ format as register |
| `address` | string | Required |
| `city` | string | Must be `"Maputo"` |
| `neighborhood` | string | Must match a seeded bairro |

**Response `200 OK`** — the updated profile.

---

## Role upgrade requests

How a `CUSTOMER` becomes a `MERCHANT`/`COURIER`/`MOBILITY_PARTNER` without an admin creating their account directly:

1. `POST /auth/register` with `requestedRole` set — account is created as `CUSTOMER`, `status: "PENDING"`, `requestedRole` set to what was requested.
2. Admin reviews pending applications: `GET /admin/users?status=PENDING`.
3. Admin approves (`POST /admin/users/{id}/approve`) — `role` flips to `requestedRole`, `requestedRole` clears, `status` returns to `"ACTIVE"`.
   Or rejects (`POST /admin/users/{id}/reject`) — `role` stays `CUSTOMER`, `requestedRole` clears, `status` becomes `"REJECTED"`. The user can still log in and use the app as a normal customer either way.

`status` and `enabled` are independent — a `DISABLED`-via-[enable/disable toggle](#patch-apiv1adminusersidenabled) account is `enabled: false` regardless of `status`, and a rejected applicant is still `enabled: true`.

---

## Admin

Requires `ROLE_ADMIN` — a non-admin token gets a 403 `ACCESS_DENIED`, no token gets a 401.

### `GET /api/v1/admin/users` — Admin

Search and paginate the user directory.

**Query params**

| Param | Type | Notes |
|---|---|---|
| `query` | string | Optional — matches email, first or last name (contains, case-insensitive) |
| `role` | string | Optional — filter to one role code (e.g. `MERCHANT`) |
| `status` | string | Optional — `PENDING` \| `ACTIVE` \| `REJECTED` |
| `page` | int | Default 0 |
| `size` | int | Default 20 |
| `sort` | string | e.g. `createdAt,desc` |

**Response `200 OK`**

```json
{
  "content": [ { "id": "…", "email": "…", "role": "CUSTOMER" } ],
  "page": 0,
  "size": 20,
  "totalElements": 128,
  "totalPages": 7
}
```

---

### `GET /api/v1/admin/users/{id}` — Admin

Full profile for one user by id.

**Errors**

| Status | Code | Meaning |
|---|---|---|
| 404 | `USER_NOT_FOUND` | No user with that id |

---

### `POST /api/v1/admin/users` — Admin

Directly onboards a staff account (Merchant, Courier, Admin, or Mobility Partner) without self-registration. No password is collected — the new user gets an emailed one-time link to set their own password (see [Set password](#post-apiv1authset-password)), the same way OTP emails work.

**Request body**

| Field | Type | Notes |
|---|---|---|
| `firstName` | string | Required |
| `lastName` | string | Required |
| `email` | string | Required, unique |
| `phone` | string | Same MZ format as register |
| `address` | string | Required |
| `city` | string | Must be `"Maputo"` |
| `neighborhood` | string | Must match a seeded bairro |
| `role` | string | One of `MERCHANT`, `COURIER`, `ADMIN`, `MOBILITY_PARTNER` — **not** `CUSTOMER` (self-registration owns that role) |

**Response `201 Created`** — a `UserProfileResponse` with `emailVerified: false` and `role` set to what was requested. Becomes usable once the invite link is completed.

**Errors**

| Status | Code | Meaning |
|---|---|---|
| 409 | `EMAIL_ALREADY_REGISTERED` | Email already exists |
| 400 | `ROLE_NOT_ALLOWED` | `role` was `CUSTOMER` — use `/auth/register` instead |
| 400 | `UNKNOWN_ROLE` | `role` doesn't match a seeded role |
| 400 | `VALIDATION_ERROR` | Bad city/neighborhood/phone/etc. |

---

### `PATCH /api/v1/admin/users/{id}` — Admin

Same field set and validation as [`PATCH /api/v1/users/me`](#patch-apiv1usersme), but lets an admin edit *any* user's profile — for fixing a phone number or bairro from the admin panel, for example. Does not touch email, password, or role.

**Request body:** identical to `PATCH /api/v1/users/me`.

**Response `200 OK`** — the updated profile.

---

### `PATCH /api/v1/admin/users/{id}/role` — Admin

Directly moves a user into `MERCHANT`, `COURIER`, `ADMIN`, or `MOBILITY_PARTNER` — bypassing the [role-request/approve flow](#role-upgrade-requests) entirely. Fires a `user.role_changed` event. If the user had a pending role request, it's cleared and `status` resets to `"ACTIVE"` (a direct assignment supersedes it).

**Request body**

| Field | Type | Notes |
|---|---|---|
| `roleCode` | string | One of the [role codes](#roles), case-insensitive |

**Errors**

| Status | Code | Meaning |
|---|---|---|
| 400 | `UNKNOWN_ROLE` | `roleCode` doesn't match a seeded role |

---

### `PATCH /api/v1/admin/users/{id}/enabled` — Admin

Suspend or restore an account. Disabling fires a `user.disabled` event and blocks future login/refresh attempts immediately.

**Query params**

| Param | Type |
|---|---|
| `enabled` | boolean |

**Response `200 OK`** — the updated profile.

---

### `POST /api/v1/admin/users/{id}/approve` — Admin

Approves a pending [role upgrade request](#role-upgrade-requests): grants `requestedRole` as the user's new `role`, clears `requestedRole`, sets `status: "ACTIVE"`. Fires a `user.role_changed` event.

**Request body:** none.

**Response `200 OK`** — the updated profile.

**Errors**

| Status | Code | Meaning |
|---|---|---|
| 404 | `USER_NOT_FOUND` | No user with that id |
| 409 | `USER_NOT_PENDING` | User has no pending role request (already decided, or never requested one) |

---

### `POST /api/v1/admin/users/{id}/reject` — Admin

Denies a pending role upgrade request. `role` is left unchanged (typically still `CUSTOMER`), `requestedRole` clears, `status` becomes `"REJECTED"`. Does **not** disable the account — the user keeps using the app as whatever role they already had.

**Request body**

| Field | Type | Notes |
|---|---|---|
| `reason` | string, optional | Free-text note, stored on the user record as `statusReason` (not currently exposed in `UserProfileResponse`, but kept server-side) |

**Response `200 OK`** — the updated profile.

**Errors**

| Status | Code | Meaning |
|---|---|---|
| 404 | `USER_NOT_FOUND` | No user with that id |
| 409 | `USER_NOT_PENDING` | User has no pending role request |

---

## Meta

### `GET /api/v1/meta/neighborhoods` — Public

Use this to populate the bairro dropdown on the registration and profile forms — don't hardcode the list client-side, it can grow.

**Query params**

| Param | Type | Notes |
|---|---|---|
| `city` | string | Default `"Maputo"` — the only supported city right now |

**Response `200 OK`**

```json
[
  { "city": "Maputo", "name": "Central" },
  { "city": "Maputo", "name": "Polana Cimento" },
  { "city": "Maputo", "name": "Alto Mae" },
  { "city": "Maputo", "name": "Sommerschield" }
]
```
*(~38 seeded bairros total)*

---

## Roles

One role per user for now. The `roles` claim on the access token is a single `ROLE_<CODE>` string — check it directly, or decode and switch on it for dashboard routing.

| Code | Name (PT) | Description |
|---|---|---|
| `CUSTOMER` | Cliente | Buys products. Default on self-registration. |
| `MERCHANT` | Comerciante | Manages store, stock, orders. |
| `COURIER` | Entregador | Accepts and completes deliveries. |
| `ADMIN` | Administrador | Platform operations. |
| `MOBILITY_PARTNER` | Parceiro de Mobilidade | Fleet / rent-lease-earn dashboards. |

---

## Data models

### `UserProfileResponse`

| Field | Type |
|---|---|
| `id` | uuid |
| `firstName` | string |
| `lastName` | string |
| `email` | string |
| `birthDate` | date |
| `phone` | string |
| `address` | string |
| `city` | string |
| `neighborhood` | string |
| `role` | string |
| `status` | string — `PENDING` \| `ACTIVE` \| `REJECTED` (see [Role upgrade requests](#role-upgrade-requests)) |
| `requestedRole` | string, nullable — set only while `status` is `PENDING` |
| `emailVerified` | boolean |
| `phoneVerified` | boolean |
| `enabled` | boolean |
| `createdAt` | timestamp |

### `TokenResponse`

| Field | Type |
|---|---|
| `accessToken` | string (JWT) |
| `refreshToken` | string (JWT) |
| `tokenType` | string — always `"Bearer"` |
| `expiresInSeconds` | number |

### `PageResponse<T>`

| Field | Type |
|---|---|
| `content` | T[] |
| `page` | number |
| `size` | number |
| `totalElements` | number |
| `totalPages` | number |

---

## Error format

Every non-2xx response — validation, auth, business-rule — shares this shape. Branch UI on `code`, show `message` as a fallback, never parse stack traces (there aren't any).

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "details": [
    "phone: phone must be a valid Mozambique mobile number"
  ],
  "timestamp": "2026-09-01T21:35:22.489Z"
}
```

> **401 vs 403, quickly:** **401** means "no valid token was presented" (missing, expired, malformed). **403** means "valid token, wrong role" — e.g. a Customer token hitting an Admin route.

Both of these now consistently return the standard envelope too — `401` as `{"code": "UNAUTHENTICATED", ...}`, `403` as `{"code": "ACCESS_DENIED", ...}` — even though they're raised by the security filter chain, not a controller.

---

*KONECTA Security Service · generated from the live controller contracts, not a spec doc — update this page if the endpoints move.*
