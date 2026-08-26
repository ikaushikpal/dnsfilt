# ⚡ dnsfilt-resolver: High-Throughput Java 26 DNS Resolution & Policy Engine

[![Java 26](https://img.shields.io/badge/Java-26-orange?style=flat-square&logo=openjdk)](https://openjdk.org/)
[![Netty / NIO](https://img.shields.io/badge/Networking-NIO%20%2F%20Virtual%20Threads-brightgreen?style=flat-square)](https://openjdk.org/jeps/444)
[![Caffeine L1 Cache](https://img.shields.io/badge/Cache-Caffeine%20L1-blue?style=flat-square)](https://github.com/ben-manes/caffeine)
[![Redis L2 Cache](https://img.shields.io/badge/Cache-Redis%20L2-red?style=flat-square&logo=redis)](https://redis.io/)
[![Kafka Streaming](https://img.shields.io/badge/Kafka-Protobuf%20%2B%20Zstd-purple?style=flat-square&logo=apachekafka)](https://kafka.apache.org/)
[![PROXY Protocol v1 & v2](https://img.shields.io/badge/Protocol-PROXY%20v1%20%26%20v2-informational?style=flat-square)](https://www.haproxy.org/download/1.8/doc/proxy-protocol.txt)

`dnsfilt-resolver` is the core, ultra-low-latency DNS resolution and security enforcement microservice of the DNSFilt platform. Written in modern **Java 26**, it utilizes **Virtual Threads (Project Loom)** to process 50,000+ concurrent UDP/TCP queries per second per node with sub-millisecond filtering latency.

---

## 👋 A Note from the Author

> Hi! I'm **Kaushik**, the developer behind **DNSFilt**. I designed `dnsfilt-resolver` to demonstrate how Java 26 Virtual Threads and multi-tier memory caching can outperform traditional C/Go resolvers while maintaining enterprise-grade safety.
>
> 🔍 **I am currently looking for new software engineering opportunities.** If you find this project interesting or well-architected, and your team is hiring (or you can provide a referral), I'd love to connect with you. Feel free to reach out via GitHub or on [**LinkedIn**](https://www.linkedin.com/in/ikaushikpal).
>
> ⭐ *Every star ⭐, issue, or referral means a lot — thank you for your support!*

---

## 💡 What is `dnsfilt-resolver`?

`dnsfilt-resolver` acts as a high-speed protective DNS nameserver. It listens on port `2053` (UDP & TCP), inspects every DNS query against an in-memory threat blocklist, and resolves legitimate domains through upstream recursive forwarders (Cloudflare `1.1.1.1` & Google `8.8.8.8`).

### Core Features:
- **🚀 Virtual Thread Socket Engine**: Spawns lightweight green threads per DNS packet, eliminating thread pool bottlenecks and context-switching overhead.
- **🛡️ Universal PROXY Protocol Support (v1 & v2)**:
  - **PROXY v1 (Text/ASCII)**: Full support for NGINX 1.20.1 upstream format `PROXY TCP4 <src-ip> <dst-ip> <src-port> <dst-port>\r\n`, as well as `PROXY TCP6` and `PROXY UNKNOWN`.
  - **PROXY v2 (Binary)**: 12-byte binary signature detection (`\r\n\r\n\0\r\nQUIT\n`) with protocol nibble validation (`DGRAM=2`, `STREAM=1`, `UNSPEC=0`) and TLV skipping.
  - **Raw DNS Fallback**: Seamless zero-copy fallback when queries arrive without a PROXY envelope.
- **⚡ Optimized Multi-Tier Caching Pipeline**:
  - **L1 In-Memory Fast-Path**: Caffeine Cache with **10-minute TTL** (`< 0.05ms` lookup).
  - **L2 Distributed Cache**: Redis / Valkey lookup with **15-minute TTL (900s)** (`~ 1ms`).
  - **Client-Facing DNS Response**: Returns standard **5-minute TTL (300s)** in DNS resource records for optimal client caching.
- **🛑 Robust Modern DNS TYPE & Opcode Safety**:
  - Encodes standard (`A`, `AAAA`, `CNAME`, `MX`, `TXT`, `PTR`, `SRV`, `SOA`) and telecom `NAPTR (35)` records.
  - Graceful handling of modern query types (such as `TYPE 65 HTTPS` or `TYPE 64 SVCB`) with `RCODE 4 (NOTIMPL)` fallback responses rather than crashing.
  - Safe parsing for Opcode (`NOTIFY=4`, `UPDATE=5`, `UNKNOWN=-1`) and RCODE preventing `ArrayIndexOutOfBoundsException`.
- **🛡️ Real-Time Policy Enforcement**: Instant sinkholing (`0.0.0.0`) of malicious domains with near-instant Redis Pub/Sub rule invalidation.
- **📦 Compressed Telemetry Batching**: Buffers queries into 10-minute analytics windows, serialized via Google Protocol Buffers and compressed using Zstandard (Zstd) before publishing to Kafka.

---

## 🎯 Why `dnsfilt-resolver`?

1. **Eliminate OS Thread Exhaustion**: Traditional Java thread-per-request architectures consume 1MB of stack per thread. Virtual threads reduce this footprint to a few hundred bytes, enabling hundreds of thousands of concurrent sockets on modest hardware.
2. **Zero-Lock Singleton Services**: Implements the **Bill Pugh Singleton Pattern** across `CacheService`, `KafkaProducerService`, and `RedisService` for thread-safe, lock-free access.
3. **Absorb Traffic Surges**: Configures 8MB OS UDP socket buffers (`SO_RCVBUF` / `SO_SNDBUF`) to prevent packet drops during microsecond spikes.
4. **Accurate Edge Client IP Auditing**: PROXY Protocol v1 & v2 extraction ensures telemetry reflects actual remote client IPs rather than Docker/Podman bridge gateway addresses (`10.88.0.1`).

---

## 🔄 Query Processing Lifecycle

```text
Incoming UDP / TCP Query (Port 2053 or via NGINX with PROXY v1/v2)
         │
         ▼
[Step 0] PROXY Protocol v1/v2 Inspection (Extract Real Client IP / Port)
         │
         ▼
[Step 1] L1 Fast Path: Caffeine In-Memory Cache (10-min TTL, < 0.05ms)
         │ ──► [HIT] Returns cached DNS Response immediately
         ▼ [MISS]
[Step 2] Security Rule Evaluation: In-Memory Decision Layer
         │ ──► [MATCHED BLOCK] Returns Sinkhole Record (0.0.0.0 / NXDOMAIN)
         ▼ [ALLOWED]
[Step 3] L2 Distributed Cache: Redis Cache Lookup (15-min TTL, ~ 1ms)
         │ ──► [HIT] Backfill L1 Cache & Return (with 5-min Client TTL)
         ▼ [MISS]
[Step 4] Upstream Forwarding: Cloudflare (1.1.1.1) / Google (8.8.8.8) with EDNS0
         │ ──► Store in L1 Caffeine (10m) & Async L2 Redis (15m, 900s)
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

# Cache Configuration
L1_CACHE_MAX_SIZE=10000
L1_CACHE_TTL_MINUTES=10

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

# 4. Test Modern HTTPS (TYPE 65) Query (Should return NOTIMPL gracefully)
dig @127.0.0.1 -p 2053 dnsfilt.mooo.com TYPE65
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
- **Fix**: The resolver listens on unprivileged port **`2053`** (or dynamic worker ports `2054–2090`). Use NGINX stream or HAProxy on the host to load-balance public port `53` across container nodes.

---

## 📄 License
Licensed under the [MIT License](../LICENSE).
