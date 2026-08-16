package com.dnsfilt.dnsadmin.controller;

import com.dnsfilt.dnsadmin.config.CaffeineCacheConfig;
import com.dnsfilt.dnsadmin.dto.analytics.*;
import com.dnsfilt.dnsadmin.entity.ResolverHourlyStats;
import com.dnsfilt.dnsadmin.repository.*;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * AnalyticsApiController
 * 
 * Serves live and historical aggregated DNS traffic analytics, security block metrics,
 * category breakdowns, and top query domains supporting custom time ranges (24H, 7D, 30D,
 * specific months, hourly and custom date intervals).
 */
@RestController
@RequestMapping("/api/v1/analytics")
@CrossOrigin(origins = "*")
public class AnalyticsApiController {

    private final ResolverHourlyRepository resolverHourlyRepo;
    private final ClientHourlyRepository clientHourlyRepo;
    private final ClientCategoryHourlyRepository categoryHourlyRepo;
    private final ClientTopDomainsHourlyRepository topDomainsHourlyRepo;

    public AnalyticsApiController(ResolverHourlyRepository resolverHourlyRepo,
                                  ClientHourlyRepository clientHourlyRepo,
                                  ClientCategoryHourlyRepository categoryHourlyRepo,
                                  ClientTopDomainsHourlyRepository topDomainsHourlyRepo) {
        this.resolverHourlyRepo = resolverHourlyRepo;
        this.clientHourlyRepo = clientHourlyRepo;
        this.categoryHourlyRepo = categoryHourlyRepo;
        this.topDomainsHourlyRepo = topDomainsHourlyRepo;
    }

    /**
     * GET /api/v1/analytics/summary
     * 
     * Computes the global or range-filtered DNS traffic summary.
     */
    @GetMapping("/summary")
    public ResponseEntity<AnalyticsSummaryResponse> getSummary(
            @RequestParam(required = false, defaultValue = "24H") String range,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate
    ) {
        TimeWindow window = resolveTimeWindow(range, month, startDate, endDate);

        Long totalQueriesObj;
        Long blockedQueriesObj;
        Long cacheHitsObj;
        Double avgLatencyObj;

        if (window.isAllTime()) {
            totalQueriesObj = resolverHourlyRepo.sumTotalQueries();
            blockedQueriesObj = resolverHourlyRepo.sumBlockedQueries();
            cacheHitsObj = resolverHourlyRepo.sumCacheHits();
            avgLatencyObj = resolverHourlyRepo.avgLatencyMs();
        } else {
            totalQueriesObj = resolverHourlyRepo.sumTotalQueriesBetween(window.start(), window.end());
            blockedQueriesObj = resolverHourlyRepo.sumBlockedQueriesBetween(window.start(), window.end());
            cacheHitsObj = resolverHourlyRepo.sumCacheHitsBetween(window.start(), window.end());
            avgLatencyObj = resolverHourlyRepo.avgLatencyMsBetween(window.start(), window.end());
        }

        long totalQueries = totalQueriesObj != null ? totalQueriesObj : 0L;
        long blockedQueries = blockedQueriesObj != null ? blockedQueriesObj : 0L;
        long cacheHits = cacheHitsObj != null ? cacheHitsObj : 0L;
        double avgLatency = avgLatencyObj != null ? avgLatencyObj : 0.0;

        double blockRate = totalQueries > 0 ? (blockedQueries * 100.0) / totalQueries : 0.0;
        double cacheHitRate = totalQueries > 0 ? (cacheHits * 100.0) / totalQueries : 0.0;
        long activeClients = clientHourlyRepo.countDistinctClients();

        AnalyticsSummaryResponse summary = new AnalyticsSummaryResponse(
                totalQueries,
                blockedQueries,
                Math.round(blockRate * 100.0) / 100.0,
                Math.round(cacheHitRate * 100.0) / 100.0,
                Math.round(avgLatency * 100.0) / 100.0,
                activeClients
        );

        return ResponseEntity.ok(summary);
    }

    /**
     * GET /api/v1/analytics/traffic
     * 
     * Returns chronological time-series query & block trends based on requested range and granularity.
     */
    @GetMapping("/traffic")
    public ResponseEntity<List<TrafficPointResponse>> getTrafficTrend(
            @RequestParam(required = false, defaultValue = "24H") String range,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String granularity
    ) {
        TimeWindow window = resolveTimeWindow(range, month, startDate, endDate);
        List<ResolverHourlyStats> stats = resolverHourlyRepo.findByHourTimestampBetweenOrderByHourTimestampAsc(window.start(), window.end());

        List<TrafficPointResponse> traffic = new ArrayList<>();

        if ("MONTH".equalsIgnoreCase(range) || "30D".equalsIgnoreCase(range) || "DAILY".equalsIgnoreCase(granularity)) {
            // Aggregate hourly rows into daily buckets
            Map<String, long[]> dailyMap = new LinkedHashMap<>();
            DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("MMM dd");

            for (ResolverHourlyStats s : stats) {
                if (s.getHourTimestamp() != null) {
                    String dayKey = s.getHourTimestamp().format(dayFormatter);
                    dailyMap.computeIfAbsent(dayKey, k -> new long[2]);
                    dailyMap.get(dayKey)[0] += s.getTotalQueries();
                    dailyMap.get(dayKey)[1] += s.getBlockedQueries();
                }
            }

            for (Map.Entry<String, long[]> entry : dailyMap.entrySet()) {
                traffic.add(new TrafficPointResponse(entry.getKey(), entry.getValue()[0], entry.getValue()[1]));
            }
        } else {
            // Hourly series format
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM d ha");
            if ("24H".equalsIgnoreCase(range) || "1H".equalsIgnoreCase(range)) {
                formatter = DateTimeFormatter.ofPattern("ha");
            }

            for (ResolverHourlyStats stat : stats) {
                String formattedTime = stat.getHourTimestamp() != null ? stat.getHourTimestamp().format(formatter) : "N/A";
                traffic.add(new TrafficPointResponse(
                        formattedTime,
                        stat.getTotalQueries(),
                        stat.getBlockedQueries()
                ));
            }
        }

        return ResponseEntity.ok(traffic);
    }

