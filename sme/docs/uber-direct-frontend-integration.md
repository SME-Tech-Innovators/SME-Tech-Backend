# Uber Direct — Frontend Integration Guide

> **Environment:** Sandbox (`https://sandbox-api.uber.com`)  
> **Base API URL:** `https://<your-backend>/api/v1`  
> **Auth:** All merchant endpoints require `Authorization: Bearer <jwt>`.  
> Public checkout endpoints require no auth.

---

## Overview

The Uber Direct integration has two sides:

| Side | Who | What |
|---|---|---|
| **Merchant (dashboard)** | Business owner | Enables Uber Direct, sets pickup address |
| **Customer (storefront)** | Shopper | Sees delivery fee + ETA at checkout, selects Uber Direct |

The flow at checkout is:

```
1. Customer fills delivery address
2. Frontend calls POST /checkout/delivery-options  →  gets fee + quoteId
3. Customer confirms order (POST /checkout)
4. Customer pays  (POST /checkout/{orderId}/pay)
5. Merchant dispatches courier from dashboard
```

---

## 1 · Merchant Dashboard — Delivery Settings

### 1.1 Get current settings

```
GET /api/v1/workspaces/{workspaceId}/delivery-settings
Authorization: Bearer <jwt>
```

**Response `200`:**
```json
{
  "data": {
    "workspaceId": "uuid",
    "uberDirectEnabled": false,
    "uberDirectAvailable": false,
    "pickupAddressLine1": null,
    "pickupAddressLine2": null,
    "pickupCity": null,
    "pickupProvince": null,
    "pickupPostalCode": null,
    "pickupCountry": "ZA",
    "pickupLatitude": null,
    "pickupLongitude": null,
    "pickupContactName": null,
    "pickupContactPhone": null,
    "updatedAt": null
  }
}
```

`uberDirectAvailable` is `true` only when the merchant has enabled it **and** the platform credentials are configured. Render the "Uber Direct active" badge based on this field, not `uberDirectEnabled`.

---

### 1.2 Enable Uber Direct & set pickup address

```
PUT /api/v1/workspaces/{workspaceId}/delivery-settings
Authorization: Bearer <jwt>
Content-Type: application/json
```

**Request body:**
```json
{
  "uberDirectEnabled": true,
  "pickupAddressLine1": "12 Main Street",
  "pickupAddressLine2": "Unit 4",
  "pickupCity": "Cape Town",
  "pickupProvince": "Western Cape",
  "pickupPostalCode": "8001",
  "pickupCountry": "ZA",
  "pickupLatitude": -33.9249,
  "pickupLongitude": 18.4241,
  "pickupContactName": "Store Manager",
  "pickupContactPhone": "+27821234567"
}
```

**Required fields:** `uberDirectEnabled`, `pickupAddressLine1`, `pickupCity`, `pickupCountry`.  
`pickupLatitude` / `pickupLongitude` are strongly recommended — Uber Direct uses them for routing accuracy.

**Response `200`:** same shape as GET above, with updated values.

---

### 1.3 Dispatch an Uber Direct courier (after payment)

Once a customer's order is paid, the merchant books a courier from the order detail page.

```
POST /api/v1/workspaces/{workspaceId}/delivery-settings/uber-direct/orders/{orderId}/book?quoteId={quoteId}
Authorization: Bearer <jwt>
```

`quoteId` is optional but **recommended** — pass the value the customer received at checkout to guarantee the same price.

**Response `200`:** raw Uber Direct delivery object, e.g.:
```json
{
  "data": {
    "id": "del_abc123",
    "status": "pending",
    "tracking_url": "https://www.uber.com/track/del_abc123",
    "courier": { "name": "John D.", "phone": "+27..." },
    "pickup_eta": 12,
    "dropoff_eta": 35
  }
}
```

Persist `tracking_url` to show the customer a live tracker link.

---

### 1.4 Refresh delivery status

```
POST /api/v1/workspaces/{workspaceId}/delivery-settings/uber-direct/orders/{orderId}/refresh-status
Authorization: Bearer <jwt>
```

Call this to sync the latest courier status from Uber Direct into your local `Delivery` record. Returns the same raw Uber Direct delivery object.

**Uber Direct status → platform status mapping:**

| Uber Direct `status` | Platform `DeliveryStatus` |
|---|---|
| `pending` | `PENDING` |
| `pickup` | `ASSIGNED` |
| `pickup_complete` | `DISPATCHED` |
| `dropoff` | `IN_TRANSIT` |
| `delivered` | `DELIVERED` |
| `cancelled` | `CANCELLED` |
| `returned` | `FAILED` |

---

## 2 · Customer Checkout — Delivery Options

### 2.1 Get delivery quote

Call this **before** the customer submits the order, once they've entered their delivery address.

```
POST /api/v1/public/storefronts/{storeSlug}/checkout/delivery-options
Content-Type: application/json
```

