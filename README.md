# 🛡️ DNSFilt — Enterprise Event-Driven DNS Security Gateway & Analytics Platform

[![Java 21](https://img.shields.io/badge/Java-21%20Virtual%20Threads-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/projects/loom/)
[![Spring Boot 3](https://img.shields.io/badge/Spring%20Boot-3.3.5-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Angular 18](https://img.shields.io/badge/Angular-18%20SSR-DD0031?style=for-the-badge&logo=angular&logoColor=white)](https://angular.dev/)
[![Apache Kafka](https://img.shields.io/badge/Apache%20Kafka-Streaming%20Telemetry-231F20?style=for-the-badge&logo=apachekafka&logoColor=white)](https://kafka.apache.org/)
[![Oracle ATP](https://img.shields.io/badge/Oracle%20Cloud-ATP%2023ai-F80000?style=for-the-badge&logo=oracle&logoColor=white)](https://www.oracle.com/autonomous-database/)
[![Docker](https://img.shields.io/badge/Docker-Multi--Arch%20Containers-2496ED?style=for-the-badge&logo=docker&logoColor=white)](https://www.docker.com/)

**DNSFilt** is a distributed, high-performance security DNS filtering platform engineered for ultra-low latency (`< 0.1ms`), real-time threat mitigation, streaming telemetry analytics, and zero-downtime policy synchronization. Built from the ground up with modern distributed systems principles on **Java 21 Virtual Threads**, **Caffeine L1 / Valkey Redis L2 Multi-Tier Caching**, **Zstandard-compressed Protocol Buffers**, **Apache Kafka Event Streams**, **Oracle Autonomous Database 23ai/26ai Materialized Views**, and **Angular 18 SSR**.

---

## 🧭 Microservices & Subproject Navigation

| Component | Description | Subproject Documentation |
|---|---|---|
| ⚡ **DNS Resolver Engine** | Ultra-fast UDP DNS server (< 150MB RAM, 100K+ QPS) | [📖 `dnsfilt-resolver/README.md`](file:///Users/kaushikpal/Desktop/codes/projects/dnsfilt/dnsfilt-resolver/README.md) |
| 🛡️ **Admin Backend & Gateway** | Spring Boot 3.3 REST API server, JWT auth & Caffeine cache | [📖 `dnsfilt-admin-backend/README.md`](file:///Users/kaushikpal/Desktop/codes/projects/dnsfilt/dnsfilt-admin-backend/README.md) |
| 📊 **Analytics & Retention Engine** | Real-time Kafka consumer, Zstd Protobuf decompression & Oracle rollups | [📖 `dnsfilt-analytics/README.md`](file:///Users/kaushikpal/Desktop/codes/projects/dnsfilt/dnsfilt-analytics/README.md) |
| 🌐 **Frontend UI Console** | Angular 18 SSR/SSG dashboard, real-time charts & client threat telemetry | [📖 `dnsfilt-ui/README.md`](file:///Users/kaushikpal/Desktop/codes/projects/dnsfilt/dnsfilt-ui/README.md) |
| 🌐 **Render Gateway Proxy** | Cloud Nginx reverse proxy with SNI forwarder bypassing enterprise firewalls | [📖 `dnsfilt-render-proxy/README.md`](file:///Users/kaushikpal/Desktop/codes/projects/dnsfilt/dnsfilt-render-proxy/README.md) |
| 🤖 **Resolver Orchestrator** | Python microservice managing resolver cluster scaling & health reconcilers | [📖 `dnsfilt-orchestrator/README.md`](file:///Users/kaushikpal/Desktop/codes/projects/dnsfilt/dnsfilt-orchestrator/README.md) |
| ⚙️ **CI/CD Pipelines** | GitHub Actions multi-architecture build matrix & SemVer release automation | [📖 `.github/workflows/README.md`](file:///Users/kaushikpal/Desktop/codes/projects/dnsfilt/.github/workflows/README.md) |

---

## 🏗️ System Architecture & Global Data Pipeline

```text
[ Client DNS Queries (macOS, Windows, Linux, Routers) ]
                            │
                            ▼ UDP Port 53 / 2053
┌────────────────────────────────────────────────────────────────────────┐
│                        dnsfilt-resolver Cluster                        │
│                                                                        │
│  [ Step 1: L1 Caffeine In-Memory Cache ] ──► (< 0.05ms Instant Hit)    │
│  [ Step 2: In-Memory Rule Snapshot ]     ──► Sinkholes Malware/Ads     │
│  [ Step 3: L2 Valkey / Redis Cache ]     ──► (~ 1.2ms Distributed Hit) │
│  [ Step 4: Upstream Recursive Forward ]  ──► Cloudflare (1.1.1.1) EDNS0│
│  [ Step 5: Kafka Batch Producer ]        ──► 10-Min Zstd Protobuf Stream│
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                       Topic: dns.analytics.10min
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                       dnsfilt-analytics Engine                         │
│                                                                        │
│  [ Kafka SASL Consumer ]   ──► Ingests 10-minute binary streams        │
│  [ Native Zstd Decoder ]   ──► Decompresses Protobuf records in RAM    │
│  [ Aggregator & Upsert ]   ──► Real-time hourly stats aggregation      │
│  [ Retention Scheduler ]   ──► Monthly rollups & auto-compaction       │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ JDBC (TCPS Wallet-less)
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│             Oracle Autonomous Database (ATP 23ai / 26ai)               │
│                                                                        │
│  - resolver_hourly_stats / client_hourly_stats                         │
│  - mv_resolver_daily_summary (FAST REFRESH ON COMMIT with MV Logs)     │
│  - mv_top_threat_categories  (COMPLETE REFRESH ON DEMAND)              │
│  - resolver_monthly_stats    (Precomputed Monthly Aggregates)          │
└───────────────────────────────────▲────────────────────────────────────┘
                                    │ JPA Queries (Cached with Caffeine)
┌───────────────────────────────────┴────────────────────────────────────┐
│                      dnsfilt-admin-backend (Port 8080)                 │
│                                                                        │
│  [ Embedded Angular Static Server ] ──► Delivers UI SSR/SPA on /       │
│  [ Caffeine Query & Token Caches ]  ──► Zero Redis web query overhead  │
│  [ Role-Based Access Control ]      ──► Admin, Operator & Viewer roles │
│  [ Redis Rule Sync Publisher ]      ──► Real-time < 1s rule updates    │
└────────────────────────────────────────────────────────────────────────┘
```

---

## ⚡ Core Technical Highlights

1. **Java 21 Virtual Threads (Project Loom)**: Replaces traditional thread-per-request blocking architectures with lightweight user-space fibers capable of handling tens of thousands of concurrent UDP packet streams.
2. **Multi-Tiered L1/L2 Caching Pipeline**:
   - **L1 In-Memory Near-Cache (Caffeine)**: Delivers sub-millisecond lookups with near-zero lock contention.
   - **L2 Distributed Cache (Valkey / Redis)**: Synchronizes resolved domain names and active block rules across multi-datacenter instances.
3. **In-Memory Caffeine API Optimization (Zero Redis Web Load)**:
   - **`tokenBlacklist`**: 50KB LRU cache with 24h TTL for instantaneous JWT and refresh token revocation upon logout.
   - **`hourlyAnalytics`**: 30MB bounded LRU cache (5-min TTL) serving dashboard analytics at microsecond speed without hitting the primary database.
4. **Oracle ATP Materialized Views & Fast Refresh**:
   - Delta change tracking with `CREATE MATERIALIZED VIEW LOG`.
   - Incremental daily rollup compilation via `REFRESH FAST ON COMMIT`.
5. **Unified Frontend & Backend Hosting**:
   - Spring Boot serves the pre-rendered Angular 18 static bundle directly from `classpath:/static/` with transparent SPA HTML5 routing fallback.
6. **Multi-Platform CI/CD with Docker Buildx**:
   - Full automated release pipelines compiling cross-platform `linux/amd64` and `linux/arm64` container images.

---

## 🚀 Getting Started

### Prerequisites
- **Docker Engine 24+** & **Docker Compose v2**
- **Java 21 JDK** & **Node.js 20+**
- **Oracle Database (ATP or 23ai)**
- **Apache Kafka** (Local or Managed Cloud)
- **Redis / Valkey 7+**

### 1. Clone the Repository
```bash
git clone https://github.com/iamkaushikpal/dnsfilt.git
cd dnsfilt
```

### 2. Configure Environment Variables
Each subservice uses a standard `.env` configuration file:
- `dnsfilt-resolver/.env`
- `dnsfilt-admin-backend/.env`
- `dnsfilt-analytics/.env`
- `dnsfilt-render-proxy/.env`

### 3. Launch the Complete Ecosystem
```bash
# Start Resolver
cd dnsfilt-resolver && docker compose up -d

# Start Analytics Stream Consumer
cd ../dnsfilt-analytics && docker compose up -d

# Start Admin Backend & Web Gateway (Port 8080)
cd ../dnsfilt-admin-backend && docker compose up -d

# Start Render Gateway Proxy (Port 80)
cd ../dnsfilt-render-proxy && docker compose up -d
```

### 4. Access the Dashboard
Navigate to `http://localhost:8080` (or `https://dnsfilt.mooo.com`) to access the live analytics console.

---

## 👨‍💻 About the Author & Looking for Opportunities

<div align="center">
  <img src="https://github.com/iamkaushikpal.png" width="120" height="120" style="border-radius: 50%;" alt="Kaushik Pal"/>
  <h3>Kaushik Pal</h3>
  <p><b>Lead Software Engineer & Distributed Systems Architect</b></p>
</div>

> **📢 Career Availability & Looking for Roles:**  
> I am actively seeking senior/lead technical opportunities as a **Lead Backend Engineer**, **Senior Software Engineer (Java / Distributed Systems)**, or **Platform / Cloud Architect**. Passionate about designing resilient high-throughput microservices, sub-millisecond low-latency engines, event-driven streaming architectures, and modern full-stack web platforms.

### 📬 Connect with Me:
- 💼 **LinkedIn**: [linkedin.com/in/iamkaushik2014](https://linkedin.com/in/iamkaushik2014)
- 🐙 **GitHub**: [github.com/iamkaushikpal](https://github.com/iamkaushikpal)
- ✉️ **Email**: [kaushikpal2014@gmail.com](mailto:kaushikpal2014@gmail.com)
- 🌐 **Portfolio / Platform**: [dnsfilt.mooo.com](https://dnsfilt.mooo.com)

---

## 📜 License
This project is open-source and available under the **MIT License**.
