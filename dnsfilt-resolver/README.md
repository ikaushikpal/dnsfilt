# DNSFilt Resolution Engine 🛡️

A high-throughput, low-latency security DNS resolution engine written in **Java 21** using **Virtual Threads (Project Loom)**, **Caffeine In-Memory L1 Cache**, **Valkey/Redis L2 Cache**, and **Kafka Zstd Protobuf Streaming**.

---

## 🎯 Core Architecture & Query Processing Flow

```text
Incoming UDP DNS Query (Port 53 / 2053)
         │
         ▼
[Step 1] L1 Fast Path: Caffeine In-Memory Cache (< 0.05ms)
         │ (Cache Miss)
         ▼
[Step 2] Security Rule Evaluation: In-Memory Hierarchy & Redis Near-Cache Snapshot
         │
         ├──► (Matched Block Rule) ──► Returns Sinkhole Record (0.0.0.0 / 0 RDATA)
         │
         ▼ (Allowed)
[Step 3] L2 Distributed Cache: Redis Lookup (~ 1-2ms)
         │ (Cache Miss)
         ▼
[Step 4] Upstream Recursive Resolver: Cloudflare (1.1.1.1) / Google (8.8.8.8) with EDNS0
         │
         ├──► Store in L1 Caffeine Cache & Async backfill L2 Redis (300s TTL)
         │
         ▼
[Step 5] Async Kafka Analytics Pipeline: Batches 10-min windows -> Zstd Protobuf to Kafka
```

---

## ⚡ Architectural Highlights

- **Java 21 Virtual Threads**: Handles 100,000+ concurrent UDP query requests with unpooled, lightweight user-space threads.
- **Bill Pugh Singleton Pattern**: Uses static inner holder classes for thread-safe, lock-free, lazy initialization of core services (`AppConfig`, `CacheService`, `RedisService`, `KafkaProducerService`, `BlockedEntryService`).
- **Factory Pattern (`DnsResponseFactory`)**: Encapsulates binary DNS wire-format assembly for resolved, sinkholed/blocked, and error responses.
- **Near-Cache In-Memory Decision Layer**: Evaluates blocklist rules and domain hierarchies in local CPU RAM with instant Redis version invalidation (`< 1s` rule propagation).
- **8MB OS UDP Socket Buffers (`SO_RCVBUF` / `SO_SNDBUF`)**: Absorbs sudden microsecond traffic spikes of up to 80,000+ packets without packet drops.
- **Strictly Tuned JVM Container Profile**:
  `JAVA_OPTS=-Xms128m -Xmx320m -XX:MaxMetaspaceSize=128m -XX:+UseG1GC`

---

## ⚙️ Configuration (`.env`)

```env
# DNS Server Port
DNS_PORT=2053

# Upstream DNS Forwarders
DNS_UPSTREAM=1.1.1.1,8.8.8.8

# Redis / Valkey L2 Cache & Rule Channel
REDIS_URL=redis://appuser:your_redis_password@host.docker.internal:6379

# Kafka Broker for Telemetry Streaming
KAFKA_BOOTSTRAP_SERVERS=kafka-server:9092
KAFKA_TOPIC=dns.analytics.10min
KAFKA_SECURITY_PROTOCOL=SASL_PLAINTEXT
KAFKA_SASL_MECHANISM=SCRAM-SHA-256
KAFKA_SASL_JAAS_CONFIG=org.apache.kafka.common.security.scram.ScramLoginModule required username="kafkaapp" password="your_kafka_password";

# JVM Memory Tuning (500MB Container Limit)
JAVA_OPTS=-Xms128m -Xmx320m -XX:MaxMetaspaceSize=128m -XX:+UseG1GC
```

---

## 🐳 Docker Deployment

```bash
cd dnsfilt-resolver

# 1. Build and run container
docker compose up --build -d

# 2. View live resolver logs
docker logs -f dnsfilt-resolver
```

---

## 🧪 Testing with `dig`

```bash
# Standard DNS resolution
dig @127.0.0.1 -p 2053 google.com

# IPv6 (AAAA) record resolution
dig @127.0.0.1 -p 2053 google.com AAAA

# Sinkholed malicious domain resolution
dig @127.0.0.1 -p 2053 ads.example.com
```
