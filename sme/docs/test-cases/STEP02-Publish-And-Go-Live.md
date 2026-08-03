# Step 02 — Publish And Go Live: Test Cases

| Field | Value |
| --- | --- |
| Suite ID | `STEP02-PUBLISH` |
| Feature | Storefront publish / unpublish / history |
| Base path | `/api/v1/workspaces/{workspaceId}/storefront` |
| Auth | Bearer JWT (merchant / workspace owner) |
| Env used (live) | `https://sme-operations-gpgudcaud8bddgdu.canadacentral-01.azurewebsites.net` |
| Related unit tests | `WorkspaceServicePublishTest`, `WorkspaceControllerPublishTest`, `StorefrontPublishValidationTest`, `SlugGeneratorServiceTest` |

## APIs Under Test

| API ID | Method | Endpoint |
| --- | --- | --- |
| API-P01 | `POST` | `/{workspaceId}/storefront/publish` |
| API-P02 | `GET` | `/{workspaceId}/storefront/published` |
| API-P03 | `GET` | `/{workspaceId}/storefront/publish-history` |
| API-P04 | `GET` | `/{workspaceId}/storefront/publish-history/{snapshotId}` |
| API-P05 | `POST` | `/{workspaceId}/storefront/unpublish` |

## Shared Preconditions

1. Merchant account exists and is verified.
2. User owns a workspace with a storefront and draft config.
3. Template used by the draft is `AVAILABLE` (e.g. `classic-boutique` v1).
4. Client sends `Authorization: Bearer <accessToken>` unless the case is unauthenticated.
5. Response envelope is always:
   - success: `{ "success": true, "data": ..., "error": null, "timestamp": "..." }`
   - failure: `{ "success": false, "data": null, "error": { "code": "...", "message": "..." }, "timestamp": "..." }`

## Test Data (example)

| Key | Example |
| --- | --- |
| Email | `mbalimona716@gmail.com` |
| Workspace ID | `fe04b4b8-721c-48d8-8067-14a415cf6d5e` |
| Storefront ID | `213d9d77-22c9-4abb-87c6-c96903b1b3a9` |
| Public slug | `something-good` |
| Valid publish body | `{ "confirm": true, "notes": "Initial launch" }` |
| Valid draft minimum | `shopName`, `themeId` in supported themes, valid `sections` / unique page `slug`s |

---

## 1. Authentication & Ownership

### TC-S02-001 — Unauthenticated access is rejected

| Field | Detail |
| --- | --- |
| **ID** | `TC-S02-001` |
| **API** | API-P01 … API-P05 |
| **Priority** | P0 |
| **Type** | Negative / Security |
| **Preconditions** | No `Authorization` header |
| **Steps** | 1. Call any Step 02 endpoint without a token |
| **Expected** | `401` or `403`; request does not mutate data |
| **Live result** | `403` on `GET .../published` |

### TC-S02-002 — Non-owner cannot publish another workspace

| Field | Detail |
| --- | --- |
| **ID** | `TC-S02-002` |
| **API** | API-P01 |
| **Priority** | P0 |
| **Type** | Negative / Security |
| **Preconditions** | Authenticated as owner A |
| **Steps** | 1. `POST /workspaces/{foreignOrFakeId}/storefront/publish` with `{ "confirm": true }` |
| **Expected** | `404` + `WORKSPACE_NOT_FOUND` |
| **Live result** | Pass (`00000000-...0099` → `WORKSPACE_NOT_FOUND`) |

### TC-S02-003 — Non-owner cannot read published / history / unpublish

| Field | Detail |
| --- | --- |
| **ID** | `TC-S02-003` |
| **API** | API-P02, API-P03, API-P04, API-P05 |
| **Priority** | P0 |
| **Type** | Negative / Security |
| **Steps** | Call each endpoint with a workspace ID the user does not own |
| **Expected** | `404` + `WORKSPACE_NOT_FOUND` for each |

---

