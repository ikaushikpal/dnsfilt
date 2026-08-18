# 🛡️ DNSFilt: Distributed Cloud-Native DNS Firewall & Real-Time Analytics Engine

[![Build Status](https://img.shields.io/badge/Build-Passing-emerald?style=flat-square&logo=github-actions)](https://github.com/ikaushikpal/dnsfilt)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg?style=flat-square)](LICENSE)
[![Java 21](https://img.shields.io/badge/Java-21%20LTS-orange?style=flat-square&logo=openjdk)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot 3](https://img.shields.io/badge/Spring%20Boot-3.3.5-brightgreen?style=flat-square&logo=springboot)](https://spring.io/projects/spring-boot)
[![Angular 18](https://img.shields.io/badge/Angular-18.0-red?style=flat-square&logo=angular)](https://angular.dev/)
[![Docker Multi-Arch](https://img.shields.io/badge/Docker-AMD64%20%7C%20ARM64-blue?style=flat-square&logo=docker)](https://hub.docker.com/u/ikaushikpal)

**DNSFilt** is a high-throughput, enterprise-grade, distributed DNS firewall, policy engine, and analytics platform. Built with Java 21 Virtual Threads (Project Loom), Kafka streaming, Zstandard Protobuf batching, Oracle Autonomous Database 23ai, and an automated Python controller with zero-downtime HAProxy load balancing.

---

## 👋 A Note from the Author

> Hi! I'm **Kaushik**, the developer behind **DNSFilt**. I built this project to explore high-throughput distributed systems, Java 21 virtual threads, stream-processing analytics rollups, and production-grade zero-downtime orchestration — all running on a modern cloud architecture.
>
> 🔍 **I am currently looking for new software engineering opportunities.** If you find this project interesting, innovative, or well-engineered, and your company is hiring (or you can provide a referral), I would genuinely appreciate connecting with you. Feel free to reach out via GitHub or connect with me directly on [**LinkedIn**](https://www.linkedin.com/in/ikaushikpal).
>
> ⭐ *Every star, issue, discussion, PR, or referral means a lot — thank you for stopping by!*

---

## 🧭 Table of Contents

- [What is DNSFilt?](#-what-is-dnsfilt)
- [Why DNSFilt? (Design Motivation)](#-why-dnsfilt-design-motivation)
- [System Architecture](#-system-architecture)
- [Microservices Overview](#-microservices-overview)
- [How to Run (Quickstart)](#-how-to-run-quickstart)
- [Troubleshooting & Common Gotchas](#-troubleshooting--common-gotchas)
- [License](#-license)

---

## 💡 What is DNSFilt?

DNSFilt is an end-to-end protective DNS gateway that sits between client devices and the upstream internet. It intercepts DNS queries on port 53 / 2053 (UDP/TCP), checks them against an in-memory cached rule engine with sub-millisecond latency, logs real-time security events into an event streaming pipeline, and provides an administrative interface for threat intelligence, custom domain overrides, and dynamic cluster auto-scaling.

### Key Capabilities:
- **⚡ Sub-Millisecond Filtering**: Powered by Java 21 virtual threads and Caffeine L1 in-memory caches, capable of handling 50,000+ QPS per node.
- **🔒 Dynamic Threat Blocking**: Real-time domain and category blocking with atomic Redis pub/sub synchronization.
- **📊 10-Minute Batch Analytics**: High-throughput Kafka ingestion compressing telemetry via Protobuf + Zstandard (Zstd) and aggregating rollups into Oracle Autonomous Database 23ai.
- **🔄 Zero-Downtime Rolling Upgrades**: Custom Python orchestrator managing HAProxy reload via native `SIGUSR2` socket signals.
- **🎨 Glassmorphic Single-Page Application**: Angular 18 frontend with live UTC synchronization, threat explorer, real-time node scaling controls, and self-service credential management.

---

## 🎯 Why DNSFilt? (Design Motivation)

| Challenge | Traditional Approach | DNSFilt Solution |
|---|---|---|
| **High Concurrency Overhead** | Heavy OS threads (1MB stack per thread) limiting socket scalability. | **Java 21 Virtual Threads**: Millions of lightweight concurrent green threads with near-zero memory footprint. |
| **Telemetry Write Amplification** | Writing every single DNS query synchronously to SQL causes database connection pool exhaustion. | **Kafka + Zstd Protobuf Rollup**: Resolver batches 10-minute micro-aggregations with 95%+ compression ratio before atomic persistence. |
| **Cache Invalidation Latency** | Polling database every few minutes leaves a vulnerability window when adding new threat domains. | **Atomic Dual-Tier Caching**: Caffeine L1 in-memory cache on each resolver node invalidated instantly via Redis Pub/Sub sync. |
| **Upgrades & Scaling Downtime** | Restarting DNS resolvers causes dropped UDP queries and DNS resolution failure on client machines. | **HAProxy Zero-Downtime Rolling Scaler**: Python reconciler spawns new worker nodes on dynamic ports and triggers graceful `SIGUSR2` reloads. |

---

## 🏗️ System Architecture

```mermaid
flowchart TD
    subgraph Clients["Clients & Edge Devices"]
        C1["Workstations & Servers"]
        C2["Mobile Devices / Routers"]
    end

    subgraph LoadBalancer["Load Balancing Layer"]
        HAP["HAProxy (Port 53 / 2053 UDP & TCP)"]
    end

    subgraph ResolverCluster["DNS Resolver Cluster (Worker Nodes)"]
        R1["dnsfilt-resolver-p2054"]
        R2["dnsfilt-resolver-p2055"]
        R3["dnsfilt-resolver-p2056"]
    end

    subgraph EventStreaming["Telemetry & Analytics Layer"]
        KAFKA["Kafka Broker (Topic: dns.analytics.10min)"]
        ANALYTICS["dnsfilt-analytics (Rollup Consumer)"]
        ORACLE[("Oracle Autonomous Database 23ai")]
    end

    subgraph Management["Control Plane & UI"]
        BACKEND["dnsfilt-admin-backend (Spring Boot REST + Security)"]
        UI["dnsfilt-ui (Angular 18 Single-Page Application)"]
        ORCH["dnsfilt-orchestrator (Python Controller & Scaler)"]
        REDIS[("Redis Blocklist Store")]
    end

    C1 & C2 -->|"DNS Queries (UDP/TCP)"| HAP
    HAP -->|"Round-Robin Distribution"| R1 & R2 & R3
    R1 & R2 & R3 -->|"Protobuf + Zstd Batches"| KAFKA
    KAFKA --> ANALYTICS
    ANALYTICS -->|"Atomic 10-Min Upserts"| ORACLE
    
    UI -->|"HTTPS REST API"| BACKEND
    BACKEND -->|"Read/Write Analytics & Users"| ORACLE
    BACKEND -->|"Push Invalidation"| REDIS
    R1 & R2 & R3 -.->|"Sync Blocked Domains"| REDIS

    BACKEND -->|"Trigger Push Webhook"| ORCH
    ORCH -->|"Docker SDK Scaling"| ResolverCluster
    ORCH -->|"SIGUSR2 Zero-Downtime Reload"| HAP
```

---

## 📦 Microservices Overview

| Microservice | Technology Stack | Role & Responsibility |
|---|---|---|
| [`dnsfilt-resolver`](file:///Users/kaushikpal/Desktop/codes/projects/dnsfilt/dnsfilt-resolver) | Java 21, Netty/NIO, Caffeine, Jedis, Kafka | Core high-throughput UDP/TCP DNS resolution and policy enforcement engine. |
| [`dnsfilt-analytics`](file:///Users/kaushikpal/Desktop/codes/projects/dnsfilt/dnsfilt-analytics) | Spring Boot 3, Spring Kafka, Zstd-JNI, Oracle JDBC | Consumes compressed analytics batches, aggregates 10-minute metrics, and saves to Oracle DB. |
| [`dnsfilt-admin-backend`](file:///Users/kaushikpal/Desktop/codes/projects/dnsfilt/dnsfilt-admin-backend) | Spring Boot 3, Spring Security, JWT, Oracle ATP | Central administrative REST API, SuperAdmin RBAC, and static host for the Angular UI. |
| [`dnsfilt-orchestrator`](file:///Users/kaushikpal/Desktop/codes/projects/dnsfilt/dnsfilt-orchestrator) | Python 3.11, FastAPI, APScheduler, Docker SDK | Autonomous cluster reconciler, HAProxy configuration generator, and rolling upgrade manager. |
| [`dnsfilt-ui`](file:///Users/kaushikpal/Desktop/codes/projects/dnsfilt/dnsfilt-ui) | Angular 18 (Signals), TailwindCSS, Chart.js | Responsive administrative dashboard, live UTC clock, threat explorer, and cluster scaling UI. |
| [`dnsfilt-render-proxy`](file:///Users/kaushikpal/Desktop/codes/projects/dnsfilt/dnsfilt-render-proxy) | NGINX Alpine | Production edge reverse proxy with SSL termination and WebSocket/HTTP upgrade support. |

---

## 🚀 How to Run (Quickstart)

### 1. Prerequisites
- Docker Engine 24+ or Podman 5+
- Java 21 JDK & Gradle 8+
- Node.js 20+ & npm

### 2. Clone the Repository
```bash
git clone https://github.com/ikaushikpal/dnsfilt.git
cd dnsfilt
```

### 3. Launch with Docker Compose
Each microservice includes its own production `docker-compose.yml`:

```bash
# 1. Start Admin Backend (Port 9090)
cd dnsfilt-admin-backend && docker compose up -d --build

# 2. Start Core DNS Resolver (Port 2053)
cd ../dnsfilt-resolver && docker compose up -d --build

# 3. Start Kafka Analytics Ingestor (Port 9091)
cd ../dnsfilt-analytics && docker compose up -d --build

# 4. Start Autonomous Cluster Orchestrator (Port 9095)
cd ../dnsfilt-orchestrator && docker compose up -d --build
```

Access the Web UI in your browser at: **`http://localhost:9090`** (or your server's mapped domain).

---

## 🔧 Troubleshooting & Common Gotchas

### 1. `ConfigException: No resolvable bootstrap urls given in bootstrap.servers`
- **Cause**: Container internal DNS could not resolve `kafka-server` or `host.docker.internal` on Linux.
- **Fix**: Verify `extra_hosts` is present in `docker-compose.yml`:
  ```yaml
  extra_hosts:
    - "kafka-server:host-gateway"
    - "host.docker.internal:host-gateway"
  ```
  If using an SSH tunnel, ensure the tunnel is listening on all interfaces (`ssh -g -L 0.0.0.0:9092:...`).

### 2. `sqlite3.OperationalError: unable to open database file` in Orchestrator
- **Cause**: Restricted root permissions on the host directory mounted to `/app/data`.
- **Fix**: Grant write permissions on the host:
  ```bash
  sudo mkdir -p /opt/platform/dnsfilt/dnsfilt-orchestrator/data
  sudo chmod -R 777 /opt/platform/dnsfilt/dnsfilt-orchestrator/data
  ```

### 3. Browser Shows Old UI After Updating
- **Cause**: Browser aggressive caching of JavaScript chunk hashes or cached Docker image layer.
- **Fix**: 
  1. Pull the fresh image on prod: `sudo docker pull ikaushikpal/dnsfilt-admin-backend:latest`
  2. Perform a hard refresh in the browser: `Ctrl + Shift + R` (Windows/Linux) or `Cmd + Shift + R` (Mac).

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
Feel free to use, modify, and distribute this codebase in your own projects!
