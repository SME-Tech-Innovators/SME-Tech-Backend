# SME Operations Automation System

A multi-tenant SaaS platform for SME owners, automating business registration, online ordering, and customer-facing storefronts.

---

## Prerequisites

- Java 21
- Maven (wrapper included — use `./mvnw`)
- PostgreSQL (Aiven cloud instance — see configuration below)
- AWS account with SES enabled and a verified sender address

---

## Environment Variables

All secrets are injected via environment variables. **Never hardcode credentials in `application.yaml`.** Each variable has a fallback default for local development, but **production deployments must override all of them**.

| Variable                | Description                                      | Default (local dev only)                                      |
|-------------------------|--------------------------------------------------|---------------------------------------------------------------|
| `DB_URL`                | Full JDBC connection URL (with SSL)              | Aiven PostgreSQL (`smetech` DB, port 12667, `sslmode=require`) |
| `DB_USERNAME`           | PostgreSQL username                              | `avnadmin`                                                    |
| `DB_PASSWORD`           | PostgreSQL password                              | `changeme` — **must be overridden**                           |
| `AWS_ACCESS_KEY_ID`     | AWS IAM access key for SES authentication        | — **required**                                                |
| `AWS_SECRET_ACCESS_KEY` | AWS IAM secret key for SES authentication        | — **required**                                                |
| `AWS_REGION`            | AWS region where SES is enabled (e.g. `us-east-1`) | — **required**                                              |
| `APP_EMAIL_FROM`        | Verified SES sender address (e.g. `noreply@yourdomain.com`) | — **required**                              |
| `FRONTEND_URL`          | Frontend base URL used for post-verification redirect and public storefront links | `https://sme-operations.netlify.app` |
| `JWT_SECRET`            | Hex-encoded 256-bit secret for JWT signing       | `404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970` — **hardcoded fallback, must be overridden in production** |
| `APP_BASE_URL`          | Backend base URL used in email verification links | `https://sme-operations-dza7e5czhdggexfh.canadacentral-01.azurewebsites.net` |
| `APP_DOMAIN`            | Domain used for public storefront links          | `localhost:8080`                                              |
| `CORS_ALLOWED_ORIGINS`  | Comma-separated list of allowed CORS origins | `http://localhost:8080,http://localhost:5173,https://sme-operations.netlify.app,https://sme-operations-dza7e5czhdggexfh.canadacentral-01.azurewebsites.net/` |

### Setting environment variables

**Linux / macOS (bash/zsh):**
```bash
export DB_URL="jdbc:postgresql://pg-23b34967-sihlentshangase06-6d21.b.aivencloud.com:12667/smetech?sslmode=require"
export DB_USERNAME=avnadmin
export DB_PASSWORD=your-db-password
export AWS_ACCESS_KEY_ID=your-aws-access-key
export AWS_SECRET_ACCESS_KEY=your-aws-secret-key
export AWS_REGION=us-east-1
export APP_EMAIL_FROM=noreply@yourdomain.com
export FRONTEND_URL="https://sme-operations.netlify.app"
export JWT_SECRET=your-hex-encoded-secret
export APP_BASE_URL="https://sme-operations-dza7e5czhdggexfh.canadacentral-01.azurewebsites.net"
export APP_DOMAIN="sme-operations-dza7e5czhdggexfh.canadacentral-01.azurewebsites.net"
export CORS_ALLOWED_ORIGINS="http://localhost:8080,http://localhost:5173,https://sme-operations.netlify.app"
```

**Windows (PowerShell):**
```powershell
$env:DB_URL="jdbc:postgresql://pg-23b34967-sihlentshangase06-6d21.b.aivencloud.com:12667/smetech?sslmode=require"
$env:DB_USERNAME="avnadmin"
$env:DB_PASSWORD="your-db-password"
$env:AWS_ACCESS_KEY_ID="your-aws-access-key"
$env:AWS_SECRET_ACCESS_KEY="your-aws-secret-key"
$env:AWS_REGION="us-east-1"
$env:APP_EMAIL_FROM="noreply@yourdomain.com"
$env:FRONTEND_URL="https://sme-operations.netlify.app"
$env:JWT_SECRET="your-hex-encoded-secret"
$env:APP_BASE_URL="https://sme-operations-dza7e5czhdggexfh.canadacentral-01.azurewebsites.net"
$env:APP_DOMAIN="sme-operations-dza7e5czhdggexfh.canadacentral-01.azurewebsites.net"
$env:CORS_ALLOWED_ORIGINS="http://localhost:8080,http://localhost:5173,https://sme-operations.netlify.app"
```

