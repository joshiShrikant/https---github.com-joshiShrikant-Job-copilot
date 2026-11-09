# EuroJob Copilot – Production Deployment & Operations Guide
**Document Version:** 2.0.0  
**Target Stack:** Docker Compose / Linux Host + TLS Reverse Proxy + MySQL 8.4 + Spring Boot 3.5.5 + Angular 20 (Nginx)

---

## 1. Production Architecture Overview

In production (`SPRING_PROFILES_ACTIVE=prod`):
- **Frontend & Edge Proxy (`frontend` container):** Nginx `1.27-alpine` serves the compiled Angular 20 browser bundle, compresses responses (`gzip`), sets security headers + CSP, enforces `client_max_body_size 12M` for CV uploads, and proxies `/api/` to `backend:8080` with a `90s` read timeout.
- **API & Worker Service (`backend` container):** Spring Boot 3.5.5 (`eclipse-temurin:21-jre-alpine`, non-root user `spring:spring`) executes Liquibase migrations on boot (`ddl-auto=validate`), enforces `RateLimitingFilter` and JWT RBAC, stores uploaded CVs on persistent volume `resumes_data` (`/var/lib/eurojob-copilot/resumes`), disables Swagger/OpenAPI docs by default, and supports graceful shutdown (`30s`).
- **Database (`mysql` container or managed MySQL 8.4):** Uses `utf8mb4` / `utf8mb4_unicode_ci` on persistent volume `mysql_data`. In the production Compose overlay (`docker-compose.prod.yml`), MySQL does **not** bind a public host port.
- **Upstream TLS Proxy (Required for Internet Exposure):** Place a TLS-terminating reverse proxy (Nginx/Caddy/Traefik/Cloud ALB) in front of the `frontend` container (`127.0.0.1:8081` in `docker-compose.prod.yml`), terminating HTTPS (`443`), forwarding `X-Forwarded-Proto: https` & `X-Forwarded-For`, and enforcing HSTS at the TLS edge.

---

## 2. Pre-Deployment Checklist

1. **Host & Tooling:** Linux/cloud VM or orchestrator with Docker Engine `>= 24.x`, Docker Compose `>= v2.20`, `bash`, `gzip`, `shasum`/`sha256sum`, and outbound HTTPS access for optional job providers/AI APIs.
2. **Generate Cryptographic Secrets:**
   ```bash
   # Generate a strong >= 256-bit JWT HMAC key (never reuse placeholders)
   openssl rand -base64 48
   # Generate strong database credentials
   openssl rand -hex 24
   ```
   `JwtService` rejects blank or placeholder (`replace-with*`) `JWT_SECRET` values at startup outside `local`/`test` profiles.
3. **Configure `.env`:**
   ```bash
   cp .env.example .env
   chmod 600 .env
   ```
   Set at minimum in `.env` for production:
   - `SPRING_PROFILES_ACTIVE=prod`
   - `JWT_SECRET=<generated-secret>`
   - `AUTH_COOKIE_SECURE=true` (requires HTTPS end-to-end)
   - `CORS_ALLOWED_ORIGINS=https://your-domain.example`
   - `MYSQL_PASSWORD=<strong-password>` and `MYSQL_ROOT_PASSWORD=<strong-root-password>`
   - `SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/eurojob_copilot?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true` (or managed RDS TLS endpoint with `useSSL=true&allowPublicKeyRetrieval=false`)
   - `SPRINGDOC_API_DOCS_ENABLED=false` and `SPRINGDOC_SWAGGER_UI_ENABLED=false`
   - `RATE_LIMIT_ENABLED=true` (adjust `RATE_LIMIT_*` thresholds to traffic needs)
   - Optional external APIs: `GEMINI_API_KEY` / `OPENAI_API_KEY` / `ANTHROPIC_API_KEY`, `ADZUNA_APP_ID` & `ADZUNA_APP_KEY`, `IND_SCHEDULER_ENABLED=true` if automated IND register updates are desired.

---

## 3. Deploying with Docker Compose (Production Overlay)

1. **Validate configuration & build images:**
   ```bash
   docker compose -f docker-compose.yml -f docker-compose.prod.yml config -q
   docker compose -f docker-compose.yml -f docker-compose.prod.yml build --pull
   ```
2. **Launch stack in detached mode (MySQL starts & passes healthcheck first; Backend runs Liquibase `001`–`017` before marking healthy):**
   ```bash
   docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
   docker compose -f docker-compose.yml -f docker-compose.prod.yml ps
   ```
3. **Verify health & migrations:**
   ```bash
   curl -fsS http://127.0.0.1:8081/api/v1/health
   curl -fsS http://127.0.0.1:8081/actuator/health
   docker compose logs --tail=100 backend
   ```
   In `prod`, `/actuator/health` returns `{"status":"UP"}` cleanly (`show-details: when_authorized`). Detailed component health is returned only to authenticated `ROLE_ADMIN` tokens.

---

## 4. Database Backups & Disaster Recovery