**Request body:**
```json
{
  "dropoffAddressLine1": "45 Long Street",
  "dropoffAddressLine2": "",
  "dropoffCity": "Cape Town",
  "dropoffProvince": "Western Cape",
  "dropoffPostalCode": "8001",
  "dropoffCountry": "ZA",
  "dropoffLatitude": -33.9258,
  "dropoffLongitude": 18.4232,
  "manifestTotalValueCents": 45000
}
```

`manifestTotalValueCents` is the cart total in cents (e.g. R450.00 → `45000`). Used by Uber Direct for value-based pricing.

**Response `200`:**
```json
{
  "data": {
    "storeSlug": "my-store",
    "options": [
      {
        "quoteId": "quote_xyz789",
        "providerName": "Uber Direct",
        "estimatedDeliveryTime": "12–35 min",
        "fee": 49.99,
        "currency": "ZAR",
        "expiresAt": "2026-09-24T10:30:00Z",
        "available": true,
        "unavailableReason": null
      }
    ]
  }
}
```

If `available` is `false`, show `unavailableReason` to the customer and hide Uber Direct as an option (don't block checkout — they can still proceed without delivery, or use another method).

If `options` is empty the store hasn't enabled any delivery provider — don't show a delivery section.

---

### 2.2 Rendering the delivery options UI

```
if (options.length === 0) {
  // No delivery configured — hide section or show "pickup only"
}

options.forEach(option => {
  if (!option.available) {
    // Show as disabled/greyed out with option.unavailableReason
    return;
  }
  // Render: "Uber Direct  •  12–35 min  •  R49.99"
  // Store option.quoteId to pass to booking step
})
```

---

### 2.3 Passing quoteId to checkout

When the customer submits their order, include the selected `quoteId` in your checkout payload (add it to your existing `CheckoutRequest` or store it in session for the merchant to use at dispatch). The merchant passes it to the `book` endpoint to lock in that price.

---

## 3 · Error handling

| HTTP status | Meaning | Suggested UI |
|---|---|---|
| `200` with `available: false` | No couriers in area | Show warning, don't block checkout |
| `400` | Pickup address missing or order already has delivery | Show error message |
| `502` | Uber Direct upstream error | Show "Delivery unavailable, try again" |
| `503` | Platform credentials not configured | Show "Delivery unavailable" |

All error responses follow the standard `{ "message": "...", "status": 4xx }` shape.

---

## 4 · Local development — setup checklist

Before hitting any endpoint locally:

- [ ] App is running (`./mvnw spring-boot:run` with `local` profile active)
- [ ] `.env` contains all four Uber Direct vars (see below)
- [ ] A workspace exists and you have a valid merchant JWT (login via `POST /api/v1/auth/login`)
- [ ] A published storefront exists for the `{storeSlug}` you are testing
- [ ] At least one `ACTIVE` product exists in the store (needed for `manifestTotalValueCents` to be meaningful)
- [ ] Swagger UI available at [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) — all endpoints are documented there

**Verify Uber Direct is live** — check startup logs:
```
########## UBER DIRECT CONFIG ##########
enabled=true
mode=sandbox
baseUrl=https://sandbox-api.uber.com
clientIdPresent=true
clientSecretPresent=true
configured=true
########################################
```
If `configured=false`, a credential is missing — check the `.env` values.

---

## 5 · Environment variables (for local dev)

```
UBER_DIRECT_ENABLED=true
UBER_DIRECT_CLIENT_ID=<from Uber Developer Dashboard>
UBER_DIRECT_CLIENT_SECRET=<from Uber Developer Dashboard>
UBER_DIRECT_CUSTOMER_ID=<from Uber Direct dashboard → Settings>
UBER_DIRECT_MODE=sandbox
UBER_DIRECT_BASE_URL=https://sandbox-api.uber.com
UBER_DIRECT_TOKEN_URL=https://auth.uber.com/oauth/v2/token
```

Sandbox base URL: `https://sandbox-api.uber.com`  
Production base URL: `https://api.uber.com`

Switch by updating `UBER_DIRECT_BASE_URL` — no code changes required.

---

## 6 · Azure deployment — secrets to add

In **Azure App Service → Configuration → Application Settings**, add these for each app service that handles deliveries:

| Setting name | Sandbox value | Production value |
|---|---|---|
| `UBER_DIRECT_ENABLED` | `true` | `true` |
| `UBER_DIRECT_CLIENT_ID` | your sandbox client id | your production client id |
| `UBER_DIRECT_CLIENT_SECRET` | your sandbox client secret | your production client secret |
| `UBER_DIRECT_CUSTOMER_ID` | your sandbox customer id | your production customer id |
| `UBER_DIRECT_MODE` | `sandbox` | `production` |
| `UBER_DIRECT_BASE_URL` | `https://sandbox-api.uber.com` | `https://api.uber.com` |
| `UBER_DIRECT_TOKEN_URL` | `https://auth.uber.com/oauth/v2/token` | `https://auth.uber.com/oauth/v2/token` |

Switching to production = update the three credential values + change `UBER_DIRECT_BASE_URL` to `https://api.uber.com` + set `UBER_DIRECT_MODE=production`. No code changes required.