**Using `application-local.yaml` (recommended for local dev):**

Create `sme/src/main/resources/application-local.yaml` (already in `.gitignore` — never commit this file). It overrides only the values that differ from the production defaults, keeping your IDE run config clean.

```yaml
# Local development overrides — gitignored, never commit this file
# Activate with: spring.profiles.active=local (set in IntelliJ run config)

spring:
  datasource:
    username: your-db-username
    password: your-db-password

app:
  base-url: http://localhost:8080
  domain: localhost
  frontend-url: http://localhost:3000
  email:
    from: your-verified-ses-address@yourdomain.com
  aws:
    region: us-east-1
  cors:
    allowed-origins: http://localhost:3000
```

Activate the `local` profile in your IntelliJ run configuration:
- **VM options**: `-Dspring.profiles.active=local`
- **OR** set the environment variable `SPRING_PROFILES_ACTIVE=local`

AWS credentials (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`) still need to be set as environment variables — they are intentionally not in this file to prevent accidental exposure.

**Using a `.env` file (alternative for local dev):**

Create `sme/.env` (already in `.gitignore`):
```
DB_URL=jdbc:postgresql://pg-23b34967-sihlentshangase06-6d21.b.aivencloud.com:12667/smetech?sslmode=require
DB_USERNAME=avnadmin
DB_PASSWORD=your-db-password
AWS_ACCESS_KEY_ID=your-aws-access-key
AWS_SECRET_ACCESS_KEY=your-aws-secret-key
AWS_REGION=us-east-1
APP_EMAIL_FROM=noreply@yourdomain.com
FRONTEND_URL=https://sme-operations.netlify.app
JWT_SECRET=your-hex-encoded-secret
APP_BASE_URL=https://sme-operations-dza7e5czhdggexfh.canadacentral-01.azurewebsites.net
APP_DOMAIN=sme-operations-dza7e5czhdggexfh.canadacentral-01.azurewebsites.net
CORS_ALLOWED_ORIGINS=http://localhost:8080,http://localhost:5173,https://sme-operations.netlify.app
```

Then load it before running:
```bash
export $(cat .env | xargs)
./mvnw spring-boot:run
```

---

## CORS

CORS is configured at the application level via `WebMvcConfig`. Allowed origins are controlled by the `CORS_ALLOWED_ORIGINS` environment variable.

| Variable               | Description                          | Default                                                                                                                                 |
|------------------------|--------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------|
| `CORS_ALLOWED_ORIGINS` | Comma-separated list of allowed origins | `http://localhost:8080,http://localhost:5173,https://sme-operations.netlify.app,https://sme-operations-dza7e5czhdggexfh.canadacentral-01.azurewebsites.net/` |

The following methods are permitted across all `/api/**` routes: `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS`.

To override allowed origins for a specific environment, set the variable before starting the application:

**Linux / macOS:**
```bash
export CORS_ALLOWED_ORIGINS="https://your-frontend.com,https://another-origin.com"
```

**Windows (PowerShell):**
```powershell
$env:CORS_ALLOWED_ORIGINS="https://your-frontend.com,https://another-origin.com"
```

> **Note:** If you are deploying behind Azure App Service, set `CORS_ALLOWED_ORIGINS` in **Configuration → Application Settings** rather than managing CORS in the Azure portal's CORS blade — the application handles it directly.

---

## Email (AWS SES)

Email is sent via AWS Simple Email Service (SES) using async delivery with retry logic (3 attempts, exponential backoff: 1s → 2s → 4s). Both plain-text and HTML body parts are included in every outbound message.

| Setting              | Value                                      |
|----------------------|--------------------------------------------|
| Transport            | AWS SES SDK v2 (HTTPS)                     |
| Credential provider  | `StaticCredentialsProvider` (env vars only) |
| Region               | Configured via `AWS_REGION` env var        |
| Sender address       | Configured via `APP_EMAIL_FROM` env var    |
| From display name    | `SME Operations <{APP_EMAIL_FROM}>`        |
| Retry attempts       | 3                                          |
| Backoff delays       | 1 s → 2 s → 4 s                            |

Credentials are read exclusively from `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` environment variables — no EC2 instance metadata or ECS task roles are used, making this compatible with Azure App Service hosting.

The sender address must be verified in AWS SES before emails will be delivered. In SES sandbox mode, recipient addresses must also be verified.

> The test profile mocks `SesClient` — no real AWS credentials are needed to run tests.

---

## Database

- **Provider**: Azure PostgreSQL (Flexible Server)
- **Host**: `learnwiselydb.postgres.database.azure.com:5432`
- **Database**: `sme_operations_db`
- **SSL**: Not enforced via connection string — configure SSL at the server or driver level if required

Schema is managed by Hibernate (`ddl-auto: update`). No manual migrations needed for development.

> **Note:** The `DB_URL` environment variable should be set to the full JDBC connection string. If your database requires SSL, append `?sslmode=require` to the URL.

> The test profile uses H2 in-memory (`jdbc:h2:mem:testdb`) — no external DB needed to run tests.

---

## Running the Application

```bash
# Run (requires environment variables set)
./mvnw spring-boot:run

# Build (skip tests)
./mvnw clean package -DskipTests

# Run tests (uses H2 in-memory DB — no env vars needed)
./mvnw test
```

---

## Deployment

Two CI/CD pipelines deploy to separate Azure App Service instances. Each pipeline triggers on a different branch.

---

### Pipeline 1 — Docker-based deploy (`sme-tech`)

**Workflow file:** `.github/workflows/main_sme-tech.yml`  
**Trigger branch:** `main`  
**Target app:** `sme-tech` (Production slot)

Builds a Docker image, pushes it to Docker Hub, then deploys it to Azure App Service.

| Job | Step | Description |
|-----|------|-------------|
| `build` | Checkout | Checks out the repository |
| | Docker Buildx | Sets up multi-platform build support |
| | Docker Hub login | Authenticates with Docker Hub using `DOCKERHUB_USERNAME` / `DOCKERHUB_TOKEN` secrets |
| | Build & push image | Builds from `sme/Dockerfile`; pushes `latest` and commit-SHA tags to Docker Hub |
| `deploy` | Azure login | Authenticates via OIDC (Workload Identity Federation) |
| | Deploy to App Service | Deploys the image to the `sme-tech` Production slot |

**Docker images** are published to Docker Hub as:
```
<DOCKERHUB_USERNAME>/sme-tech-backend:<commit-sha>
<DOCKERHUB_USERNAME>/sme-tech-backend:latest
```

Deployments use the commit SHA tag (not `latest`) for traceability.

**Required secrets:**

| Secret | Description |
|--------|-------------|
| `DOCKERHUB_USERNAME` | Docker Hub account username |
| `DOCKERHUB_TOKEN` | Docker Hub access token |
| `AZUREAPPSERVICE_CLIENTID_2A69...` | Azure AD client ID for `sme-tech` |
| `AZUREAPPSERVICE_TENANTID_53A7...` | Azure AD tenant ID |
| `AZUREAPPSERVICE_SUBSCRIPTIONID_1E5E...` | Azure subscription ID |

---

### Pipeline 2 — JAR-based deploy (`innovators`)

**Workflow file:** `.github/workflows/dev_innovators.yml`  
**Trigger branch:** `dev`  
**Target app:** `innovators` (Production slot)

Builds a JAR with Maven and deploys it directly to Azure App Service (no Docker).

| Job | Step | Description |
|-----|------|-------------|
| `build` | Checkout | Checks out the repository |
| | Set up Java 21 | Installs Java 21 (Microsoft distribution) |
| | Build with Maven | Runs `mvn -B clean package -DskipTests` from the `sme/` directory |
| | Upload artifact | Uploads `sme/target/*.jar` as the `java-app` artifact |
| `deploy` | Download artifact | Retrieves the built JAR |
| | Azure login | Authenticates via OIDC (Workload Identity Federation) |
| | Deploy to App Service | Deploys `*.jar` to the `innovators` Production slot; startup command: `java -jar /home/site/wwwroot/*.jar` |

**Required secrets:**

| Secret | Description |
|--------|-------------|
| `AZUREAPPSERVICE_CLIENTID_7090...` | Azure AD client ID for `innovators` |
| `AZUREAPPSERVICE_TENANTID_715A...` | Azure AD tenant ID |
| `AZUREAPPSERVICE_SUBSCRIPTIONID_C5B5...` | Azure subscription ID |

---

> For both pipelines, environment variables (DB credentials, JWT secret, AWS SES credentials, sender address, frontend URL, CORS origins) must be set in the Azure App Service **Configuration → Application Settings** panel — they are never baked into the build artifact.

---

## API

Base URL: `http://localhost:8080`  
All endpoints are versioned under `/api/v1/`.

Swagger UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)  
OpenAPI spec: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

