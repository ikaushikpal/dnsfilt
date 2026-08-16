package com.dnsfilt.dnsresolver.service;

import com.dnsfilt.dnsanalytics.proto.*;
import com.dnsfilt.dnsresolver.utility.ClientHmacUtil;
import com.github.luben.zstd.Zstd;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class DnsMetricsAggregator {
    private static final Logger logger = LoggerFactory.getLogger(DnsMetricsAggregator.class);

    private static final String KAFKA_TOPIC = "dns.analytics.10min";
    private static final String RESOLVER_ID = "resolver-node-1";

    private final KafkaProducer<String, byte[]> kafkaProducer;

    // Current 1-minute window statistics
    private final AtomicLong windowStartTime = new AtomicLong(Instant.now().getEpochSecond());
    private final ConcurrentHashMap<String, ClientAccumulator> clientStatsMap = new ConcurrentHashMap<>();
    private final ResolverAccumulator resolverStats = new ResolverAccumulator();

    // Queue holding completed 1-minute windows awaiting 10-minute batching
    private final List<MinuteAggregationWindow> batchWindows = Collections.synchronizedList(new ArrayList<>());

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public DnsMetricsAggregator(KafkaProducer<String, byte[]> kafkaProducer) {
        this.kafkaProducer = kafkaProducer;

        // Schedule 1-minute window freezing
        this.scheduler.scheduleAtFixedRate(this::freezeMinuteWindow, 60, 60, TimeUnit.SECONDS);
    }

    public void recordQuery(String rawClientIp, String domain, String qType, String action, String category, boolean cacheHit, long latencyMs, int rCode) {
        String clientHash = ClientHmacUtil.hashClientIp(rawClientIp);

        // Update Global Resolver accumulator
        resolverStats.record(action, cacheHit, latencyMs, rCode);

        // Update Client accumulator
        clientStatsMap.computeIfAbsent(clientHash, k -> new ClientAccumulator(clientHash))
                .record(domain, action, category, cacheHit, latencyMs, rCode);
    }

    private synchronized void freezeMinuteWindow() {
        long now = Instant.now().getEpochSecond();
        long windowStart = windowStartTime.getAndSet(now);

        // Build ResolverMinuteStats Protobuf
        ResolverMinuteStats rStats = ResolverMinuteStats.newBuilder()
                .setWindowTimestamp(windowStart)
                .setTotalQueries(resolverStats.totalQueries.get())
                .setAllowedQueries(resolverStats.allowedQueries.get())
                .setBlockedQueries(resolverStats.blockedQueries.get())
                .setNxdomainQueries(resolverStats.nxdomainQueries.get())
                .setServfailQueries(resolverStats.servfailQueries.get())
                .setCacheHits(resolverStats.cacheHits.get())
                .setCacheMisses(resolverStats.cacheMisses.get())
                .setTotalLatencyMs(resolverStats.totalLatencyMs.get())
                .build();

        // Build list of ClientMinuteStats Protobuf
        List<ClientMinuteStats> clientProtos = new ArrayList<>();
        for (ClientAccumulator ca : clientStatsMap.values()) {
            clientProtos.add(ca.toProtobuf());
        }

        // Create 1-minute aggregation window
        MinuteAggregationWindow minuteWindow = MinuteAggregationWindow.newBuilder()
                .setWindowTimestamp(windowStart)
                .setResolverStats(rStats)
                .addAllClientStats(clientProtos)
                .build();

        // Clear current window state for next minute
        resolverStats.reset();
        clientStatsMap.clear();

        // Add to 10-minute batch queue
        batchWindows.add(minuteWindow);
        logger.info("Freezed 1-minute window at {}. Queue size: {}", windowStart, batchWindows.size());

        // When 10 windows (or 10 minutes) accumulate, flush batch to Kafka
        if (batchWindows.size() >= 10) {
            flush10MinuteBatch();
        }
    }

    private synchronized void flush10MinuteBatch() {
        if (batchWindows.isEmpty()) return;

        List<MinuteAggregationWindow> windowsToSend = new ArrayList<>(batchWindows);
        batchWindows.clear();

        long startTs = windowsToSend.get(0).getWindowTimestamp();
        long endTs = windowsToSend.get(windowsToSend.size() - 1).getWindowTimestamp() + 60;
        String batchId = "batch-" + UUID.randomUUID().toString();

        DnsAnalyticsBatch batch = DnsAnalyticsBatch.newBuilder()
                .setBatchId(batchId)
                .setResolverId(RESOLVER_ID)
                .setWindowStartTimestamp(startTs)
                .setWindowEndTimestamp(endTs)
                .addAllMinutes(windowsToSend)
                .build();

        // Serialize to Protobuf binary bytes
        byte[] rawBytes = batch.toByteArray();

        // Compress using Zstd
        byte[] compressedBytes = Zstd.compress(rawBytes);

        logger.info("Compressed 10-min batch {} (Raw: {} bytes -> Zstd: {} bytes, ratio: {}%)",
                batchId, rawBytes.length, compressedBytes.length,
                String.format("%.2f", (compressedBytes.length * 100.0) / rawBytes.length));

        // Publish compressed batch to Kafka
        if (kafkaProducer != null) {
            kafkaProducer.send(new ProducerRecord<>(KAFKA_TOPIC, batchId, compressedBytes), (meta, ex) -> {
                if (ex == null) {
                    logger.info("Emitted 10-min analytics batch {} to Kafka topic {} [partition: {}, offset: {}]",
                            batchId, meta.topic(), meta.partition(), meta.offset());
                } else {
                    logger.error("Failed to emit analytics batch {} to Kafka: {}", batchId, ex.getMessage());
                }
            });
        }
    }

    // Helper Accumulators
    private static class ResolverAccumulator {
        final AtomicLong totalQueries = new AtomicLong(0);
        final AtomicLong allowedQueries = new AtomicLong(0);
        final AtomicLong blockedQueries = new AtomicLong(0);
        final AtomicLong nxdomainQueries = new AtomicLong(0);
        final AtomicLong servfailQueries = new AtomicLong(0);
        final AtomicLong cacheHits = new AtomicLong(0);
        final AtomicLong cacheMisses = new AtomicLong(0);
        final AtomicLong totalLatencyMs = new AtomicLong(0);

        void record(String action, boolean cacheHit, long latency, int rCode) {
            totalQueries.incrementAndGet();
            if ("BLOCKED".equalsIgnoreCase(action)) blockedQueries.incrementAndGet();
            else allowedQueries.incrementAndGet();

            if (rCode == 3) nxdomainQueries.incrementAndGet(); // NXDOMAIN
            if (rCode == 2) servfailQueries.incrementAndGet(); // SERVFAIL

            if (cacheHit) cacheHits.incrementAndGet();
            else cacheMisses.incrementAndGet();

            totalLatencyMs.addAndGet(latency);
        }

        void reset() {
            totalQueries.set(0); allowedQueries.set(0); blockedQueries.set(0);
            nxdomainQueries.set(0); servfailQueries.set(0); cacheHits.set(0);
            cacheMisses.set(0); totalLatencyMs.set(0);
        }
    }

    private static class ClientAccumulator {
        final String clientHash;
        final AtomicLong totalQueries = new AtomicLong(0);
        final AtomicLong allowedQueries = new AtomicLong(0);
        final AtomicLong blockedQueries = new AtomicLong(0);
        final AtomicLong nxdomainQueries = new AtomicLong(0);
        final AtomicLong servfailQueries = new AtomicLong(0);
        final AtomicLong cacheHits = new AtomicLong(0);
        final AtomicLong cacheMisses = new AtomicLong(0);
        final AtomicLong totalLatencyMs = new AtomicLong(0);

        final ConcurrentHashMap<String, AtomicLong> categoryMap = new ConcurrentHashMap<>();
        final ConcurrentHashMap<String, AtomicLong> categoryBlockedMap = new ConcurrentHashMap<>();
        final ConcurrentHashMap<String, AtomicLong> domainMap = new ConcurrentHashMap<>();
        final ConcurrentHashMap<String, AtomicLong> domainBlockedMap = new ConcurrentHashMap<>();

        ClientAccumulator(String clientHash) {
            this.clientHash = clientHash;
        }

        void record(String domain, String action, String category, boolean cacheHit, long latency, int rCode) {
            totalQueries.incrementAndGet();
            boolean isBlocked = "BLOCKED".equalsIgnoreCase(action);
            if (isBlocked) blockedQueries.incrementAndGet();
            else allowedQueries.incrementAndGet();

            if (rCode == 3) nxdomainQueries.incrementAndGet();
            if (rCode == 2) servfailQueries.incrementAndGet();

            if (cacheHit) cacheHits.incrementAndGet();
            else cacheMisses.incrementAndGet();

            totalLatencyMs.addAndGet(latency);

            // Track category
            if (category != null && !category.isEmpty()) {
                categoryMap.computeIfAbsent(category, k -> new AtomicLong(0)).incrementAndGet();
                if (isBlocked) {
                    categoryBlockedMap.computeIfAbsent(category, k -> new AtomicLong(0)).incrementAndGet();
                }
            }

            // Track domain
            if (domain != null && !domain.isEmpty()) {
                domainMap.computeIfAbsent(domain, k -> new AtomicLong(0)).incrementAndGet();
                if (isBlocked) {
                    domainBlockedMap.computeIfAbsent(domain, k -> new AtomicLong(0)).incrementAndGet();
                }
            }
        }

        ClientMinuteStats toProtobuf() {
            List<CategoryCount> catList = new ArrayList<>();
            for (Map.Entry<String, AtomicLong> entry : categoryMap.entrySet()) {
                String cat = entry.getKey();
                long total = entry.getValue().get();
                long blocked = categoryBlockedMap.getOrDefault(cat, new AtomicLong(0)).get();
                catList.add(CategoryCount.newBuilder()
                        .setCategory(cat)
                        .setCount(total)
                        .setBlockedCount(blocked)
                        .build());
            }

            // Bounded Top 10 Domains
            List<Map.Entry<String, AtomicLong>> sortedDomains = new ArrayList<>(domainMap.entrySet());
            sortedDomains.sort((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()));

            List<TopDomainCount> topDomainsList = new ArrayList<>();
            int rank = 1;
            for (Map.Entry<String, AtomicLong> entry : sortedDomains) {
                if (rank > 10) break;
                String dom = entry.getKey();
                long total = entry.getValue().get();
                long blocked = domainBlockedMap.getOrDefault(dom, new AtomicLong(0)).get();
                topDomainsList.add(TopDomainCount.newBuilder()
                        .setDomain(dom)
                        .setCount(total)
                        .setBlockedCount(blocked)
                        .setRank(rank++)
                        .build());
            }

            return ClientMinuteStats.newBuilder()
                    .setClientHash(clientHash)
                    .setTotalQueries(totalQueries.get())
                    .setAllowedQueries(allowedQueries.get())
                    .setBlockedQueries(blockedQueries.get())
                    .setNxdomainQueries(nxdomainQueries.get())
                    .setServfailQueries(servfailQueries.get())
                    .setCacheHits(cacheHits.get())
                    .setCacheMisses(cacheMisses.get())
                    .setTotalLatencyMs(totalLatencyMs.get())
                    .addAllCategories(catList)
                    .addAllTopDomains(topDomainsList)
                    .build();
        }
    }
}
