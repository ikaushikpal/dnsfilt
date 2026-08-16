# DNSFilt Analytics & Retention Engine 📊

A high-throughput streaming stream consumer and data rollup service for the DNSFilt platform. Built on **Java 21**, **Spring Boot 3.3**, **Apache Kafka (SASL_PLAINTEXT / SASL_SSL)**, **Zstandard (Zstd) Decompression**, **Google Protocol Buffers (Protobuf)**, and **Oracle Autonomous Database (ATP 23ai/26ai)**.

---

## 🎯 Architecture & Data Flow

```text
[ dnsfilt-resolver ] (Kafka Producer)
         │
         ▼ (Zstd Compressed Protobuf Batches: dns.analytics.10min)
┌─────────────────────────────────────────────────────────────┐
│                    dnsfilt-analytics                        │
│                                                             │
│  [ Kafka Listener ]     ──► Consumes 10-min batch streams   │
│  [ Zstd Decompressor ]  ──► Decompresses payload in RAM     │
│  [ Protobuf Parser ]    ──► Deserializes batch records      │
│  [ Aggregator / Upsert ]──► Upserts hourly statistics       │
│  [ Retention Scheduler ]──► Monthly rollups & auto-pruning  │
└──────────────────────────────┬──────────────────────────────┘
                               │ JDBC (TCPS Wallet-less)
                               ▼
            [ Oracle Autonomous Database (ATP) ]
   (Hourly Stats, Daily Summaries, Precomputed Monthly Rollups)
```

---

## ⚡ Core Features

- **High-Efficiency Protobuf Ingestion**: Ingests `Dns10MinBatch` Protobuf payloads from Kafka topic `dns.analytics.10min`.
- **Zstandard Decompression**: Decompresses binary query logs on-the-fly with zero disk I/O overhead.
- **Hierarchical Time-Series Tables**:
  - `resolver_hourly_stats`: Resolver-wide queries, blocks, cache hits, avg latency.
  - `client_hourly_stats`: Unique client activity and threat block rates.
  - `client_category_hourly`: Threat breakdowns (MALWARE, PHISHING, ADS).
  - `client_top_domains_hourly`: Top requested and blocked domain names.
  - `resolver_daily_stats` & `client_daily_stats`: Compressed daily records.
  - `resolver_monthly_stats` & `client_monthly_stats`: Precomputed monthly summaries for instant multi-month reporting (< 2ms).
- **Automated Monthly Compaction (`RetentionRollupService`)**:
  - Cron trigger: `0 0 1 1 * ?` (1st of every month at 01:00 AM).
  - Summarizes completed hourly rows into daily and monthly records.
  - Deletes expired hourly rows to prevent database table bloat.
- **Oracle Materialized Views (`schema-oracle-materialized-views.sql`)**:
  - `mv_resolver_daily_summary` with `REFRESH FAST ON COMMIT` using Materialized View Logs.
  - `mv_top_threat_categories` with `REFRESH COMPLETE ON DEMAND`.

---

## ⚙️ Configuration (`.env`)

```env
# Database Configuration (Oracle ATP Database)
DB_URL="jdbc:oracle:thin:@(description=(retry_count=20)(retry_delay=3)(address=(protocol=tcps)(port=1522)(host=adb.ap-mumbai-1.oraclecloud.com))(connect_data=(service_name=your_service_high.adb.oraclecloud.com))(security=(ssl_server_dn_match=yes)))"
DB_USERNAME=ADMIN
DB_PASSWORD=your_db_password
DB_DRIVER=oracle.jdbc.OracleDriver

# Kafka Broker Configuration
KAFKA_BOOTSTRAP_SERVERS=kafka-server:9092
KAFKA_SECURITY_PROTOCOL=SASL_PLAINTEXT
KAFKA_SASL_MECHANISM=SCRAM-SHA-256
KAFKA_SASL_JAAS_CONFIG=org.apache.kafka.common.security.scram.ScramLoginModule required username="kafkaapp" password="your_kafka_password";

# JVM Memory Tuning (500MB Container Limit)
JAVA_OPTS=-Xms128m -Xmx320m -XX:MaxMetaspaceSize=128m -XX:+UseG1GC
```

---

## 🐳 Docker Deployment

```bash
cd dnsfilt-analytics

# 1. Build and run container
docker compose up --build -d

# 2. View live ingestion logs
docker logs -f dnsfilt-analytics
```