## 2. Publish — `POST .../publish` (API-P01)

### TC-S02-010 — Publish succeeds with confirm=true

| Field | Detail |
| --- | --- |
| **ID** | `TC-S02-010` |
| **API** | API-P01 |
| **Priority** | P0 |
| **Type** | Happy path |
| **Preconditions** | Valid draft; template available; user owns workspace |
| **Steps** | 1. `POST .../publish` body `{ "confirm": true, "notes": "Live API test publish" }` |
| **Expected** | `200`; `data.status = LIVE`; `publishedSnapshotId` set; `publishedAt` set; draft unchanged; immutable snapshot row created; `storefronts.published_snapshot_id` + `last_published_at` updated |
| **Live result** | Pass — snapshot `c94cafb7-...` |

### TC-S02-011 — Publish rejects confirm=false

| Field | Detail |
| --- | --- |
| **ID** | `TC-S02-011` |
| **API** | API-P01 |
| **Priority** | P0 |
| **Type** | Negative |
| **Steps** | `POST .../publish` `{ "confirm": false }` |
| **Expected** | `400` + `PUBLISH_CONFIRMATION_REQUIRED` |
| **Live result** | Pass |

### TC-S02-012 — Publish rejects missing confirm

| Field | Detail |
| --- | --- |
| **ID** | `TC-S02-012` |
| **API** | API-P01 |
| **Priority** | P0 |
| **Type** | Negative / Validation |
| **Steps** | `POST .../publish` `{}` |
| **Expected** | `400` + `VALIDATION_FAILED` (confirm required) |
| **Live result** | Pass |

### TC-S02-013 — Publish rejects empty / missing draft config

| Field | Detail |
| --- | --- |
| **ID** | `TC-S02-013` |
| **API** | API-P01 |
| **Priority** | P0 |
| **Type** | Negative |
| **Preconditions** | Storefront draft config null or `{}` |
| **Steps** | Publish with `confirm: true` |
| **Expected** | `404` + `STOREFRONT_DRAFT_NOT_FOUND` |
| **Covered by** | `WorkspaceServicePublishTest` |

### TC-S02-014 — Publish rejects invalid config (missing shopName)

| Field | Detail |
| --- | --- |
| **ID** | `TC-S02-014` |
| **API** | API-P01 |
| **Priority** | P0 |
| **Type** | Negative / Validation |
| **Preconditions** | Draft missing `shopName` |
| **Steps** | Publish with `confirm: true` |
| **Expected** | `400` + `INVALID_PUBLISH_CONFIG` |
| **Covered by** | `StorefrontPublishValidationTest`, controller mapping test |

### TC-S02-015 — Publish rejects unsupported themeId

| Field | Detail |
| --- | --- |
| **ID** | `TC-S02-015` |
| **API** | API-P01 |
| **Priority** | P1 |
| **Type** | Negative / Validation |
| **Preconditions** | Draft `themeId` not in template supported themes |
| **Expected** | `400` + `INVALID_PUBLISH_CONFIG` |

### TC-S02-016 — Publish rejects unsupported section type

| Field | Detail |
| --- | --- |
| **ID** | `TC-S02-016` |
| **API** | API-P01 |
| **Priority** | P1 |
| **Type** | Negative / Validation |
| **Preconditions** | Draft section `type` not supported by template |
| **Expected** | `400` + `INVALID_PUBLISH_CONFIG` |

### TC-S02-017 — Publish rejects duplicate page slugs

| Field | Detail |
| --- | --- |
| **ID** | `TC-S02-017` |
| **API** | API-P01 |
| **Priority** | P1 |
| **Type** | Negative / Validation |
| **Preconditions** | Two pages share the same `slug` |
| **Expected** | `400` + `INVALID_PUBLISH_CONFIG` |

### TC-S02-018 — Publish rejects disabled / coming-soon template

