# Changelog

All notable changes to MR Board System will be documented in this file.

## [1.1.0] — 2026-07-26

### Added
- **HTTPS/SSL**: Nginx reverse proxy with TLSv1.2/1.3 + self-signed cert generation scripts
- **JMeter Stress Test**: 100 concurrent users test plan covering 7 core APIs
- **First-login password enforcement**: User store, router guard, and profile warning banner
- **Prometheus + Grafana**: 10-panel monitoring dashboard + docker-compose monitoring stack
- **Smoke test scripts**: 11-checkpoint shell script covering full core API flow + Windows .bat
- **MR comments cache**: `mr_comments` table + GitHub/GitLab API integration + frontend comments tab
- **Async export**: Export task manager + async service + polling-based download flow
- **Pinia persisted state**: Automatic localStorage persistence via `pinia-plugin-persistedstate`
- **403 Forbidden page**: Dedicated `ForbiddenView.vue` + route registration
- **Profile page**: Display name, email, password change with form validation
- **Batch user delete**: Backend endpoint + frontend table selection
- **Board skeleton screen**: `el-skeleton` loading placeholder
- **Responsive layout**: `@media` queries for 1440×900 / 1600px / 1280px
- **WebSocket sync notifications**: Real-time sync start/complete/failure notifications
- **Flyway DB migration**: Versioned schema management for test/prod environments
- **Redis utility**: `RedisUtil` with String/Hash/List/Set/ZSet/Expire operations
- **Logback rolling**: Size-and-time-based log rotation + password masking
- **Permission evaluator**: Fine-grained `CustomPermissionEvaluator` for data-level RBAC
- **Checkstyle rules**: `checkstyle.xml` based on Google Java Style
- **ESLint + Prettier**: Frontend code quality config
- **Husky pre-commit**: Compile + type-check hook
- **HTTP proxy support**: `GitClientFactory` proxy configuration for Git API clients
- **Docker BuildKit**: Multi-stage backend Dockerfile with Maven dependency caching

### Fixed
- Database `report_daily_summary` table missing in init.sql
- Webhook endpoints blocked by Spring Security (added `.permitAll()`)
- `password_changed` column missing in existing MySQL containers
- Logback `TimeBasedRollingPolicy` → `SizeAndTimeBasedRollingPolicy`
- Frontend double `/api` prefix causing 404 + response value extraction

### Changed
- Flyway migrations moved to `db/migration/` directory
- Dev profile: Flyway disabled (init.sql handles schema)
- Prod profile: Flyway enabled for incremental migrations

## [1.0.0] — 2026-07-25

### Added
- Phase 1: RBAC authentication (JWT + BCrypt + 5 roles + 11 permissions)
- Phase 2: Git data sync (GitLab/GitHub API → MySQL)
- Phase 3: Kanban board with drag-and-drop + CI status + MR details
- Phase 4: Statistics reports with Excel/CSV export
- Phase 5: Docker Compose deployment + E2E tests (35 test cases)