### Server Environments

The Swagger UI exposes a server selector with the following environments:

| Environment | URL |
|-------------|-----|
| Local Development | `http://localhost:8080` |
| tech-innovators (Austria) | `https://tech-innovators-behmcxdbhtb3arap.austriaeast-01.azurewebsites.net` |
| sme-tech (Brazil) | `https://sme-tech-brdyghahdgcab3a2.brazilsouth-01.azurewebsites.net` |
| sme-operations (Canada) | `https://sme-operations-dza7e5czhdggexfh.canadacentral-01.azurewebsites.net` |
| innovators (South Africa) | `https://innovators-d2b3gycthabmdnhj.southafricanorth-01.azurewebsites.net` |

---

### Authentication Endpoints (`/api/v1/auth`)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/register` | None | Register a new user and business. Rate limited: 5/hour per IP, 3/hour per email. |
| `GET` | `/verify` | None | Verify email address using the token from the verification email. Returns HTTP 200 with `ApiResponse` envelope on success; HTTP 400/404 on invalid or expired token. |
| `POST` | `/resend-verification` | None | Resend the verification email for a pending account. |
| `POST` | `/login` | None | Authenticate and receive a JWT access token (24 hours) + refresh token (7 days). |
| `POST` | `/refresh` | None | Exchange a valid refresh token for a new access token. |
| `POST` | `/logout` | Bearer | Revoke the current refresh token. |

