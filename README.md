# EuroJob Copilot

**EuroJob Copilot** is a full-stack European job discovery, visa sponsorship intelligence, and AI-assisted career SaaS platform built for international and European engineers. It unifies real European job aggregation, official visa sponsor verification, deterministic + AI compatibility scoring, grounded CV/cover-letter tailoring, Kanban application tracking, recruiter CRM, interview coaching, and job search autopilot workflows.

---

## Key Features

1. **Authentication & Security:** JWT (`HS256`) access tokens, rotating `HttpOnly`/`SameSite=Strict` refresh token cookies with replay revocation, BCrypt password hashing, role-based access (`ROLE_USER`, `ROLE_ADMIN`), per-client fixed-window HTTP rate limiting (`AUTH`, `AI`, `API`), security headers, and strict CSP in Nginx.
2. **Candidate Profile & Master Resume Workspace:** Secure PDF/DOCX upload (`Apache Tika` magic-bytes validation), PDF/text extraction + structured AI parsing, mandatory human-in-the-loop approval, and profile sync (skills, education, work experience).
3. **European Job Discovery & Connectors:** Deduplicated MySQL-backed catalog with real external connectors for **Arbeitnow** and **Adzuna**, faceted search/filtering, save/interested/hide actions, and validated external apply redirects.
4. **Visa Sponsorship Intelligence:** Rules across **14 European countries**, multi-tier company verification, and official **Netherlands IND Public Register of Recognized Sponsors** CSV synchronization and lookup.
5. **Hybrid Matching & AI JD Intelligence:** Weighted deterministic compatibility scoring (`Skill 40%`, `Experience 20%`, `Title 15%`, `Location 10%`, `Sponsorship 10%`, `Salary 5%`) paired with configurable AI semantic evaluation (`Gemini`, `OpenAI`, or `Anthropic`).
6. **Grounded Tailored CVs & Cover Letters:** Evidence-grounded AI tailoring against approved candidate facts (`MODERN`, `MINIMAL_ATS`, `EUROPASS` CV templates) plus native **Apache PDFBox** PDF, pure-Java **OOXML DOCX**, and **TXT** exports.
7. **Application Tracker, Recruiter CRM & AI Copilot:** 10-stage drag-and-drop Kanban pipeline with status audit history, recruiter contacts & interaction log with explicit-approval outreach drafts, contextual Job AI Assistant threads, AI Interview Prep Coach with graded practice answers, Search Autopilot with in-app notifications, Executive Dashboard, and European Market Insights.

---

## Architecture & Tech Stack

- **Frontend:** Angular 20 (Standalone Components, Signals, RxJS, Angular Material, SCSS), served via Nginx (`nginx:1.27-alpine`)
- **Backend:** Java 21 LTS, Spring Boot 3.5.5, Spring Security 6, Spring Data JPA, Liquibase, SpringDoc OpenAPI 3, Apache PDFBox 3, Apache Tika 3
- **Database:** MySQL 8.4 (`utf8mb4_unicode_ci`), **32 tables** managed via **17 Liquibase changelogs** (`001`–`017`)
- **Operations & CI/CD:** Docker multi-stage builds, production Compose overlay (`docker-compose.prod.yml`), MySQL backup/restore scripts, GitHub Actions CI (`.github/workflows/ci.yml`)

---

## Documentation Suite

| Document | Scope |
|---|---|
| [Architecture Specification](docs/architecture/eurojob-copilot-architecture.md) | System boundaries, filter chain, AI/connector integrations, modules |
| [REST API Contracts](docs/api/eurojob-copilot-api-contracts.md) | All 83 endpoints across 23 controllers, rate limits, error & binary contracts |
| [OpenAPI 3 Specification](docs/api/openapi.yaml) | Machine-readable OpenAPI 3 YAML specification |
| [Database Design & ERD](docs/database/eurojob-copilot-database-design.md) | 32-table MySQL schema catalog, Mermaid ERD, Liquibase `001`–`017` inventory |
| [Production Deployment Guide](docs/deployment/production-deployment-guide.md) | Secrets, TLS upstream proxy, prod overlay, backups & monitoring |

---

## Project Structure

```text
.
├── backend/                     # Spring Boot 3.5.5 / Java 21 backend & Liquibase 001-017
│   ├── Dockerfile               # Multi-stage Maven -> Temurin 21 JRE (non-root user)
│   └── src/main/resources/
│       ├── application*.properties # local, dev, test, prod profiles
│       ├── logback-spring.xml   # Profile-aware structured logging
│       └── db/changelog/        # Liquibase migrations (32 tables)
├── frontend/                    # Angular 20 Standalone frontend
│   ├── Dockerfile               # Multi-stage Node 22 build -> Nginx 1.27 Alpine
│   ├── nginx.conf               # Security headers, CSP, /api/ proxy, SPA fallback
│   └── proxy.conf.json          # Local ng serve proxy config
├── scripts/
│   ├── backup-mysql.sh          # Compressed MySQL 8 backup + SHA-256 checksum + retention
│   └── restore-mysql.sh         # Interactive verified MySQL restore script
├── docs/                        # Architecture, API, Database, Deployment guides
├── .github/workflows/ci.yml     # Automated backend/frontend tests, JAR, and Docker builds
├── docker-compose.yml           # Base local/dev stack (MySQL, Backend, Frontend)
├── docker-compose.prod.yml      # Hardened production Compose overlay
└── .env.example                 # Complete environment variable reference
```

