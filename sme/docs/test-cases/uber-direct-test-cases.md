# Uber Direct — Test Cases

> **Environment:** Sandbox  
> **Prerequisite:** App running locally with `UBER_DIRECT_ENABLED=true` and valid sandbox credentials in `.env`.  
> **Tools:** Postman, curl, or the Swagger UI at `/swagger-ui.html`.

---

## Setup

1. Register a business, create a workspace, and note the `workspaceId`.
2. Log in and keep the JWT for all merchant endpoints.
3. Create a published storefront and note the `storeSlug`.
4. Add at least one product to the store.

---

## TC-UD-01 · Get delivery settings (no settings yet)

| | |
|---|---|
| **Method** | `GET /api/v1/workspaces/{workspaceId}/delivery-settings` |
| **Auth** | Bearer JWT |
| **Expected status** | `200` |

**Expected response:**
```json
{
  "data": {
    "uberDirectEnabled": false,
    "uberDirectAvailable": false,
    "pickupAddressLine1": null
  }
}
```

**Pass criteria:** Returns default disabled state without error.

---

## TC-UD-02 · Enable Uber Direct — missing required fields

| | |
|---|---|
| **Method** | `PUT /api/v1/workspaces/{workspaceId}/delivery-settings` |
| **Auth** | Bearer JWT |

**Request body:**
```json
{
  "uberDirectEnabled": true
}
```

**Expected status:** `400`  
**Pass criteria:** Validation error returned for missing `pickupAddressLine1`, `pickupCity`, `pickupCountry`.

---

## TC-UD-03 · Enable Uber Direct — valid request

| | |
|---|---|
| **Method** | `PUT /api/v1/workspaces/{workspaceId}/delivery-settings` |
| **Auth** | Bearer JWT |

**Request body:**
```json
{
  "uberDirectEnabled": true,
  "pickupAddressLine1": "12 Main Street",
  "pickupCity": "Cape Town",
  "pickupProvince": "Western Cape",
  "pickupPostalCode": "8001",
  "pickupCountry": "ZA",
  "pickupLatitude": -33.9249,
  "pickupLongitude": 18.4241,
  "pickupContactName": "Test Store",
  "pickupContactPhone": "+27821234567"
}
```

**Expected status:** `200`  
**Pass criteria:**
- `uberDirectEnabled: true` in response
- `uberDirectAvailable: true` (platform credentials configured)
- Row created in `workspace_delivery_settings` table

---

## TC-UD-04 · Get delivery settings after update

| | |
|---|---|
| **Method** | `GET /api/v1/workspaces/{workspaceId}/delivery-settings` |
| **Auth** | Bearer JWT |
| **Expected status** | `200` |

**Pass criteria:** Returns values saved in TC-UD-03, `updatedAt` is populated.

---

## TC-UD-05 · Get checkout delivery options — valid address

| | |
|---|---|
| **Method** | `POST /api/v1/public/storefronts/{storeSlug}/checkout/delivery-options` |
| **Auth** | None |

**Request body:**
```json
{
  "dropoffAddressLine1": "45 Long Street",
  "dropoffCity": "Cape Town",
  "dropoffCountry": "ZA",
  "dropoffLatitude": -33.9258,
  "dropoffLongitude": 18.4232,
  "manifestTotalValueCents": 25000
}
```

**Expected status:** `200`  
**Pass criteria:**
- `options` array has at least one entry
- First option has `providerName: "Uber Direct"`
- `fee` is a positive decimal
- `quoteId` is a non-empty string
- `available: true`
- `estimatedDeliveryTime` is set (e.g. `"12–35 min"`)

---

## TC-UD-06 · Get checkout delivery options — store with Uber Direct disabled

Disable Uber Direct first: `PUT` with `"uberDirectEnabled": false`.

**Same request as TC-UD-05.**

**Expected status:** `200`  
**Pass criteria:** `options` array is empty — no error thrown.

---

## TC-UD-07 · Get checkout delivery options — unknown store slug

```
POST /api/v1/public/storefronts/nonexistent-slug/checkout/delivery-options
```

**Request body:** same as TC-UD-05.

**Expected status:** `404`  
**Pass criteria:** Error response with `"Store not found"` message.

---

## TC-UD-08 · Get checkout delivery options — missing required fields

**Request body:**
```json
{
  "dropoffCity": "Cape Town",
  "dropoffCountry": "ZA",
  "manifestTotalValueCents": 10000
}
```

**Expected status:** `400`  
**Pass criteria:** Validation error for missing `dropoffAddressLine1`.

---

## TC-UD-09 · Get checkout delivery options — wrong workspace owner

Try GET delivery-settings with a JWT belonging to a different user.

**Expected status:** `404`  
**Pass criteria:** Workspace not found / access denied.

---

## TC-UD-10 · Book Uber Direct delivery — successful

**Prerequisite:** Complete a checkout + payment for an order in the test store.

```
POST /api/v1/workspaces/{workspaceId}/delivery-settings/uber-direct/orders/{orderId}/book?quoteId={quoteId}
Authorization: Bearer <merchant JWT>
```

Use the `quoteId` from TC-UD-05.

**Expected status:** `200`  
**Pass criteria:**
- Response contains `id` (Uber delivery ID) and `status: "pending"`
- `tracking_url` is present
- A `Delivery` row is created in the database with `provider = 'uber_direct'`
- A `DeliveryEvent` row is created with `status = PENDING`

---

## TC-UD-11 · Book Uber Direct delivery — duplicate booking rejected

Call the book endpoint a second time for the same `orderId`.

**Expected status:** `400`  
**Pass criteria:** Error `"A delivery already exists for this order"`.

---

## TC-UD-12 · Refresh delivery status

```
POST /api/v1/workspaces/{workspaceId}/delivery-settings/uber-direct/orders/{orderId}/refresh-status
Authorization: Bearer <merchant JWT>
```

**Expected status:** `200`  
**Pass criteria:**
- Response contains current Uber Direct `status`
- If status changed, a new `DeliveryEvent` row is inserted
- `Delivery.status` is updated in the database

---

## TC-UD-13 · Platform credentials not configured

Set `UBER_DIRECT_ENABLED=false` (or clear the client ID) and restart.

Call `POST /checkout/delivery-options`.

**Expected status:** `200`  
**Pass criteria:** `options` is empty — no 5xx errors.

Call `POST /uber-direct/orders/{orderId}/book`.

**Expected status:** `503`  
**Pass criteria:** Error `"Uber Direct is not configured on this platform"`.

---

## TC-UD-14 · Sandbox token caching

1. Make two consecutive calls to `POST /checkout/delivery-options`.
2. Check application logs.

**Pass criteria:** Only one `"UberDirect: fetching new access token"` log line — second call reuses the cached token.

---

## Regression checklist

After merging the Uber Direct feature, confirm existing flows still work:

- [ ] Cart create / add item / remove item
- [ ] Checkout (place order without delivery)
- [ ] Payment initialisation + verification
- [ ] Order lookup by email + order number
- [ ] Bob Go shipping quote (`POST /shipping/quote`)
- [ ] Order shipping status (`GET /orders/{id}/shipping`)
