# 📈 dnsfilt-analytics: Stream Processing & Data Rollup Service

[![Java 21](https://img.shields.io/badge/Java-21%20LTS-orange?style=flat-square&logo=openjdk)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot 3](https://img.shields.io/badge/Spring%20Boot-3.3.5-brightgreen?style=flat-square&logo=springboot)](https://spring.io/projects/spring-boot)
[![Kafka Stream](https://img.shields.io/badge/Apache%20Kafka-SASL_PLAINTEXT-purple?style=flat-square&logo=apachekafka)](https://kafka.apache.org/)
[![Zstd JNI](https://img.shields.io/badge/Compression-Zstandard%20Protobuf-blue?style=flat-square)](https://github.com/luben/zstd-jni)
[![Oracle ATP](https://img.shields.io/badge/Database-Oracle%20ATP%2023ai-red?style=flat-square&logo=oracle)](https://www.oracle.com/autonomous-database/)

`dnsfilt-analytics` is the high-throughput stream consumption and metric aggregation microservice of the DNSFilt platform. It ingests compressed 10-minute telemetry batches from Kafka, unpacks Protocol Buffer payloads in RAM, and performs atomic upserts into Oracle Autonomous Database 23ai.

---

## 👋 A Note from the Author

> Hi! I'm **Kaushik**, the developer behind **DNSFilt**. I built `dnsfilt-analytics` to tackle the massive write amplification problem inherent in large-scale DNS monitoring — turning billions of individual raw packets into compact, highly-queryable multi-tier time-series summaries.
>
> 🔍 **I am currently looking for new software engineering opportunities.** If your team needs someone passionate about high-throughput streaming, distributed event architectures, or cloud databases, please connect with me on [**LinkedIn**](https://www.linkedin.com/in/ikaushikpal).
>
> ⭐ *Every star ⭐, issue, or referral means a lot — thank you!*

---

## 💡 What is `dnsfilt-analytics`?

`dnsfilt-analytics` acts as the analytical brain of DNSFilt. Instead of overwhelming SQL databases with single-row inserts for every DNS query, it listens for 10-minute compressed batch streams from `dnsfilt-resolver` via the Kafka topic `dns.analytics.10min`, aggregates hourly/daily metrics, and computes monthly historical rollups.

### Core Capabilities:
- **🗜️ Instant In-Memory Zstd Decompression**: Decompresses binary payloads on-the-fly via JNI bindings (`< 1ms` latency).
- **⚡ Protocol Buffer Deserialization**: Deserializes `Dns10MinBatch` structures containing client query counts, threat categories, and latency percentiles.
- **📊 Hierarchical Multi-Tier Aggregations**:
  - `resolver_hourly_stats`: Overall cluster QPS, blocked queries, cache hit rates.
  - `client_hourly_stats`: Per-client activity and security block metrics.
  - `client_category_hourly`: Categorized threat distributions (Phishing, Malware, Trackers).
  - `client_top_domains_hourly`: Top requested and top blocked domain analytics.
- **🧹 Automated Lifecycle Compaction (`RetentionRollupService`)**:
  - Scheduled cron on the 1st of every month at 01:00 AM (`0 0 1 1 * ?`).
  - Summarizes hourly records into daily and monthly tables and auto-prunes expired rows.

---

## 🎯 Why `dnsfilt-analytics`?

| Metric | Direct Ingestion | `dnsfilt-analytics` Batch Rollup |
|---|---|---|
| **Database IOPS** | 100,000 writes/sec | **< 5 atomic batch upserts/min** |
| **Network Bandwidth** | ~ 50 MB/s uncompressed JSON | **< 1.2 MB/s Protobuf + Zstd** (97% reduction) |
| **Historical Reporting Speed** | Multi-second table full-scans | **< 2ms precomputed monthly queries** |

---

## 🏗️ Ingestion & Aggregation Architecture

```text
[ dnsfilt-resolver ] (Kafka Producer)
         │
         ▼ (Zstd Compressed Protobuf: dns.analytics.10min)
┌─────────────────────────────────────────────────────────────┐
│                    dnsfilt-analytics                        │
│                                                             │
│  [ Kafka Listener ]     ──► Consumes 10-min batch streams   │
│  [ Java 21 SASL JAAS ]  ──► Authenticates to Kafka broker   │
│  [ Zstd Decompressor ]  ──► Decompresses binary payload     │
│  [ Protobuf Parser ]    ──► Deserializes batch records      │
│  [ Batch Aggregator ]   ──► Executes atomic MERGE / Upsert  │
│  [ Retention Worker ]   ──► Compresses monthly rollups      │
└──────────────────────────────┬──────────────────────────────┘
                               │ JDBC (TCPS Wallet-less TLS)
                               ▼
             [ Oracle Autonomous Database (ATP 23ai) ]
```

---

## 🚀 How to Run

### 1. Configuration (`.env`)
Create a `.env` file in `dnsfilt-analytics/`:

```dotenv
ANALYTICS_PORT=9091

# Oracle ATP Database Connection (TCPS)
DB_URL=jdbc:oracle:thin:@(description=(retry_count=20)(retry_delay=3)(address=(protocol=tcps)(port=1522)(host=adb.ap-mumbai-1.oraclecloud.com))(connect_data=(service_name=your_service_high.adb.oraclecloud.com))(security=(ssl_server_dn_match=yes)))
DB_USERNAME=ADMIN
DB_PASSWORD=your_database_password
DB_DRIVER=oracle.jdbc.OracleDriver

# Kafka Broker & SASL Credentials
KAFKA_BOOTSTRAP_SERVERS=kafka-server:9092
KAFKA_TOPIC=dns.analytics.10min
KAFKA_SECURITY_PROTOCOL=SASL_PLAINTEXT
KAFKA_SASL_MECHANISM=PLAIN
KAFKA_SASL_USERNAME=kafkaapp
KAFKA_SASL_PASSWORD=your_kafka_password

# 14-Day Rolling Log Configuration
LOG_DIR=./logs
LOG_RETENTION_DAYS=14

# JVM Memory Profile (500MB Container Limit)
JAVA_OPTS=-Xms128m -Xmx320m -XX:MaxMetaspaceSize=128m -XX:+UseG1GC
```

### 2. Run with Docker Compose
```bash
cd dnsfilt-analytics
docker compose up -d --build
```

### 3. Run Standalone with Docker
```bash
docker run -d \
  --name dnsfilt-analytics \
  --restart unless-stopped \
  -p 9091:8081 \
  --add-host kafka-server:host-gateway \
  --add-host host.docker.internal:host-gateway \
  -v $(pwd)/logs:/app/logs \
  --env-file .env \
  --memory 500M \
  ikaushikpal/dnsfilt-analytics:latest
```

---

## 🔧 Troubleshooting Guide

### 1. `KafkaException: Failed to construct kafka consumer (No resolvable bootstrap urls)`
- **Cause**: Unresolvable broker hostname on Linux/Podman networks.
- **Fix**: The service includes pre-flight DNS validation (`resolveBootstrapServers`) to prevent context failure, and maps `kafka-server:host-gateway` in compose.

### 2. `IllegalStateException: Subject.getSubject(AccessControlContext) is deprecated / unsupported` in Java 21
- **Cause**: Java 21 JEP 411 removed `Subject.getSubject()`, which older Kafka SASL authenticators invoked.
- **Fix**: We implemented [`Java21SaslCallbackHandler`](file:///Users/kaushikpal/Desktop/codes/projects/dnsfilt/dnsfilt-analytics/src/main/java/com/dnsfilt/dnsanalytics/config/Java21SaslCallbackHandler.java), ensuring seamless SASL JAAS authentication on OpenJDK 21+.

### 3. `HikariPool-1 - Connection is not available, request timed out`
- **Cause**: Oracle ATP outbound egress blocked on host firewall or invalid SSL certificate.
- **Fix**: Verify TCP port `1522` is open outbound on your host firewall:
  ```bash
  nc -zv adb.ap-mumbai-1.oraclecloud.com 1522
  ```

---

## 📄 License
Licensed under the [MIT License](../LICENSE).