---

## Prerequisites

- **Docker Mode (Recommended):** Docker Engine 24+ and Docker Compose v2.20+
- **Native Mode:** Java 21 JDK, Node.js 22+ / npm 10+, MySQL 8.4+

---

## 1. Environment Configuration

Copy the environment template and set strong secrets:

```bash
cp .env.example .env
openssl rand -base64 48   # Use output for JWT_SECRET
```

Required variables to customize before deployment:
- `MYSQL_ROOT_PASSWORD`, `MYSQL_PASSWORD`, `SPRING_DATASOURCE_PASSWORD`
- `JWT_SECRET` (minimum 32 characters; `prod` forbids dev/placeholder values)
- `CORS_ALLOWED_ORIGINS` (explicit allowed origin(s), HTTPS in production)
- Optional API keys for external providers: `GEMINI_API_KEY` / `OPENAI_API_KEY` / `ANTHROPIC_API_KEY`, `ADZUNA_APP_ID` & `ADZUNA_APP_KEY`

> Never commit `.env` or real credentials to Git.

---

## 2. Running with Docker Compose

### Development / Local Verification Stack
```bash
docker compose up --build -d
docker compose ps
docker compose logs -f
```
Services:
1. `mysql` (`mysql:8.4`): Persists `mysql_data`; mapped to host `127.0.0.1:${MYSQL_EXTERNAL_PORT:-3307}` by default.
2. `backend`: Runs Liquibase migrations `001`–`017`, exposes `http://localhost:8080`, mounts `resumes_data` volume.
3. `frontend` (`Nginx` + Angular): Serves SPA on `http://localhost:4200` and proxies `/api/` and `/actuator/` to `backend:8080`.

### Hardened Production Overlay
For production deployments behind an upstream TLS reverse proxy (Caddy/Nginx/ALB), ensure `SPRING_PROFILES_ACTIVE=prod`, `AUTH_COOKIE_SECURE=true`, strong `JWT_SECRET`, and explicit `CORS_ALLOWED_ORIGINS` in `.env`, then run:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up --build -d
```

This overlay removes public MySQL port exposure, binds backend/frontend ports to `127.0.0.1` by default, enforces `prod` profile (`ddl-auto: validate`), and disables `/v3/api-docs` and Swagger UI. See [Production Deployment Guide](docs/deployment/production-deployment-guide.md) for full details.

---

## 3. Running Natively (Without Docker)

```bash
# 1. Ensure MySQL 8.4 is running on localhost:3306 with database `eurojob_copilot`

# 2. Backend
cd backend
export SPRING_DATASOURCE_USERNAME=root
export SPRING_DATASOURCE_PASSWORD=<your_mysql_password>
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# 3. Frontend (separate terminal)
cd frontend
npm install --legacy-peer-deps
npm start
```

---

## 4. Database Backups & Restores

Executable operational scripts in `scripts/`:

```bash
# Backup (Docker Compose mysql container)
./scripts/backup-mysql.sh --mode docker --output-dir ./backups --retention-days 14

# Restore after verification (prompts for confirmation unless --yes)
./scripts/restore-mysql.sh --mode docker --file ./backups/eurojob_copilot_<timestamp>.sql.gz
```

Always back up both MySQL (`mysql_data`) and uploaded resumes (`resumes_data`).

---

## 5. Automated Testing & CI

### Backend Test Suite & JAR Package
```bash
cd backend
./mvnw test
./mvnw package -DskipTests
```

### Frontend Unit Tests & Production Build
```bash
cd frontend
npm test -- --watch=false --browsers=ChromeHeadless
npm run build
```

### GitHub Actions CI
[`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs on push/PR to `main` and `develop`:
1. `backend`: Java 21 `./mvnw test -B` & `./mvnw package -DskipTests -B`
2. `frontend`: Node 22 headless Chrome Karma tests & `npm run build`
3. `containers`: Shell script syntax checks, `docker compose` dev + prod config validation, and Buildx Docker image builds

---

## 6. Known Limitations

- **Email Delivery:** `NOTIFICATIONS_EMAIL_ENABLED` defaults to `false`; the current notifier (`LoggingAutopilotEmailNotifier`) logs digests only and does not send SMTP emails (`spring-boot-starter-mail` is not configured).
- **Password Reset Flow:** `/auth/forgot-password` and `/auth/reset-password` frontend routes are UI previews; backend password reset token delivery requires SMTP configuration.
- **In-Memory Rate Limiter:** `RateLimitingFilter` is designed for single-instance deployment; multi-replica horizontal scaling should replace it with distributed gateway or Redis rate limiting.


<!-- List of other job listing providers -->
<!-- // 1. Best alternatives to Adzuna API
// Platform	API availability	Coverage
// Jooble	Official API	Global
// Careerjet	Official API	Global
// Reed	Official API	UK
// Arbeitnow	Free public API	Germany / Europe
// The Muse	Official API	Mainly USA
// Findwork	Developer API	Developer jobs
// JSearch	API via RapidAPI	Multi-country
// USAJobs	Official government API	USA
// Arbeitsagentur	Official API	Germany

// 2. Direct developer/API links
// Platform	Developer / API registration
// Adzuna	Get API key
// Jooble	Get API key
// Careerjet	API access
// Reed	Developer portal
// Arbeitnow	Website
// The Muse	Developer API
// Findwork	Developer API
// JSearch	RapidAPI access
// USAJobs	Developer API
// Arbeitsagentur	API documentation -->