**Registration request body:**
```json
{
  "business": {
    "email": "owner@example.com",
    "password": "SecurePass1!",
    "fullName": "Jane Doe",
    "businessName": "Jane's Bakery",
    "description": "Artisan breads and pastries"
  }
}
```

---

### Account Management Endpoints (`/api/v1/account`)

All account endpoints require a valid JWT Bearer token.

| Method | Path | Role | Description |
|--------|------|------|-------------|
| `GET` | `/me` | Any | Retrieve the authenticated user's full profile (user + business details). |
| `PUT` | `/profile` | Any | Update display name (`fullName`). |
| `PUT` | `/password` | Any | Change password. Requires current password. Revokes all active refresh tokens on success. |
| `PUT` | `/business` | OWNER, ADMIN | Update business name and/or description. Slug and public link are never changed. |
| `DELETE` | `/` | Any | Soft-delete the account (and business). Requires password confirmation. |

**`GET /me` — response shape (`data` field):**
```json
{
  "userId": "uuid",
  "email": "owner@example.com",
  "fullName": "Jane Doe",
  "accountStatus": "VERIFIED",
  "role": "OWNER",
  "userCreatedAt": "2025-01-01T10:00:00",
  "userUpdatedAt": "2025-01-01T10:00:00",
  "businessId": "uuid",
  "businessName": "Jane's Bakery",
  "businessDescription": "Artisan breads and pastries",
  "slug": "janes-bakery",
  "publicLink": "https://sme-operations.netlify.app/store/janes-bakery",
  "businessUpdatedAt": "2025-01-01T10:00:00"
}
```

> The profile response never includes the password, tokens, or any internal security fields.

**`PUT /business` — behaviour notes:**
- `name` and `description` are both optional. Omit a field (or pass `null`) to leave it unchanged.
- Changing the business name does **not** regenerate the slug or public link.
- EMPLOYEE role receives `403 ACCESS_DENIED`.
- Description is HTML-sanitized on write (`<script>` blocks and all HTML tags stripped).

**`DELETE /` — request body:**
```json
{ "password": "your-current-password" }
```
On success, the user and their business are soft-deleted (`is_deleted = true`, `deleted_at` set). All refresh tokens are revoked. Subsequent requests with the old JWT return `401`.

---

### Workspace Endpoints (`/api/v1/workspaces`)

All workspace endpoints require a valid JWT Bearer token. A workspace is auto-created for the authenticated user's business on first access.

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/` | List all workspaces owned by the authenticated user. Auto-creates a workspace if none exists. |
| `GET` | `/{workspaceId}` | Get a single workspace by ID. Returns 404 if not found or not owned by the caller. |
| `GET` | `/{workspaceId}/storefront/draft` | Get the storefront draft for the workspace. Auto-creates a storefront from the `classic-boutique` v1 template if none exists. |
| `PUT` | `/{workspaceId}/storefront/draft` | Replace the full draft config. Validates the config against the specified template version before saving. |
| `POST` | `/{workspaceId}/storefront/draft/reset` | Reset the draft config to that version's DB `default_config` and sets `templateSetupCompletedAt` (products/media unchanged). |

### Storefront Templates (`/api/v1/storefront-templates`)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/` | List `available` and `coming_soon` templates for the picker (auth required). |