| Field | Detail |
| --- | --- |
| **ID** | `TC-S02-018` |
| **API** | API-P01 |
| **Priority** | P1 |
| **Type** | Negative |
| **Preconditions** | Draft template status is `DISABLED` or `COMING_SOON` |
| **Expected** | `422` + `TEMPLATE_DISABLED` |

### TC-S02-019 — Publish rejects missing template / version

| Field | Detail |
| --- | --- |
| **ID** | `TC-S02-019` |
| **API** | API-P01 |
| **Priority** | P1 |
| **Type** | Negative |
| **Preconditions** | `templateId` or version does not exist |
| **Expected** | `404` + `TEMPLATE_NOT_FOUND` |

### TC-S02-020 — Publish assigns public slug when missing

| Field | Detail |
| --- | --- |
| **ID** | `TC-S02-020` |
| **API** | API-P01 |
| **Priority** | P0 |
| **Type** | Happy path |
| **Preconditions** | `workspaces.public_slug` is null/blank |
| **Steps** | Publish successfully |
| **Expected** | Slug generated from workspace/business name; saved before finish; workspace becomes `LIVE` |
| **Covered by** | `WorkspaceServicePublishTest`, `SlugGeneratorServiceTest` |

### TC-S02-021 — Publish keeps existing public slug

| Field | Detail |
| --- | --- |
| **ID** | `TC-S02-021` |
| **API** | API-P01 |
| **Priority** | P1 |
| **Type** | Happy path |
| **Preconditions** | `public_slug` already set (e.g. `something-good`) |
| **Expected** | Slug unchanged after publish |
| **Live result** | Pass — slug remained `something-good` |

### TC-S02-022 — Publish conflict suffix when slug taken

| Field | Detail |
| --- | --- |
| **ID** | `TC-S02-022` |
| **API** | API-P01 |
| **Priority** | P1 |
| **Type** | Edge |
| **Preconditions** | Generated base slug already used by another workspace |
| **Expected** | Slug like `nkandu-fashion-8f3a`; on exhaustion `409` + `PUBLIC_SLUG_UNAVAILABLE` |

### TC-S02-023 — Publish creates immutable snapshot (does not mutate draft)

| Field | Detail |
| --- | --- |
| **ID** | `TC-S02-023` |
| **API** | API-P01 |
| **Priority** | P0 |
| **Type** | Happy path |
| **Steps** | 1. Note draft config 2. Publish 3. Compare draft vs snapshot |
| **Expected** | Snapshot is a deep copy; later draft edits do not change past snapshots |
| **Covered by** | `WorkspaceServicePublishTest` |

### TC-S02-024 — Re-publish creates a new history entry

| Field | Detail |
| --- | --- |
| **ID** | `TC-S02-024` |
| **API** | API-P01, API-P03 |
| **Priority** | P0 |
| **Type** | Happy path |
| **Steps** | Publish twice with different `notes` |
| **Expected** | Two distinct `publishedSnapshotId`s; history newest-first; latest pointer updated |
| **Live result** | Pass — e.g. `c94cafb7-...` then `27f446cc-...` |

### TC-S02-025 — Optional notes are stored on snapshot

| Field | Detail |
| --- | --- |
| **ID** | `TC-S02-025` |
| **API** | API-P01, API-P03 |
| **Priority** | P2 |
| **Type** | Happy path |
| **Steps** | Publish with `"notes": "Second live publish"` |
| **Expected** | History item includes the same notes |
| **Live result** | Pass |

---

## 3. Get Published — `GET .../published` (API-P02)

### TC-S02-030 — Returns latest published snapshot with full config

| Field | Detail |
| --- | --- |
| **ID** | `TC-S02-030` |
| **API** | API-P02 |
| **Priority** | P0 |
| **Type** | Happy path |
| **Preconditions** | At least one successful publish |
| **Expected** | `200`; includes `publishedSnapshotId`, `templateId`, `templateVersion`, `config`, `status`, `publicSlug`, `publishedAt`, `notes` |
| **Live result** | Pass |

