# Tovikeli — Open-source Social Platform

Tovikeli is a self-hostable social platform with a separately maintained web frontend and backend.

**中文简介：** Tovikeli 是一个可自行部署的社交平台项目。前端与后端分开存放，使用者可以按需获取其中一个，也可以组合部署完整服务。

## Live preview

A live preview deployment is available at: https://proud-cell-10b7.chenlinwen107.workers.dev/

The preview is provided for evaluation and may be unavailable or change independently of the source code.

## Repository layout

- **Tovikeli-Frontend/** — React 19, TypeScript, HeroUI, Tailwind CSS 4 and Vite.
- **Tovikeli-Backend/** — Kotlin, Spring Boot, Java 21, PostgreSQL and Flyway. It also contains backend-served static pages and assets.
- **THIRD-PARTY-NOTICES.md** — upstream attribution and third-party license notes.
- **OPERATIONS.md** — deployment overview.

## Frontend origin and attribution

The frontend is adapted from the frontend portion of [maobase/hahasns](https://github.com/maobase/hahasns). The upstream project is licensed under the MIT License. Its original HahaSNS contributors copyright notice and license are retained in Tovikeli-Frontend/LICENSE.

Tovikeli is not a fork of the complete HahaSNS application. The Tovikeli backend is a separate implementation written for this project; it is not the NestJS backend from HahaSNS.

## Quick start

### Backend

Requirements: JDK 21 and PostgreSQL.

1. Create a PostgreSQL database and a dedicated database user.
2. Set the environment variables DB_URL, DB_USERNAME, and DB_PASSWORD.
3. From the backend directory, build and run:

    cd Tovikeli-Backend
    SPRING_PROFILES_ACTIVE=dev DB_URL=jdbc:postgresql://127.0.0.1:5432/contentplatform_dev DB_USERNAME=<db-user> DB_PASSWORD=<db-password> ./gradlew bootRun

For production, use SPRING_PROFILES_ACTIVE=prod and supply credentials through a secure environment/secret manager. Database schema migrations are managed by Flyway.

### Frontend

Requirements: Node.js 20 or newer.

    cd Tovikeli-Frontend
    npm install
    npm run dev

The Vite development server listens on port 5173 and proxies /api and /uploads to http://localhost:8080. Start the backend separately. For a production frontend build, run npm run build.

See Tovikeli-Backend/docs/OPERATIONS.md and OPERATIONS.md for deployment notes. The frontend API contract is documented in Tovikeli-Frontend/src/api/README.md.

## Media asset

The MP4 under Tovikeli-Backend/src/main/resources/static/assets/backgrounds/ is intentionally included. It is referenced by backend-served HTML pages as a background video; removing it would break those references.

## License

- The root LICENSE applies only to original Tovikeli backend code in Tovikeli-Backend/, except files that carry their own notices or are third-party materials.
- The adapted frontend retains the upstream MIT license and copyright notice in Tovikeli-Frontend/LICENSE.
- Third-party dependencies and assets remain subject to their respective licenses and terms. See THIRD-PARTY-NOTICES.md.

Do not interpret the root LICENSE as relicensing the upstream frontend or third-party components.
