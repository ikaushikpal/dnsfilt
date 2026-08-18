# ⚡ dnsfilt-resolver: High-Throughput Java 21 DNS Resolution & Policy Engine

[![Java 21](https://img.shields.io/badge/Java-21%20LTS-orange?style=flat-square&logo=openjdk)](https://openjdk.org/projects/jdk/21/)
[![Netty / NIO](https://img.shields.io/badge/Networking-NIO%20%2F%20Virtual%20Threads-brightgreen?style=flat-square)](https://openjdk.org/jeps/444)
[![Caffeine L1 Cache](https://img.shields.io/badge/Cache-Caffeine%20L1-blue?style=flat-square)](https://github.com/ben-manes/caffeine)
[![Kafka Streaming](https://img.shields.io/badge/Kafka-Protobuf%20%2B%20Zstd-purple?style=flat-square&logo=apachekafka)](https://kafka.apache.org/)

`dnsfilt-resolver` is the core, low-latency DNS resolution and security enforcement microservice of the DNSFilt platform. Written in modern **Java 21**, it utilizes **Virtual Threads (Project Loom)** to process 50,000+ concurrent UDP/TCP queries per second per node with sub-millisecond filtering latency.

---

## 👋 A Note from the Author

> Hi! I'm **Kaushik**, the developer behind **DNSFilt**. I designed `dnsfilt-resolver` to demonstrate how Java 21 Virtual Threads and multi-tier memory caching can outperform traditional C/Go resolvers while maintaining enterprise-grade safety.
>
> 🔍 **I am currently looking for new software engineering opportunities.** If you find this project interesting or well-architected, and your team is hiring (or you can provide a referral), I'd love to connect with you. Feel free to reach out via GitHub or on [**LinkedIn**](https://www.linkedin.com/in/ikaushikpal).
>
> ⭐ *Every star ⭐, issue, or referral means a lot — thank you for your support!*

---

## 💡 What is `dnsfilt-resolver`?

`dnsfilt-resolver` acts as a high-speed protective DNS nameserver. It listens on port 2053 (UDP & TCP), inspects every DNS query against an in-memory threat blocklist, and resolves legitimate domains through upstream recursive forwarders (Cloudflare `1.1.1.1` & Google `8.8.8.8`).

### Core Features:
- **🚀 Virtual Thread Socket Engine**: Spawns lightweight green threads per DNS packet, eliminating thread pool bottlenecks and context-switching overhead.
- **⚡ Dual-Tier Caching Pipeline**:
  - **L1 Fast-Path**: In-memory Caffeine Cache (`< 0.05ms` lookup).
  - **L2 Distributed Cache**: Redis / Valkey lookup (`~ 1ms`).
- **🛡️ Real-Time Policy Enforcement**: Instant sinkholing (`0.0.0.0`) of malicious domains with near-instant Redis Pub/Sub rule invalidation.
- **📦 Compressed Telemetry Batching**: Buffers queries into 10-minute analytics windows, serialized via Google Protocol Buffers and compressed using Zstandard (Zstd) before publishing to Kafka.

---

## 🎯 Why `dnsfilt-resolver`?

1. **Eliminate OS Thread Exhaustion**: Traditional Java thread-per-request architectures consume 1MB of stack per thread. Virtual threads reduce this footprint to a few hundred bytes, enabling hundreds of thousands of concurrent sockets on modest hardware.
2. **Zero-Lock Singleton Services**: Implements the **Bill Pugh Singleton Pattern** across `CacheService`, `KafkaProducerService`, and `RedisService` for thread-safe, lock-free access.
3. **Absorb Traffic Surges**: Configures 8MB OS UDP socket buffers (`SO_RCVBUF` / `SO_SNDBUF`) to prevent packet drops during microsecond spikes.

---

## 🔄 Query Processing Lifecycle

```text
Incoming UDP / TCP Query (Port 2053)
         │
         ▼
[Step 1] L1 Fast Path: Caffeine In-Memory Cache (< 0.05ms)
         │ ──► [HIT] Returns cached DNS Response immediately
         ▼ [MISS]
[Step 2] Security Rule Evaluation: In-Memory Decision Layer
         │ ──► [MATCHED BLOCK] Returns Sinkhole Record (0.0.0.0 / NXDOMAIN)
         ▼ [ALLOWED]
[Step 3] L2 Distributed Cache: Redis Cache Lookup (~ 1ms)
         │ ──► [HIT] Backfill L1 Cache & Return
         ▼ [MISS]
[Step 4] Upstream Forwarding: Cloudflare (1.1.1.1) / Google (8.8.8.8) with EDNS0
         │ ──► Store in L1 Caffeine (5 min TTL) & Async L2 Redis (300s TTL)
         ▼
[Step 5] Async Kafka Pipeline: Protobuf Zstd Batch Ingestion (Non-blocking)
```

---

## 🚀 How to Run

### 1. Configuration (`.env`)
Create a `.env` file in `dnsfilt-resolver/`:

```dotenv
RESOLVER_PORT=2053
DNS_PORT=2053

# Redis L2 & Blocklist Sync
REDIS_HOST=host.docker.internal
REDIS_PORT=6379
REDIS_USER=appuser
REDIS_PASSWORD=your_redis_password
REDIS_SSL=false

# Kafka Streaming Broker
KAFKA_BOOTSTRAP_SERVERS=kafka-server:9092
KAFKA_TOPIC=dns.analytics.10min
KAFKA_SECURITY_PROTOCOL=SASL_PLAINTEXT
KAFKA_SASL_MECHANISM=PLAIN
KAFKA_SASL_USERNAME=kafkaapp
KAFKA_SASL_PASSWORD=your_kafka_password

# JVM Memory Profile (200MB Container Footprint)
JAVA_OPTS=-Xms64m -Xmx160m -XX:MaxMetaspaceSize=40m -XX:+UseG1GC
```

### 2. Run with Docker Compose
```bash
cd dnsfilt-resolver
docker compose up -d --build
```

### 3. Run Standalone with Docker
```bash
docker run -d \
  --name dnsfilt-resolver \
  --restart unless-stopped \
  -p 2053:2053/udp \
  -p 2053:2053/tcp \
  --add-host kafka-server:host-gateway \
  --add-host host.docker.internal:host-gateway \
  --env-file .env \
  --memory 200M \
  ikaushikpal/dnsfilt-resolver:latest
```

---

## 🧪 Testing Resolution with `dig`

```bash
# 1. Test Standard A Record Resolution
dig @127.0.0.1 -p 2053 google.com

# 2. Test IPv6 (AAAA) Resolution
dig @127.0.0.1 -p 2053 cloudflare.com AAAA

# 3. Test Sinkholed Threat Domain (Should return 0.0.0.0)
dig @127.0.0.1 -p 2053 malware.test.com
```

---

## 🔧 Troubleshooting Guide

### 1. `Kafka bootstrap host 'kafka-server' is not resolvable via DNS`
- **Cause**: The container cannot find `kafka-server` in its internal DNS.
- **Fix**: Pre-flight DNS validation prevents the resolver from crashing, but to stream telemetry, ensure `--add-host kafka-server:host-gateway` is passed in Docker, and the SSH tunnel on the host is bound to `0.0.0.0:9092`.

### 2. `Redis connection failed: Cannot open Redis connection due invalid URI`
- **Cause**: Redis password contains special characters (like `//` or `@`) that break URI schemes.
- **Fix**: The resolver connects using explicit host/port/user/password properties (`REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`), bypassing fragile URI parsing.

### 3. UDP Port Permission Denied (Port 53)
- **Cause**: Binding to ports below 1024 on Linux requires root/`CAP_NET_BIND_SERVICE`.
- **Fix**: The resolver listens on unprivileged port **`2053`**. Use HAProxy or an `iptables` redirect on the host to forward public port `53` to `2053`.

---

## 📄 License
Licensed under the [MIT License](../LICENSE).