    /**
     * GET /api/v1/analytics/categories
     */
    @GetMapping("/categories")
    @Cacheable(value = CaffeineCacheConfig.CACHE_HOURLY_ANALYTICS, key = "'categories'")
    public ResponseEntity<List<CategoryBreakdownResponse>> getCategoryBreakdown() {
        List<Object[]> aggregates = categoryHourlyRepo.getCategorySummaryAggregate();
        List<CategoryBreakdownResponse> categories = new ArrayList<>();

        for (Object[] row : aggregates) {
            String category = row[0] != null ? row[0].toString() : "GENERAL";
            long total = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            long blocked = row[2] != null ? ((Number) row[2]).longValue() : 0L;
            categories.add(new CategoryBreakdownResponse(category, total, blocked));
        }

        return ResponseEntity.ok(categories);
    }

    /**
     * GET /api/v1/analytics/top-blocked
     */
    @GetMapping("/top-blocked")
    @Cacheable(value = CaffeineCacheConfig.CACHE_HOURLY_ANALYTICS, key = "'top-blocked'")
    public ResponseEntity<List<TopBlockedDomainResponse>> getTopBlockedDomains() {
        List<Object[]> aggregates = topDomainsHourlyRepo.getTopDomainsAggregate();
        List<TopBlockedDomainResponse> list = new ArrayList<>();
        int rank = 1;

        for (Object[] row : aggregates) {
            String domain = row[0] != null ? row[0].toString() : "";
            long requests = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            long blockedRequests = row[2] != null ? ((Number) row[2]).longValue() : 0L;
            long clients = row[3] != null ? ((Number) row[3]).longValue() : 0L;

            list.add(new TopBlockedDomainResponse(
                    rank++,
                    domain,
                    "SECURITY",
                    requests,
                    blockedRequests,
                    clients
            ));
            if (rank > 50) break;
        }

        return ResponseEntity.ok(list);
    }

    /**
     * GET /api/v1/analytics/top-clients
     */
    @GetMapping("/top-clients")
    @Cacheable(value = CaffeineCacheConfig.CACHE_HOURLY_ANALYTICS, key = "'top-clients'")
    public ResponseEntity<List<TopClientResponse>> getTopClients() {
        List<Object[]> aggregates = clientHourlyRepo.getClientSummaryAggregate();
        List<TopClientResponse> list = new ArrayList<>();

        for (Object[] row : aggregates) {
            String clientHash = row[0] != null ? row[0].toString() : "unknown";
            long totalQueries = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            long blockedQueries = row[2] != null ? ((Number) row[2]).longValue() : 0L;
            double blockRate = totalQueries > 0 ? Math.round((blockedQueries * 100.0 / totalQueries) * 10.0) / 10.0 : 0.0;
            long distinctDomains = topDomainsHourlyRepo.countDistinctDomainsByClientHash(clientHash);

            String riskLevel = "LOW";
            String riskBadge = "🟢";
            if (blockRate > 20.0) {
                riskLevel = "HIGH";
                riskBadge = "🔴";
            } else if (blockRate > 5.0) {
                riskLevel = "MEDIUM";
                riskBadge = "🟡";
            }

            list.add(new TopClientResponse(
                    clientHash,
                    totalQueries,
                    blockedQueries,
                    blockRate,
                    distinctDomains,
                    riskLevel,
                    riskBadge
            ));
        }

        return ResponseEntity.ok(list);
    }

    private record TimeWindow(LocalDateTime start, LocalDateTime end, boolean isAllTime) {}

    private TimeWindow resolveTimeWindow(String range, String month, String startDate, String endDate) {
        LocalDateTime now = LocalDateTime.now();

        if (month != null && !month.trim().isEmpty()) {
            try {
                YearMonth ym = YearMonth.parse(month.trim());
                LocalDateTime start = ym.atDay(1).atStartOfDay();
                LocalDateTime end = ym.atEndOfMonth().atTime(23, 59, 59);
                return new TimeWindow(start, end, false);
            } catch (Exception ignored) {}
        }

        if (startDate != null && endDate != null && !startDate.trim().isEmpty() && !endDate.trim().isEmpty()) {
            try {
                LocalDateTime start = LocalDate.parse(startDate.trim()).atStartOfDay();
                LocalDateTime end = LocalDate.parse(endDate.trim()).atTime(23, 59, 59);
                return new TimeWindow(start, end, false);
            } catch (Exception ignored) {}
        }

        if ("1H".equalsIgnoreCase(range)) {
            return new TimeWindow(now.minusHours(1), now, false);
        } else if ("7D".equalsIgnoreCase(range)) {
            return new TimeWindow(now.minusDays(7), now, false);
        } else if ("30D".equalsIgnoreCase(range)) {
            return new TimeWindow(now.minusDays(30), now, false);
        } else if ("ALL".equalsIgnoreCase(range)) {
            return new TimeWindow(now.minusYears(10), now, true);
        }

        // Default 24H
        return new TimeWindow(now.minusHours(24), now, false);
    }
}
