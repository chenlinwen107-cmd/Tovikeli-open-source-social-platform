# Tovikeli — Operations overview

This repository separates the browser frontend from the backend service. PostgreSQL stores application data; Flyway manages schema migrations.

## Requirements

- Backend: JDK 21, PostgreSQL, Linux/macOS/Windows (Dockerfile provided).
- Frontend: Node.js 20+, npm.
- Production: HTTPS termination/reverse proxy recommended.

## Backend configuration

Set these environment variables for the backend:

- DB_URL — PostgreSQL JDBC URL.
- DB_USERNAME — dedicated database role.
- DB_PASSWORD — database password supplied through the runtime environment or secret manager.
- SPRING_PROFILES_ACTIVE — use dev for local development and prod for production.

Never commit real credentials, tokens, certificates or private keys. The production profile requires the database settings to be supplied externally.

## Build

Backend:

    cd Tovikeli-Backend
    ./gradlew build

Frontend:

    cd Tovikeli-Frontend
    npm ci
    npm run build

For local frontend development, npm run dev starts Vite on port 5173 and proxies /api and /uploads to the backend at localhost:8080.

## Database

Flyway migrations are located at Tovikeli-Backend/src/main/resources/db/migration/. Do not edit migrations that have already been applied to a production database; add a new migration instead. Back up PostgreSQL before upgrades and test restores regularly.

## Deployment

The backend includes a Dockerfile and Cloudflare Pages worker configuration. Review the backend operations guide and deployment files before deploying. Configure environment-specific origins and secrets in the hosting provider; do not hardcode them into source files.

## Security checklist

- Use HTTPS in production.
- Use a dedicated, least-privilege database user.
- Keep secrets outside the repository.
- Restrict database network access.
- Back up the database and test recovery.
- Review third-party API terms before enabling optional content ingestion.