Two operational scripts live under [`scripts/`](../../scripts/):

### 4.1 Automated Logical Backup ([`scripts/backup-mysql.sh`](../../scripts/backup-mysql.sh))
Uses `mysqldump --single-transaction --quick --routines --triggers --events --hex-blob` (passing password via `MYSQL_PWD` env rather than process args), verifies gzip integrity (`gzip -t`), creates a `.sha256` checksum, and prunes files older than `--retention-days`:
```bash
./scripts/backup-mysql.sh --mode docker --output-dir ./backups --retention-days 14
```
For native MySQL outside Docker:
```bash
./scripts/backup-mysql.sh --mode native --output-dir /var/backups/eurojob --retention-days 30
```
Schedule daily at 01:30 UTC via host `cron`:
```cron
30 1 * * * cd /opt/eurojob-copilot && ./scripts/backup-mysql.sh --mode docker --output-dir /var/backups/eurojob --retention-days 14 >> /var/log/eurojob-backup.log 2>&1
```

### 4.2 Verified Restore Drill ([`scripts/restore-mysql.sh`](../../scripts/restore-mysql.sh))
Validates the `.sha256` checksum and gzip stream integrity before restoring:
```bash
./scripts/restore-mysql.sh --file ./backups/eurojob_copilot_20261109T013000Z.sql.gz --mode docker --yes
```

### 4.3 Persistent Document Volume Backup (`resumes_data`)
Uploaded master resumes reside outside MySQL on Docker volume `resumes_data` (`/var/lib/eurojob-copilot/resumes`). Snapshot this directory alongside MySQL dumps:
```bash
docker run --rm -v eurojob_resumes_data:/data:ro -v "$(pwd)/backups:/backup" alpine \
  tar czf "/backup/resumes_$(date -u +%Y%m%dT%H%M%SZ).tar.gz" -C /data .
```

---

## 5. Logging, Monitoring & Rate Limiting

- **Application Logging:** Configured via [`logback-spring.xml`](../../backend/src/main/resources/logback-spring.xml) (`prod` profile logs `WARN` root / `INFO` `com.eurojobcopilot`, suppressing noisy PDFBox font warnings). Inspect live logs via `docker compose logs -f backend frontend`.
- **Rate Limiting (`RateLimitingFilter`):**
  - Enforced per-client across `AUTH` (`20/min` on login/register/refresh), `AI` (`30/min` on AI generation/parse/coach POSTs), and `API` (`180/min` across other `/api/v1/**` routes). `/api/v1/health` is exempt.
  - Exceeded limits emit HTTP `429 Too Many Requests` (`ApiErrorResponse` JSON) with `Retry-After` and `X-RateLimit-*` headers.
  - Note: When placing Nginx or a load balancer in front of `backend`, `server.forward-headers-strategy=framework` in `application-prod.properties` handles private proxy `X-Forwarded-For` resolution safely.
- **Container Resource Guidance:** `backend/Dockerfile` sets `-XX:MaxRAMPercentage=75.0`. Allocate at least 2 GB RAM (`1 GB` backend JVM + MySQL + Nginx) for smooth multi-user + PDFBox execution.

---

## 6. CI/CD Pipeline (GitHub Actions)

The repository includes [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml), running on pushes and PRs to `main` and `develop`:
1. **`backend`:** JDK 21 Maven `./mvnw test -B`, `./mvnw package -DskipTests -B`, uploads Surefire XML reports.
2. **`frontend`:** Node 22 `npm ci --legacy-peer-deps`, headless Chrome Karma/Jasmine suite (`ng test`), and Angular production build (`npm run build`).
3. **`containers`:** Bash syntax validation (`bash -n`) on backup/restore scripts, Docker Compose manifest validation (`base` + `prod` overlay), and Buildx image compilation for both `backend` and `frontend`.

---

## 7. Operational Notes Prior to Public Launch

1. **Email Relay Not Bundled (`LoggingAutopilotEmailNotifier`):** Autopilot email digests log dispatch intent rather than sending SMTP mail (`spring-boot-starter-mail` is not configured). Keep `NOTIFICATIONS_EMAIL_ENABLED=false` or integrate an SMTP/API relay before marketing email alerts.
2. **Self-Service Password Reset Deferred:** `/auth/forgot-password` is a UI placeholder; backend password-reset email issuance is not yet wired.
3. **Bootstrap First Admin Account Carefully:** Public registration always assigns `ROLE_USER`. Promote initial administrators explicitly in MySQL (`UPDATE users SET role = 'ROLE_ADMIN' WHERE email = '...';`) or via a dedicated ops procedure.
4. **Live External Integrations Require Runtime Credentials & Access:** Live Arbeitnow/IND HTTP sync, Adzuna (`ADZUNA_APP_ID`/`ADZUNA_APP_KEY`), and LLM features (`GEMINI_API_KEY` / `OPENAI_API_KEY` / `ANTHROPIC_API_KEY`) require valid outbound connectivity and API keys in the target deployment environment.

