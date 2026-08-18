# 🔐 dnsfilt-admin-backend: Central REST API, Security & UI Gateway

[![Java 21](https://img.shields.io/badge/Java-21%20LTS-orange?style=flat-square&logo=openjdk)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot 3](https://img.shields.io/badge/Spring%20Boot-3.3.5-brightgreen?style=flat-square&logo=springboot)](https://spring.io/projects/spring-boot)
[![Spring Security](https://img.shields.io/badge/Security-JWT%20%2B%20RBAC-blue?style=flat-square&logo=springsecurity)](https://spring.io/projects/spring-security)
[![Caffeine Cache](https://img.shields.io/badge/Cache-Caffeine%20L1-blue?style=flat-square)](https://github.com/ben-manes/caffeine)
[![Oracle ATP](https://img.shields.io/badge/Database-Oracle%20ATP%2023ai-red?style=flat-square&logo=oracle)](https://www.oracle.com/autonomous-database/)

`dnsfilt-admin-backend` is the central control plane, authentication server, and API gateway for the DNSFilt ecosystem. Built with **Spring Boot 3.3** and **Java 21**, it hosts all administrative REST endpoints, enforces Role-Based Access Control (RBAC), interacts with Oracle Autonomous Database 23ai, and embeds the compiled Angular 18 Single-Page Application.

---

## 👋 A Note from the Author

> Hi! I'm **Kaushik**, the developer behind **DNSFilt**. I built `dnsfilt-admin-backend` with an emphasis on production resilience: zero-latency Caffeine in-memory caching to shield the database, cascade-safe user entity lifecycles, and automated frontend asset packaging.
>
> 🔍 **I am actively seeking new engineering opportunities.** If you are looking for a software engineer skilled in Java, Spring Boot, microservices architecture, and cloud infrastructure, feel free to connect with me on [**LinkedIn**](https://www.linkedin.com/in/ikaushikpal).
>
> ⭐ *Every star ⭐, issue, or referral means a lot — thank you!*

---

## 💡 What is `dnsfilt-admin-backend`?

`dnsfilt-admin-backend` serves as both the REST API provider and the web server for the platform. It handles user authentication, domain threat policies, custom DNS rewrites, telemetry analytics retrieval, and communicates asynchronously with the Python orchestrator for cluster scaling.

### Key Capabilities:
- **📦 Single-Jar Deployment**: Compiles and embeds the Angular 18 frontend inside `src/main/resources/static/`, allowing a single Spring Boot container to serve both APIs and web UI.
- **⚡ Caffeine In-Memory Caching**:
  - `tokenBlacklist`: Instant JWT revocation on logout (`< 0.01ms`).
  - `rulesCache` / `domainsCache` / `resolverConfigCache`: Evicted automatically via `@CacheEvict` upon any mutation.
- **🛡️ Multi-Tier RBAC Security**:
  - `ROLE_SUPER_ADMIN`: Permanent superadmin (`kaushik`) with infrastructure scaling and password management.
  - `ROLE_ADMIN`: User management, cluster node scaling, and rule configuration.
  - `ROLE_OPERATOR`: Domain rule and override management.
  - `ROLE_VIEWER`: Read-only audit access to telemetry.
- **🔄 Instant Push Notifications**: Automatically fires webhook notifications to `dnsfilt-orchestrator` and pushes domain rule updates into Redis.

---

## 🎯 Why `dnsfilt-admin-backend`?

1. **Eliminate SPA Routing Issues**: Implements tiered HTML5 pushState fallback in [`SpaWebMvcConfig.java`](file:///Users/kaushikpal/Desktop/codes/projects/dnsfilt/dnsfilt-admin-backend/src/main/java/com/dnsfilt/dnsadmin/config/SpaWebMvcConfig.java) (`index.html` -> `index.csr.html` -> `home/index.html`) so browser refreshes on sub-routes never throw 404.
2. **Cascade FK Safety**: Eliminates Oracle `ORA-02292` constraint errors by programmatically purging child refresh tokens before deleting user accounts.
3. **Automated Gradle UI Bundling**: Includes `buildFrontend` and `copyFrontend` tasks in `build.gradle` that automatically rebuild the Angular app during backend compilation.

---

## 📡 REST API Reference

| Method | Endpoint | Access Level | Description |
|---|---|---|---|
| `POST` | `/api/auth/login` | Public | Authenticate username/password and receive JWT tokens |
| `POST` | `/api/auth/refresh` | Public | Exchange valid refresh token for a new access token |
| `POST` | `/api/auth/logout` | Authenticated | Blacklist JWT in Caffeine cache and delete refresh token |
| `POST` | `/api/auth/change-password` | Authenticated | Verify current password and update to new BCrypt hash |
| `GET` | `/api/v1/analytics/summary` | Authenticated | Live cluster metrics (Total Queries, Block Rate, Avg Latency) |
| `GET` | `/api/v1/analytics/traffic` | Authenticated | 24-hour time-series query trends |
| `GET` | `/api/v1/analytics/categories` | Authenticated | Query volume breakdown by security category |
| `GET` | `/api/v1/analytics/top-blocked` | Authenticated | Top 50 blocked malicious domains |
| `GET` | `/api/rules` | Authenticated | List all active domain filtering rules |
| `POST` | `/api/rules` | Admin / Operator | Create or update a domain filtering rule |
| `DELETE`| `/api/rules/{domain}` | Admin / Operator | Remove domain filtering rule |
| `GET` | `/api/v1/domains` | Authenticated | List custom DNS domain overrides |
| `POST` | `/api/v1/domains` | Admin / Operator | Add custom DNS record override |
| `DELETE`| `/api/v1/domains/{id}` | Admin / Operator | Delete custom DNS record override |
| `GET` | `/api/users` | Admin Only | List all registered user accounts |
| `POST` | `/api/users` | Admin Only | Provision a new user account |
| `DELETE`| `/api/users/{id}` | Admin Only | Delete user and cascade delete active sessions |
| `GET` | `/api/v1/resolver/config` | Authenticated | Get current resolver node count and target version |
| `PUT` | `/api/v1/resolver/count` | Admin Only | Scale resolver cluster worker node count |
| `PUT` | `/api/v1/resolver/version` | Admin Only | Trigger zero-downtime rolling software upgrade |

---

## 🚀 How to Run

### 1. Configuration (`.env`)
Create a `.env` file in `dnsfilt-admin-backend/`:

```dotenv
ADMIN_PORT=9090
SPRING_PROFILES_ACTIVE=prod

# Oracle ATP Database Connection (TCPS)
DB_URL=jdbc:oracle:thin:@(description=(retry_count=20)(retry_delay=3)(address=(protocol=tcps)(port=1522)(host=adb.ap-mumbai-1.oraclecloud.com))(connect_data=(service_name=your_service_high.adb.oraclecloud.com))(security=(ssl_server_dn_match=yes)))
DB_USERNAME=ADMIN
DB_PASSWORD=your_database_password
DB_DRIVER=oracle.jdbc.OracleDriver

# Redis URL (For instant resolver invalidation)
REDIS_URL=redis://appuser:your_redis_password@10.0.0.78:6379

# Orchestrator Webhook Target
ORCHESTRATOR_URL=http://10.0.0.78:9095/internal/reconcile

# Security & JWT Token Expiration
JWT_SECRET=dnsfilt-admin-backend-jwt-secret-key-32bytes-long-2026
JWT_EXPIRATION_MS=86400000

# 14-Day Gzip Rolling Logs
LOG_DIR=./logs
LOG_RETENTION_DAYS=14

# JVM Memory Profile (500MB Container Limit)
JAVA_OPTS=-Xms128m -Xmx320m -XX:MaxMetaspaceSize=128m -XX:+UseG1GC
```

### 2. Build & Run Locally with Gradle
```bash
cd dnsfilt-admin-backend

# Automatically builds Angular UI and Spring Boot jar
gradle build -x test

# Run application locally
java -jar build/libs/dnsfilt-admin-backend-1.0-SNAPSHOT.jar
```

### 3. Run with Docker Compose
```bash
docker compose up -d --build
```

### 4. Run Standalone with Docker
```bash
docker run -d \
  --name dnsfilt-admin-backend \
  --restart unless-stopped \
  -p 9090:8080 \
  --add-host kafka-server:host-gateway \
  --add-host host.docker.internal:host-gateway \
  -v $(pwd)/logs:/app/logs \
  --env-file .env \
  --memory 500M \
  ikaushikpal/dnsfilt-admin-backend:latest
```

---

## 🔧 Troubleshooting Guide

### 1. `Cannot open Redis connection due invalid URI`
- **Cause**: Redis password contains `//` or special URL characters.
- **Fix**: [`RuleService.java`](file:///Users/kaushikpal/Desktop/codes/projects/dnsfilt/dnsfilt-admin-backend/src/main/java/com/dnsfilt/dnsadmin/service/RuleService.java) and [`BlocklistSyncService.java`](file:///Users/kaushikpal/Desktop/codes/projects/dnsfilt/dnsfilt-admin-backend/src/main/java/com/dnsfilt/dnsadmin/service/BlocklistSyncService.java) implement robust token parsing with UTF-8 URL decoding, eliminating standard `java.net.URI` errors.

### 2. `ORA-02292: integrity constraint violated - child record found`
- **Cause**: Attempting to delete a user who still has active refresh tokens in the `refresh_tokens` table.
- **Fix**: The backend automatically purges all associated tokens before executing the user delete transaction.

### 3. XML SAX Parser Crash with Oracle JDBC
- **Cause**: Legacy `logback-spring.xml` conflicted with Oracle JDBC's embedded `JXSAXParserFactory`.
- **Fix**: Migrated entirely to native Spring Boot YAML logging in `application.yml` with 14-day `.log.gz` rolling policy.

---

## 📄 License
Licensed under the [MIT License](../LICENSE).
