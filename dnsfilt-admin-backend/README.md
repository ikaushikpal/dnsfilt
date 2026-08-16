# DNSFilt Admin Backend & Gateway 🛡️

The central administrative REST API server and web gateway for the DNSFilt platform. Built on **Java 21**, **Spring Boot 3.3**, **Spring Security**, **Caffeine in-memory caching**, and **Oracle Autonomous Database (ATP 23ai/26ai)**.

---

## 🎯 Architecture Overview

```text
[ Browser / Angular UI (dnsfilt-ui) ]
                  │
                  ▼ HTTP / HTTPS (Port 8080)
┌─────────────────────────────────────────────────────────────┐
│                 dnsfilt-admin-backend                        │
│                                                             │
│  [ Web SPA Handler ] ──► Serves compiled Angular SSR/SPA    │
│  [ Security Filter ] ──► JWT Auth & Caffeine Blacklist      │
│  [ Caffeine Caches ] ──► 30MB Hourly / Monthly Query Caches │
│  [ Controllers ]     ──► REST APIs for Rules, Users, Stats  │
│  [ Repositories ]    ──► Oracle ATP TCPS (Wallet-less)      │
└──────────────────────────────┬──────────────────────────────┘
                               │
               ┌───────────────┴───────────────┐
               ▼                               ▼
     [ Oracle ATP Database ]           [ Redis / Valkey ]
    (Real-time Query Analytics)      (Blocklist Sync Push)
```

---

## ⚡ Core Features

- **Unified Frontend & Backend Server**: Serves compiled Angular 18 static assets from `classpath:/static/` with transparent Single Page Application (SPA) HTML5 pushState routing fallback (`SpaWebMvcConfig`).
- **In-Memory Caffeine Caching (Zero Redis Web Load)**:
  - **`tokenBlacklist`**: ~50KB (~2,000 tokens, 24h TTL) for instantaneous JWT revocation on logout.
  - **`hourlyAnalytics`**: ~30MB bounded LRU cache (5-min TTL) for sub-millisecond dashboard queries.
  - **`monthlyAnalytics`**: 24h TTL for historical rollups.
  - **`rulesCache` / `domainsCache` / `resolverConfigCache`**: Automatically evicted via `@CacheEvict` on any mutations.
- **Enterprise Role-Based Access Control (RBAC)**:
  - `ROLE_ADMIN`: Full system control (user creation, resolver scaling, rule deletion).
  - `ROLE_OPERATOR`: Operational management (add/delete rules, domain records, inspect analytics).
  - `ROLE_VIEWER`: Read-only audit access to live analytics and active rule tables.
- **Strongly Typed DTO Architecture**: All endpoints use typed Java records (zero untyped `Map<String, String>` payloads).
- **Oracle ATP TCPS Support**: Connects seamlessly to Oracle Autonomous Database 23ai/26ai with retry policies and SSL server DN verification.

---

## 📡 REST API Reference

| Method | Endpoint | Access Level | Description |
|---|---|---|---|
| `POST` | `/api/auth/login` | Public | Authenticate and obtain JWT access + refresh tokens |
| `POST` | `/api/auth/refresh` | Public | Refresh expired access token |
| `POST` | `/api/auth/logout` | Authenticated | Immediately blacklist current JWT & refresh tokens in Caffeine |
| `GET` | `/api/v1/analytics/summary` | Admin / Operator / Viewer | Global query counts, block rate, cache hit %, latency, active clients |
| `GET` | `/api/v1/analytics/traffic` | Admin / Operator / Viewer | 24-hour time-series query trends |
| `GET` | `/api/v1/analytics/categories` | Admin / Operator / Viewer | Query volume breakdown by security category |
| `GET` | `/api/v1/analytics/top-blocked`| Admin / Operator / Viewer | Top 50 blocked domains ranked by frequency |
| `GET` | `/api/v1/analytics/top-clients`| Admin / Operator / Viewer | Active clients ranked with threat risk badges (🔴/🟡/🟢) |
| `GET` | `/api/rules` | Admin / Operator / Viewer | List all active domain filtering rules |
| `POST` | `/api/rules` | Admin / Operator | Create or update a domain filtering rule |
| `DELETE`| `/api/rules/{domain}` | Admin / Operator | Remove a domain filtering rule |
| `GET` | `/api/v1/domains` | Admin / Operator / Viewer | List custom DNS domain overrides |
| `POST` | `/api/v1/domains` | Admin / Operator | Create custom DNS domain override |
| `DELETE`| `/api/v1/domains/{id}` | Admin / Operator | Delete custom DNS domain override |
| `GET` | `/api/users` | Admin Only | List registered user accounts |
| `POST` | `/api/users` | Admin Only | Provision new user account (`ROLE_ADMIN`, `ROLE_OPERATOR`, `ROLE_VIEWER`)|
| `DELETE`| `/api/users/{id}` | Admin Only | Delete user account |
| `GET` | `/api/v1/resolver/config` | Admin / Operator / Viewer | Get resolver cluster desired count and version |
| `PUT` | `/api/v1/resolver/count` | Admin Only | Scale resolver cluster desired worker count |
| `PUT` | `/api/v1/resolver/version` | Admin Only | Update resolver desired engine version |

---

## ⚙️ Configuration (`.env`)

```env
PORT=8080
SPRING_PROFILES_ACTIVE=prod

# Database Configuration (Oracle ATP Database)
DB_URL="jdbc:oracle:thin:@(description=(retry_count=20)(retry_delay=3)(address=(protocol=tcps)(port=1522)(host=adb.ap-mumbai-1.oraclecloud.com))(connect_data=(service_name=your_service_high.adb.oraclecloud.com))(security=(ssl_server_dn_match=yes)))"
DB_USERNAME=ADMIN
DB_PASSWORD=your_db_password
DB_DRIVER=oracle.jdbc.OracleDriver

# Redis Configuration (For resolver notification push)
REDIS_URL=redis://appuser:your_redis_password@host.docker.internal:6379

# JWT Security
JWT_SECRET=your_super_secret_jwt_key_minimum_32_bytes_long
JWT_EXPIRATION_MS=86400000

# JVM Memory Tuning (500MB Container Limit: 320MB Heap + 128MB Metaspace)
JAVA_OPTS=-Xms128m -Xmx320m -XX:MaxMetaspaceSize=128m -XX:+UseG1GC
```

---

## 🐳 Docker Deployment

```bash
# Build and run container
cd dnsfilt-admin-backend
docker compose up --build -d

# Check live logs
docker logs -f dnsfilt-admin-backend
```
