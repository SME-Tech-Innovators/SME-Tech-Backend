# Customer order access and cancellation requests

The storefront page `/s/{storeSlug}/orders/track` now emails a secure access link. The footer of each storefront template links to this page. Enter the order number and checkout email; incorrect details display “Incorrect order number or email”. Opening the email link displays the order, delivery tracking and cancellation eligibility.

## Public API

All paths below are relative to `/api/v1/public/storefronts/{storeSlug}/orders`.

| Method | Path | Input |
| --- | --- | --- |
| POST | `/access-link` | `{ "orderNumber": "ORD-…", "email": "buyer@example.com" }` |
| GET | `/{orderId}/customer-access` | `X-Order-Access-Token` header |
| GET | `/{orderId}/cancellation-request` | `X-Order-Access-Token` header |
| POST | `/{orderId}/cancellation-request` | Same header; `{ "reason": "Changed my mind" }` |

The access-link endpoint returns HTTP 202 for matching details, or HTTP 404 with `ORDER_NOT_FOUND` and “Incorrect order number or email” for a mismatch. It does not distinguish which of the two fields is wrong. Links expire after 24 hours. Only a SHA-256 hash of the random 256-bit token is stored. A new link revokes the previous link; resends within one minute are suppressed. Access-link and cancellation submissions use the existing per-IP order lookup limit. The token is placed in the email URL fragment and passed to the API in a header, not an API query parameter. Tokens are not included in checkout, lookup or merchant order responses.

Set `FRONTEND_BASE_URL` (or `FRONTEND_URL`) to the frontend receiving the link. AWS SES must be configured to send to the buyer. No email is sent by the automated tests.

## Merchant API

Paths relative to `/api/v1/workspaces/{workspaceId}/orders/{orderId}`; merchant bearer authentication and workspace ownership are required.

- GET `/cancellation-request`
- POST `/cancellation-request/review` with `{ "decision": "approve", "note": "Optional response" }` or `decision: "reject"`.

Status response: `status` (`none`, `requested`, `approved`, `rejected`), `reason`, `reviewNote`, `requestedAt`, `reviewedAt`, `canRequest`, `unavailableReason`. Order list/detail DTOs also expose `cancellationRequestStatus` for the dashboard badge.

## Behaviour

- Initial scope: paid orders in `PAID` or `PROCESSING`, before dispatch. Bob Go shipments must be `CREATED` with a tracking reference. Other cases direct the buyer to contact the store.
- Customer requests do not change order, shipment, stock or payment status. Duplicate submissions return the original request; a declined request cannot be resubmitted.
- The merchant can review requests in order details. Approval rechecks eligibility under an order lock. Rejection preserves the order and delivery.
- Bob Go approval invokes the existing cancellation workflow. `approved` means the merchant accepted the request, not that the carrier confirmed it. Delivery remains the source of cancellation confirmation. If approval is recorded but the process stops before invoking Bob Go, the authenticated merchant cancellation API can initiate cancellation; refresh reconciles ambiguous provider outcomes.
- Non-Bob Go approval cancels the order and uses the existing idempotent inventory restock. Neither path issues a refund. Refunds remain a separate merchant action.
- Existing return management remains separate. This change does not add a customer return-request workflow.
- The existing legacy public order endpoints are retained for checkout compatibility; they do not grant a cancellation token.

## Storage and verification

New nullable columns on `orders` store the access hash/expiry/send time and cancellation status/reason/review/timestamps. Local startup applies them through the existing Hibernate `ddl-auto: update`. A SQL migration is provided for environments that manage schema explicitly.

Tests cover wrong/expired/cross-order/cross-store tokens, link rotation, resend throttling, ineligible orders, duplicate submissions and decisions, merchant ownership, provider uncertainty, no automatic refunds, request-only behaviour and frontend credential transport.
