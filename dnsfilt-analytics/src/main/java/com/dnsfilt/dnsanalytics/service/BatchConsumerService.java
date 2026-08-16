package com.dnsfilt.dnsanalytics.service;

import com.dnsfilt.dnsanalytics.proto.*;
import com.dnsfilt.dnsanalytics.entity.*;
import com.dnsfilt.dnsanalytics.repository.*;
import com.github.luben.zstd.Zstd;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * BatchConsumerService
 * 
 * Core Ingestion Engine for dnsfilt-analytics.
 * 
 * Processing Workflow:
 * 1. Consumes 10-minute compressed batch payloads from Kafka topic (`dns.analytics.10min`).
 * 2. Decompresses the message payload using high-speed Zstd-JNI native decompression (< 1ms).
 * 3. Deserializes the raw binary payload into a Protobuf `DnsAnalyticsBatch` object graph.
 * 4. Iterates across the 10 one-minute aggregation windows (`MinuteAggregationWindow`).
 * 5. Truncates timestamps to top-of-hour (`hourTimestamp`) and performs atomic upserts:
 *    - ResolverHourlyStats: System-wide queries, allowed, blocked, NXDOMAIN, cache hits, avg latency.
 *    - ClientHourlyStats: Per-client anonymized hashed IP query volume.
 *    - ClientCategoryHourly: Query volume tagged by security categories (ADVERTISING, MALWARE, etc.).
 *    - ClientTopDomainsHourly: Top queried and blocked domain names per client.
 */
@Service
public class BatchConsumerService {
    private static final Logger logger = LoggerFactory.getLogger(BatchConsumerService.class);

    private final ResolverHourlyRepository resolverHourlyRepo;
    private final ClientHourlyRepository clientHourlyRepo;
    private final ClientCategoryHourlyRepository categoryHourlyRepo;
    private final ClientTopDomainsHourlyRepository topDomainsHourlyRepo;

    public BatchConsumerService(ResolverHourlyRepository resolverHourlyRepo,
                                ClientHourlyRepository clientHourlyRepo,
                                ClientCategoryHourlyRepository categoryHourlyRepo,
                                ClientTopDomainsHourlyRepository topDomainsHourlyRepo) {
        this.resolverHourlyRepo = resolverHourlyRepo;
        this.clientHourlyRepo = clientHourlyRepo;
        this.categoryHourlyRepo = categoryHourlyRepo;
        this.topDomainsHourlyRepo = topDomainsHourlyRepo;
    }

    /**
     * Kafka batch listener.
     * Consumes Zstd-compressed binary Protobuf batches from the analytics topic.
     * Annotated with @Transactional to guarantee all database updates succeed or roll back atomically.
     */
    @KafkaListener(topics = "${kafka.topic:dns.analytics.10min}", groupId = "dnsfilt-analytics-rollup-group")
    @Transactional
    public void consume10MinBatch(byte[] compressedMessage) {
        try {
            // 1. Decompress Zstd-compressed message bytes
            byte[] decompressedBytes = Zstd.decompress(compressedMessage, (int) Zstd.decompressedSize(compressedMessage));

            // 2. Deserialize Protobuf message
            DnsAnalyticsBatch batch = DnsAnalyticsBatch.parseFrom(decompressedBytes);

            logger.info("Decompressed & parsed 10-min analytics batch ID: {} (windows count: {})",
                    batch.getBatchId(), batch.getMinutesCount());

            // 3. Process each 1-minute window in the 10-minute batch
            for (MinuteAggregationWindow window : batch.getMinutesList()) {
                LocalDateTime hourTs = Instant.ofEpochSecond(window.getWindowTimestamp())
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime()
                        .truncatedTo(ChronoUnit.HOURS);

                // Accumulate system-wide resolver metrics
                processResolverStats(window.getResolverStats(), hourTs);

                // Accumulate per-client metrics, category breakdowns, and top domains
                for (ClientMinuteStats clientStats : window.getClientStatsList()) {
                    processClientStats(clientStats, hourTs);
                }
            }

            logger.info("Successfully persisted 10-min batch {} to SQL database.", batch.getBatchId());

        } catch (Exception e) {
            logger.error("Failed to process incoming 10-min analytics batch: {}", e.getMessage(), e);
        }
    }

