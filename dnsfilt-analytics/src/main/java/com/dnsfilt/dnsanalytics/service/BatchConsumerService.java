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
import java.util.*;

/**
 * BatchConsumerService
 * 
 * High-performance Ingestion Engine for dnsfilt-analytics.
 * 
 * Resolves Oracle ORA-12860 sibling row lock deadlocks by:
 * 1. Pre-aggregating all 10 one-minute windows in memory across the entire batch.
 * 2. Processing and updating database rows in deterministic sorted order.
 * 3. Performing single-pass batch upserts per table with phased flushes,
 *    eliminating interleaved SELECT / UPDATE cycles and repeated row-level lock contention.
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

    // In-memory accumulators
    private static class ResolverAgg {
        long totalQueries;
        long allowedQueries;
        long blockedQueries;
        long nxdomainQueries;
        long servfailQueries;
        long cacheHits;
        long cacheMisses;
        double totalLatencyMs;
    }

    private static class ClientAgg {
        long totalQueries;
        long allowedQueries;
        long blockedQueries;
        long nxdomainQueries;
        long servfailQueries;
        long cacheHits;
        long cacheMisses;
    }

    private static class CategoryAgg {
        long totalQueries;
        long blockedQueries;
    }

    private static class DomainAgg {
        long totalQueries;
        long blockedQueries;
        int minRank = Integer.MAX_VALUE;
    }

    private record ClientHourKey(String clientHash, LocalDateTime hourTimestamp) implements Comparable<ClientHourKey> {
        @Override
        public int compareTo(ClientHourKey o) {
            int cmp = this.clientHash.compareTo(o.clientHash);
            if (cmp != 0) return cmp;
            return this.hourTimestamp.compareTo(o.hourTimestamp);
        }
    }

    private record ClientCategoryKey(String clientHash, String category, LocalDateTime hourTimestamp) implements Comparable<ClientCategoryKey> {
        @Override
        public int compareTo(ClientCategoryKey o) {
            int cmp = this.clientHash.compareTo(o.clientHash);
            if (cmp != 0) return cmp;
            cmp = this.category.compareTo(o.category);
            if (cmp != 0) return cmp;
            return this.hourTimestamp.compareTo(o.hourTimestamp);
        }
    }

    private record ClientDomainKey(String clientHash, String domain, LocalDateTime hourTimestamp) implements Comparable<ClientDomainKey> {
        @Override
        public int compareTo(ClientDomainKey o) {
            int cmp = this.clientHash.compareTo(o.clientHash);
            if (cmp != 0) return cmp;
            cmp = this.domain.compareTo(o.domain);
            if (cmp != 0) return cmp;
            return this.hourTimestamp.compareTo(o.hourTimestamp);
        }
    }

    /**
     * Kafka batch listener.
     * Consumes Zstd-compressed binary Protobuf batches from the analytics topic.
     */
    @KafkaListener(
        topics = "${kafka.topic:${KAFKA_TOPIC:dns.analytics.10min}}", 
        groupId = "dnsfilt-analytics-rollup-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void consume10MinBatch(byte[] compressedMessage) {
        try {
            // 1. Decompress Zstd-compressed message bytes
            byte[] decompressedBytes = Zstd.decompress(compressedMessage, (int) Zstd.decompressedSize(compressedMessage));

            // 2. Deserialize Protobuf message
            DnsAnalyticsBatch batch = DnsAnalyticsBatch.parseFrom(decompressedBytes);

            logger.info("Decompressed & parsed 10-min analytics batch ID: {} (windows count: {})",
                    batch.getBatchId(), batch.getMinutesCount());

            // 3. Pre-aggregate entire 10-minute batch in memory into sorted TreeMaps
            Map<LocalDateTime, ResolverAgg> resolverMap = new TreeMap<>();
            Map<ClientHourKey, ClientAgg> clientMap = new TreeMap<>();
            Map<ClientCategoryKey, CategoryAgg> categoryMap = new TreeMap<>();
            Map<ClientDomainKey, DomainAgg> domainMap = new TreeMap<>();

            for (MinuteAggregationWindow window : batch.getMinutesList()) {
                LocalDateTime hourTs = Instant.ofEpochSecond(window.getWindowTimestamp())
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime()
                        .truncatedTo(ChronoUnit.HOURS);

                // Accumulate Resolver metrics
                if (window.hasResolverStats()) {
                    ResolverMinuteStats rStats = window.getResolverStats();
                    ResolverAgg rAgg = resolverMap.computeIfAbsent(hourTs, k -> new ResolverAgg());
                    rAgg.totalQueries += rStats.getTotalQueries();
                    rAgg.allowedQueries += rStats.getAllowedQueries();
                    rAgg.blockedQueries += rStats.getBlockedQueries();
                    rAgg.nxdomainQueries += rStats.getNxdomainQueries();
                    rAgg.servfailQueries += rStats.getServfailQueries();
                    rAgg.cacheHits += rStats.getCacheHits();
                    rAgg.cacheMisses += rStats.getCacheMisses();
                    rAgg.totalLatencyMs += rStats.getTotalLatencyMs();
                }

                // Accumulate Per-Client metrics
                for (ClientMinuteStats cStats : window.getClientStatsList()) {
                    String hash = cStats.getClientHash();
                    if (hash == null || hash.isEmpty()) continue;

                    ClientHourKey cKey = new ClientHourKey(hash, hourTs);
                    ClientAgg cAgg = clientMap.computeIfAbsent(cKey, k -> new ClientAgg());
                    cAgg.totalQueries += cStats.getTotalQueries();
                    cAgg.allowedQueries += cStats.getAllowedQueries();
                    cAgg.blockedQueries += cStats.getBlockedQueries();
                    cAgg.nxdomainQueries += cStats.getNxdomainQueries();
                    cAgg.servfailQueries += cStats.getServfailQueries();
                    cAgg.cacheHits += cStats.getCacheHits();
                    cAgg.cacheMisses += cStats.getCacheMisses();

                    // Categories
                    for (CategoryCount cat : cStats.getCategoriesList()) {
                        String category = cat.getCategory() != null && !cat.getCategory().isEmpty() ? cat.getCategory() : "GENERAL";
                        ClientCategoryKey catKey = new ClientCategoryKey(hash, category, hourTs);
                        CategoryAgg catAgg = categoryMap.computeIfAbsent(catKey, k -> new CategoryAgg());
                        catAgg.totalQueries += cat.getCount();
                        catAgg.blockedQueries += cat.getBlockedCount();
                    }

                    // Top Domains
                    for (TopDomainCount dom : cStats.getTopDomainsList()) {
                        String domain = dom.getDomain();
                        if (domain == null || domain.isEmpty()) continue;

                        ClientDomainKey domKey = new ClientDomainKey(hash, domain, hourTs);
                        DomainAgg domAgg = domainMap.computeIfAbsent(domKey, k -> new DomainAgg());
                        domAgg.totalQueries += dom.getCount();
                        domAgg.blockedQueries += dom.getBlockedCount();
                        if (dom.getRank() > 0 && dom.getRank() < domAgg.minRank) {
                            domAgg.minRank = dom.getRank();
                        }
                    }
                }
            }

            // 4. Phase 1: Persist Resolver Hourly Stats
            for (Map.Entry<LocalDateTime, ResolverAgg> entry : resolverMap.entrySet()) {
                LocalDateTime hourTs = entry.getKey();
                ResolverAgg agg = entry.getValue();

                ResolverHourlyStats entity = resolverHourlyRepo.findByHourTimestamp(hourTs)
                        .orElseGet(() -> new ResolverHourlyStats(hourTs, 0, 0, 0, 0, 0, 0, 0, 0.0));

                long newTotal = entity.getTotalQueries() + agg.totalQueries;
                long newAllowed = entity.getAllowedQueries() + agg.allowedQueries;
                long newBlocked = entity.getBlockedQueries() + agg.blockedQueries;
                long newNx = entity.getNxdomainQueries() + agg.nxdomainQueries;
                long newSf = entity.getServfailQueries() + agg.servfailQueries;
                long newHits = entity.getCacheHits() + agg.cacheHits;
                long newMisses = entity.getCacheMisses() + agg.cacheMisses;

                double newAvgLatency = newTotal > 0
                        ? ((entity.getAvgLatencyMs() * entity.getTotalQueries()) + agg.totalLatencyMs) / (double) newTotal
                        : 0.0;

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
            resolverHourlyRepo.flush();

            // 5. Phase 2: Persist Client Hourly Stats in deterministic sorted order
            List<ClientHourlyStats> clientEntities = new ArrayList<>();
            for (Map.Entry<ClientHourKey, ClientAgg> entry : clientMap.entrySet()) {
                ClientHourKey key = entry.getKey();
                ClientAgg agg = entry.getValue();

                ClientHourlyStats entity = clientHourlyRepo.findByClientHashAndHourTimestamp(key.clientHash(), key.hourTimestamp())
                        .orElseGet(() -> new ClientHourlyStats(key.hourTimestamp(), key.clientHash(), 0, 0, 0, 0, 0, 0, 0));

                entity.setTotalQueries(entity.getTotalQueries() + agg.totalQueries);
                entity.setAllowedQueries(entity.getAllowedQueries() + agg.allowedQueries);
                entity.setBlockedQueries(entity.getBlockedQueries() + agg.blockedQueries);
                entity.setNxdomainQueries(entity.getNxdomainQueries() + agg.nxdomainQueries);
                entity.setServfailQueries(entity.getServfailQueries() + agg.servfailQueries);
                entity.setCacheHits(entity.getCacheHits() + agg.cacheHits);
                entity.setCacheMisses(entity.getCacheMisses() + agg.cacheMisses);

                clientEntities.add(entity);
            }
            if (!clientEntities.isEmpty()) {
                clientHourlyRepo.saveAll(clientEntities);
                clientHourlyRepo.flush();
            }

            // 6. Phase 3: Persist Category Breakdowns in deterministic sorted order
            List<ClientCategoryHourly> catEntities = new ArrayList<>();
            for (Map.Entry<ClientCategoryKey, CategoryAgg> entry : categoryMap.entrySet()) {
                ClientCategoryKey key = entry.getKey();
                CategoryAgg agg = entry.getValue();

                ClientCategoryHourly entity = categoryHourlyRepo.findByClientHashAndCategoryAndHourTimestamp(
                        key.clientHash(), key.category(), key.hourTimestamp())
                        .orElseGet(() -> new ClientCategoryHourly(key.hourTimestamp(), key.clientHash(), key.category(), 0, 0));

                entity.setTotalQueries(entity.getTotalQueries() + agg.totalQueries);
                entity.setBlockedQueries(entity.getBlockedQueries() + agg.blockedQueries);

                catEntities.add(entity);
            }
            if (!catEntities.isEmpty()) {
                categoryHourlyRepo.saveAll(catEntities);
                categoryHourlyRepo.flush();
            }

            // 7. Phase 4: Persist Top Domains in deterministic sorted order
            List<ClientTopDomainsHourly> domEntities = new ArrayList<>();
            for (Map.Entry<ClientDomainKey, DomainAgg> entry : domainMap.entrySet()) {
                ClientDomainKey key = entry.getKey();
                DomainAgg agg = entry.getValue();

                int rank = agg.minRank == Integer.MAX_VALUE ? 1 : agg.minRank;
                ClientTopDomainsHourly entity = topDomainsHourlyRepo.findByClientHashAndDomainAndHourTimestamp(
                        key.clientHash(), key.domain(), key.hourTimestamp())
                        .orElseGet(() -> new ClientTopDomainsHourly(key.hourTimestamp(), key.clientHash(), key.domain(), 0, 0, rank));

                entity.setTotalQueries(entity.getTotalQueries() + agg.totalQueries);
                entity.setBlockedQueries(entity.getBlockedQueries() + agg.blockedQueries);
                if (agg.minRank != Integer.MAX_VALUE) {
                    entity.setDomainRank(agg.minRank);
                }

                domEntities.add(entity);
            }
            if (!domEntities.isEmpty()) {
                topDomainsHourlyRepo.saveAll(domEntities);
                topDomainsHourlyRepo.flush();
            }

            logger.info("Successfully persisted 10-min batch {} (clients: {}, categories: {}, domains: {})",
                    batch.getBatchId(), clientEntities.size(), catEntities.size(), domEntities.size());

        } catch (Exception e) {
            logger.error("Failed to process incoming 10-min analytics batch: {}", e.getMessage(), e);
            throw new RuntimeException("Error processing 10-min batch", e);
        }
    }
}