**Template catalog item (`data[]`):**
```json
{
  "id": "classic-boutique",
  "name": "Classic Boutique",
  "description": "Editorial homepage with hero, products, promos…",
  "vibe": "Editorial retail",
  "status": "available",
  "latestVersion": 1,
  "previewImageUrl": null,
  "supportedThemeIds": ["blue", "red"]
}
```

**`GET /` — response shape (`data` field):**
```json
[
  {
    "id": "uuid",
    "businessId": "uuid",
    "name": "Jane's Bakery",
    "publicSlug": null,
    "status": "DRAFT",
    "createdAt": "2025-01-01T10:00:00",
    "updatedAt": "2025-01-01T10:00:00"
  }
]
```

**`PUT /{workspaceId}/storefront/draft` — request body:**
```json
{
  "templateId": "classic-boutique",
  "templateVersion": 1,
  "configVersion": 1,
  "config": {
    "theme": "light",
    "sections": ["hero", "products", "contact"]
  }
}
```

**Storefront draft response shape (`data` field):**
```json
{
  "workspaceId": "uuid",
  "storefrontId": "uuid",
  "templateId": "classic-boutique",
  "templateVersion": 1,
  "configVersion": 1,
  "config": { ... },
  "templateSetupCompletedAt": null,
  "updatedAt": "2025-01-01T10:00:00"
}
```

**Storefront validation rules:**
- The `templateId` must exist and not be `DISABLED`.
- Apply/reset/update requires template `status = AVAILABLE` (`coming_soon` is catalog-only).
- The `templateVersion` must exist for that template.
- The `config` sections must be a subset of the template version's `supportedSections`.
- classic-boutique `supportedSections`: `hero`, `featuredProducts`, `newArrivals`, `shopByCategory`, `sale`, `promoBanner`, `textImage`, `features`, `testimonials`, `instagramGallery`, `newsletter`, `faq`, `contactCta`.
- The `config` theme must be one of the template version's `supportedThemes`.

---

### Public Store Endpoints (`/api/v1/public`)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/store/{slug}` | None | Retrieve public business info by slug. Returns name, slug, description, and public link only — no sensitive data. |

---

## Error Codes

All error responses use the `ApiResponse` envelope with `success: false`. The `error.code` field is always `SCREAMING_SNAKE_CASE`.

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `EMAIL_ALREADY_EXISTS` | 409 | The email address is already registered to an active account. |
| `INVALID_TOKEN` | 404 | The verification or refresh token does not exist. |
| `TOKEN_EXPIRED` | 400 | The verification token has passed its 24-hour expiry window. |
| `TOKEN_REVOKED` | 401 | The refresh token has been revoked (e.g., after logout or password change). |
| `INVALID_CREDENTIALS` | 401 | The supplied password does not match the account's stored credentials. |
| `ACCESS_DENIED` | 403 | The account is unverified, or the user lacks the required role for the resource. |
| `RATE_LIMIT_EXCEEDED` | 429 | Too many requests from this IP (>5/hour) or for this email (>3/hour). |
| `INVALID_PASSWORD` | 400 | The password does not meet complexity requirements (length, uppercase, lowercase, digit, special character). |
| `VALIDATION_FAILED` | 400 | One or more request fields failed Bean Validation or slug validation. |
| `SLUG_GENERATION_FAILED` | 500 | Unique slug could not be generated after 5 retries. |
| `ACCOUNT_ALREADY_DELETED` | 409 | The account has already been soft-deleted and cannot be acted upon. |
| `INTERNAL_ERROR` | 500 | An unexpected server-side error occurred. No internal details are exposed. |
| `WORKSPACE_NOT_FOUND` | 404 | The requested workspace does not exist or is not accessible. |
| `STOREFRONT_NOT_FOUND` | 404 | The requested storefront does not exist or has been removed. |
| `TEMPLATE_NOT_FOUND` | 404 | The specified storefront template does not exist. |
| `TEMPLATE_DISABLED` | 422 | The specified storefront template exists but is currently disabled. |
| `INVALID_STOREFRONT_CONFIG` | 400 | The storefront configuration is missing required fields or contains invalid values. |

**Error response shape:**
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "INVALID_CREDENTIALS",
    "message": "Bad credentials"
  },
  "timestamp": "2025-05-03T10:00:00Z"
}
```

---

## Testing

- Unit tests: JUnit 5 + Mockito
- Property-based tests: jqwik
- Integration tests: H2 in-memory DB (activated via `test` Spring profile)

Tests do **not** require any environment variables — the `test` profile overrides all external dependencies.

```bash
./mvnw test
```