### TC-S02-031 — Returns 404 when never published

| Field | Detail |
| --- | --- |
| **ID** | `TC-S02-031` |
| **API** | API-P02 |
| **Priority** | P0 |
| **Type** | Negative |
| **Preconditions** | `published_snapshot_id` is null |
| **Expected** | `404` + `PUBLISHED_STOREFRONT_NOT_FOUND` |
| **Covered by** | unit + controller tests |

### TC-S02-032 — Still readable after unpublish (merchant dashboard)

| Field | Detail |
| --- | --- |
| **ID** | `TC-S02-032` |
| **API** | API-P02 |
| **Priority** | P1 |
| **Type** | Edge |
| **Preconditions** | Published then unpublished; snapshot pointer retained |
| **Expected** | Merchant can still load last published snapshot; workspace `status = UNPUBLISHED` |
| **Note** | Public customer routes are out of Step 02 scope |

---

## 4. Publish History — `GET .../publish-history` (API-P03)

### TC-S02-040 — Returns metadata only, newest first

| Field | Detail |
| --- | --- |
| **ID** | `TC-S02-040` |
| **API** | API-P03 |
| **Priority** | P0 |
| **Type** | Happy path |
| **Expected** | `200` array of items with `snapshotId`, `templateId`, `templateVersion`, `configVersion`, `publishedByUserId`, `publishedAt`, `notes`; **no full `config`** |
| **Live result** | Pass (9+ snapshots after tests) |

### TC-S02-041 — Empty history returns empty array

| Field | Detail |
| --- | --- |
| **ID** | `TC-S02-041` |
| **API** | API-P03 |
| **Priority** | P1 |
| **Type** | Edge |
| **Preconditions** | Workspace never published |
| **Expected** | `200` + `data: []` |
| **Covered by** | controller/unit tests |

### TC-S02-042 — History preserved after unpublish

| Field | Detail |
| --- | --- |
| **ID** | `TC-S02-042` |
| **API** | API-P03, API-P05 |
| **Priority** | P0 |
| **Type** | Acceptance |
| **Steps** | Unpublish, then get history |
| **Expected** | Same snapshot rows still returned |
| **Live result** | Pass |

---

## 5. Get Snapshot — `GET .../publish-history/{snapshotId}` (API-P04)

### TC-S02-050 — Returns one snapshot with full config

| Field | Detail |
| --- | --- |
| **ID** | `TC-S02-050` |
| **API** | API-P04 |
| **Priority** | P0 |
| **Type** | Happy path |
| **Steps** | Take `snapshotId` from history; GET that path |
| **Expected** | `200` with full `config` and snapshot metadata |
| **Live result** | Pass |

### TC-S02-051 — Unknown snapshot returns 404

| Field | Detail |
| --- | --- |
| **ID** | `TC-S02-051` |
| **API** | API-P04 |
| **Priority** | P0 |
| **Type** | Negative |
| **Steps** | Use fake UUID `00000000-0000-0000-0000-000000000088` |
| **Expected** | `404` + `PUBLISHED_STOREFRONT_NOT_FOUND` |
| **Live result** | Pass |

### TC-S02-052 — Snapshot from another workspace is not accessible

| Field | Detail |
| --- | --- |
| **ID** | `TC-S02-052` |
| **API** | API-P04 |
| **Priority** | P0 |
| **Type** | Security |
| **Steps** | Request `{workspaceA}/publish-history/{snapshotOwnedByB}` |
| **Expected** | `404` + `PUBLISHED_STOREFRONT_NOT_FOUND` (lookup scoped by workspace) |

---

## 6. Unpublish — `POST .../unpublish` (API-P05)

### TC-S02-060 — Unpublish sets workspace to UNPUBLISHED

| Field | Detail |
| --- | --- |
| **ID** | `TC-S02-060` |
| **API** | API-P05 |
| **Priority** | P0 |
| **Type** | Happy path |
| **Preconditions** | Workspace is `LIVE` with a published snapshot |
| **Steps** | `POST .../unpublish` |
| **Expected** | `200`; `data.status = UNPUBLISHED`; `lastPublishedAt` returned |
| **Live result** | Pass |

