# ContentPlatformBackend — Operations Guide

Production-readiness notes for the current V1 application (Spring Boot 4, Kotlin,
PostgreSQL, Flyway, Spring Session JDBC). This document contains **no
credentials**; every secret is supplied by the runtime environment.

---

## 1. Configuration model

| Profile | Purpose | Datasource |
|---------|---------|------------|
| *(none)* | Framework/tests; no database beans are created | none |
| `dev` | Local development over HTTP | `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` (localhost default) |
| `prod` | Production over HTTPS | `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` (required, no defaults) |

Shared settings live in `src/main/resources/application.properties` and contain
no environment-specific values.

### Environment variables

| Variable | Profile | Notes |
|----------|---------|-------|
| `DB_URL` | dev, prod | JDBC URL, e.g. `jdbc:postgresql://host:5432/contentplatform` |
| `DB_USERNAME` | dev, prod | Dedicated least-privilege database role |
| `DB_PASSWORD` | dev, prod | Supplied by the secret store; never committed |

Secrets MUST NOT be written into `application*.properties`, source code, the
frontend, shell scripts or tests.

### Running

```bash
# Development (plain HTTP)
SPRING_PROFILES_ACTIVE=dev \
DB_URL=jdbc:postgresql://127.0.0.1:5432/contentplatform_dev \
DB_USERNAME=<local-user> DB_PASSWORD= \
java -jar build/libs/content-platform-backend-0.0.1-SNAPSHOT.jar

# Production (HTTPS terminator in front; cookie is Secure)
SPRING_PROFILES_ACTIVE=prod \
DB_URL='jdbc:postgresql://db.internal:5432/contentplatform' \
DB_USERNAME='contentplatform_app' \
DB_PASSWORD="$(cat /run/secrets/db_password)" \
java -jar build/libs/content-platform-backend-0.0.1-SNAPSHOT.jar
```

---

## 2. Security boundary (summary)

- **Authentication is session-only.** `HttpOnly` `JSESSIONID` cookie →
  `AuthenticationFilter` → `CurrentCaller` → `CallerResolver`. There is no
  `X-User-Id`, no `Authorization: Bearer` fallback and no debug/test user.
- **Cookie:** `HttpOnly`, `SameSite=Lax`, `Secure=true` in `prod`,
  `Secure=false` in `dev`. Session timeout 30 minutes.
- **Session fixation:** `AuthController.login` calls `changeSessionId()` when a
  session already exists before storing the identity.
- **CSRF:** Spring Security CSRF is intentionally disabled. The primary defence
  is the `SameSite=Lax` session cookie (not attached to cross-site
  POST/PATCH/DELETE) combined with a JSON-only API (`Content-Type:
  application/json`) and **no CORS configuration**, so a cross-site form cannot
  forge state-changing requests. Session/state changes are POST/PATCH/DELETE
  only; GETs are read-only.
- **Errors:** internal details are never returned
  (`server.error.include-* = never/false`); API errors stay status-code only.
- **Logs:** framework internals are quieted in `prod`; the Spring Security
  generated in-memory user/password is disabled via
  `spring.autoconfigure.exclude` in `application.properties`.

---

## 3. Database backup and restore

All commands use a connection URI (`DB_URL`) so no password appears in the
command line history or process list when it is read from the environment.

### 3.1 Backup (custom, compressed format — recommended)

```bash
STAMP="$(date +%Y%m%d-%H%M%S)"
pg_dump --format=custom --no-owner --file="contentplatform-${STAMP}.dump" "$DB_URL"
```

Plain-SQL alternative (human-readable, larger):

```bash
pg_dump --format=plain --no-owner "$DB_URL" > "contentplatform-${STAMP}.sql"
```

### 3.2 Restore

Into a fresh database (create it first with `createdb`):

```bash
TARGET_URL='jdbc:postgresql://127.0.0.1:5432/contentplatform_restore'
# pg_restore/psql take a libpq URI or keyword string, not a JDBC URL:
TARGET="${TARGET_URL#jdbc:}"
pg_restore --clean --if-exists --no-owner --dbname="$TARGET" contentplatform-YYYYmmdd-HHMMSS.dump
```

Plain-SQL restore:

```bash
psql "$TARGET" -v ON_ERROR_STOP=1 -f contentplatform-YYYYmmdd-HHMMSS.sql
```

### 3.3 Verify a restored database

```bash
# Schema history is complete and successful
psql "$TARGET" -c \
  "SELECT installed_rank, version, description, success
     FROM flyway_schema_history ORDER BY installed_rank;"

# Required tables are present
psql "$TARGET" -c \
  "SELECT table_name FROM information_schema.tables
    WHERE table_schema='public'
      AND table_name IN ('content','author','tag','platform_user',
                         'content_interaction','content_comment',
                         'spring_session','spring_session_attributes')
    ORDER BY table_name;"
```

The application also re-validates the schema on every startup: Flyway refuses to
start if the history or checksums do not match, so a successful boot is itself a
schema check.

---

## 4. Startup verification procedure

Run the repeatable check against any running instance:

```bash
BASE=http://127.0.0.1:8080 \
DB_URL=jdbc:postgresql://127.0.0.1:5432/contentplatform_prod \
./scripts/verify-startup.sh
```

The script verifies, in order:

1. Process is answering HTTP (`GET /` → 200).
2. Public API is reachable (`GET /api/v1/contents` → 200).
3. Flyway history exists and every migration succeeded.
4. The required business tables and both Spring Session tables exist.

Expected startup log lines (all at `INFO`):

```
Successfully validated N migrations
Current version of schema "public": N
Started ContentPlatformBackendApplicationKt
Tomcat started on port <port> (http)
```