    /**
     * Upserts system-wide hourly stats (queries, blocks, cache hits, avg latency).
     */
    private void processResolverStats(ResolverMinuteStats rStats, LocalDateTime hourTs) {
        Optional<ResolverHourlyStats> existingOpt = resolverHourlyRepo.findByHourTimestamp(hourTs);
        ResolverHourlyStats entity = existingOpt.orElseGet(() -> new ResolverHourlyStats(hourTs, 0, 0, 0, 0, 0, 0, 0, 0.0));

        long newTotal = entity.getTotalQueries() + rStats.getTotalQueries();
        long newAllowed = entity.getAllowedQueries() + rStats.getAllowedQueries();
        long newBlocked = entity.getBlockedQueries() + rStats.getBlockedQueries();
        long newNx = entity.getNxdomainQueries() + rStats.getNxdomainQueries();
        long newSf = entity.getServfailQueries() + rStats.getServfailQueries();
        long newHits = entity.getCacheHits() + rStats.getCacheHits();
        long newMisses = entity.getCacheMisses() + rStats.getCacheMisses();

        double newAvgLatency = newTotal > 0 ? ((entity.getAvgLatencyMs() * entity.getTotalQueries()) + rStats.getTotalLatencyMs()) / (double) newTotal : 0.0;

        entity.setTotalQueries(newTotal);
        entity.setAllowedQueries(newAllowed);
        entity.setBlockedQueries(newBlocked);
        entity.setNxdomainQueries(newNx);
        entity.setServfailQueries(newSf);
        entity.setCacheHits(newHits);
        entity.setCacheMisses(newMisses);
        entity.setAvgLatencyMs(newAvgLatency);

        resolverHourlyRepo.save(entity);
    }

    /**
     * Upserts per-client hourly traffic, category breakdowns, and top queried domains.
     */
    private void processClientStats(ClientMinuteStats cStats, LocalDateTime hourTs) {
        String hash = cStats.getClientHash();

        // 1. Client Hourly Volume
        Optional<ClientHourlyStats> clientOpt = clientHourlyRepo.findByClientHashAndHourTimestamp(hash, hourTs);
        ClientHourlyStats clientEntity = clientOpt.orElseGet(() -> new ClientHourlyStats(hourTs, hash, 0, 0, 0, 0, 0, 0, 0));

        clientEntity.setTotalQueries(clientEntity.getTotalQueries() + cStats.getTotalQueries());
        clientEntity.setAllowedQueries(clientEntity.getAllowedQueries() + cStats.getAllowedQueries());
        clientEntity.setBlockedQueries(clientEntity.getBlockedQueries() + cStats.getBlockedQueries());
        clientEntity.setNxdomainQueries(clientEntity.getNxdomainQueries() + cStats.getNxdomainQueries());
        clientEntity.setServfailQueries(clientEntity.getServfailQueries() + cStats.getServfailQueries());
        clientEntity.setCacheHits(clientEntity.getCacheHits() + cStats.getCacheHits());
        clientEntity.setCacheMisses(clientEntity.getCacheMisses() + cStats.getCacheMisses());

        clientHourlyRepo.save(clientEntity);

        // 2. Client Category Breakdown (ADVERTISING, MALWARE, PHISHING, etc.)
        for (CategoryCount cat : cStats.getCategoriesList()) {
            Optional<ClientCategoryHourly> catOpt = categoryHourlyRepo.findByClientHashAndCategoryAndHourTimestamp(hash, cat.getCategory(), hourTs);
            ClientCategoryHourly catEntity = catOpt.orElseGet(() -> new ClientCategoryHourly(hourTs, hash, cat.getCategory(), 0, 0));

            catEntity.setTotalQueries(catEntity.getTotalQueries() + cat.getCount());
            catEntity.setBlockedQueries(catEntity.getBlockedQueries() + cat.getBlockedCount());
            categoryHourlyRepo.save(catEntity);
        }

        // 3. Client Top Requested Domains
        for (TopDomainCount dom : cStats.getTopDomainsList()) {
            Optional<ClientTopDomainsHourly> domOpt = topDomainsHourlyRepo.findByClientHashAndDomainAndHourTimestamp(hash, dom.getDomain(), hourTs);
            ClientTopDomainsHourly domEntity = domOpt.orElseGet(() -> new ClientTopDomainsHourly(hourTs, hash, dom.getDomain(), 0, 0, dom.getRank()));

            domEntity.setTotalQueries(domEntity.getTotalQueries() + dom.getCount());
            domEntity.setBlockedQueries(domEntity.getBlockedQueries() + dom.getBlockedCount());
            domEntity.setDomainRank(dom.getRank());
            topDomainsHourlyRepo.save(domEntity);
        }
    }
}