### TC-S02-061 — Unpublish does not delete draft

| Field | Detail |
| --- | --- |
| **ID** | `TC-S02-061` |
| **API** | API-P05 |
| **Priority** | P0 |
| **Type** | Acceptance |
| **Steps** | Unpublish → `GET .../draft` |
| **Expected** | Draft still present and readable |
| **Live result** | Pass |

### TC-S02-062 — Unpublish does not delete publish history

| Field | Detail |
| --- | --- |
| **ID** | `TC-S02-062` |
| **API** | API-P05, API-P03 |
| **Priority** | P0 |
| **Type** | Acceptance |
| **Expected** | History list unchanged in content/count (no deletes) |
| **Live result** | Pass |

### TC-S02-063 — Unpublish when never published

| Field | Detail |
| --- | --- |
| **ID** | `TC-S02-063` |
| **API** | API-P05 |
| **Priority** | P1 |
| **Type** | Negative |
| **Preconditions** | No `published_snapshot_id` |
| **Expected** | `404` + `PUBLISHED_STOREFRONT_NOT_FOUND` |
| **Covered by** | unit + controller tests |

### TC-S02-064 — Republish after unpublish

| Field | Detail |
| --- | --- |
| **ID** | `TC-S02-064` |
| **API** | API-P05, API-P01 |
| **Priority** | P0 |
| **Type** | Happy path / Regression |
| **Steps** | Unpublish → publish again |
| **Expected** | New snapshot; status back to `LIVE` |
| **Live result** | Pass — final status `LIVE` |

---

## 7. Acceptance Criteria Traceability

| Acceptance criterion | Covered by |
| --- | --- |
| Merchant can publish current draft | `TC-S02-010` |
| Publishing creates immutable snapshot | `TC-S02-010`, `TC-S02-023` |
| Updates `storefronts.published_snapshot_id` | `TC-S02-010` |
| Marks workspace `live` | `TC-S02-010`, `TC-S02-064` |
| Merchant can load latest published snapshot | `TC-S02-030` |
| Merchant can view publish history | `TC-S02-040` |
| Merchant can unpublish | `TC-S02-060` |
| Unpublish does not delete draft or history | `TC-S02-061`, `TC-S02-062` |
| Invalid config fails with clear errors | `TC-S02-011`–`TC-S02-017` |

---

## 8. Execution Checklist (manual / curl)

```bash
BASE=https://sme-operations-gpgudcaud8bddgdu.canadacentral-01.azurewebsites.net
# 1) login → TOKEN
# 2) GET /api/v1/workspaces → WS_ID
# 3) Run cases in order: 001, 011, 012, 010, 030, 040, 050, 024, 060, 061, 062, 064, 002, 051
```

### Suggested run order

1. `TC-S02-001` auth denial  
2. `TC-S02-011`, `TC-S02-012` publish validation  
3. `TC-S02-010` publish success  
4. `TC-S02-030`, `TC-S02-040`, `TC-S02-050` reads  
5. `TC-S02-024` second publish  
6. `TC-S02-060`–`TC-S02-062` unpublish + preservation  
7. `TC-S02-064` republish  
8. `TC-S02-002`, `TC-S02-051` not-found paths  

---

## 9. Automated Coverage Map

| Test case IDs | Automated where |
| --- | --- |
| `TC-S02-010`–`025` (service behaviour) | `WorkspaceServicePublishTest` |
| HTTP status + error codes | `WorkspaceControllerPublishTest` |
| Config validation `014`–`017` | `StorefrontPublishValidationTest` |
| Public slug `020`–`022` | `SlugGeneratorServiceTest` + service tests |
| Live smoke `001,002,010–012,024,030,040,050,051,060–064` | Manual curl suite (executed 2026-08-03) |
