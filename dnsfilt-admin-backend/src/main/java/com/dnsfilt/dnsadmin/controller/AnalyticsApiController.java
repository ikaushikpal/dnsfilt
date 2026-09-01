package com.dnsfilt.dnsadmin.controller;

import com.dnsfilt.dnsadmin.dto.analytics.*;
import com.dnsfilt.dnsadmin.entity.ClientDailyStats;
import com.dnsfilt.dnsadmin.entity.ClientMonthlyStats;
import com.dnsfilt.dnsadmin.entity.ResolverDailyStats;
import com.dnsfilt.dnsadmin.entity.ResolverHourlyStats;
import com.dnsfilt.dnsadmin.entity.ResolverMonthlyStats;
import com.dnsfilt.dnsadmin.repository.*;
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
 * category breakdowns, and top query domains supporting custom time ranges (1H, 24H, 7D, 30D,
 * specific months, hourly and custom date intervals with multi-tier retention support).
 */
@RestController
@RequestMapping("/api/v1/analytics")
@CrossOrigin(origins = "*")
public class AnalyticsApiController {

    private final ResolverHourlyRepository resolverHourlyRepo;
    private final ResolverDailyRepository resolverDailyRepo;
    private final ResolverMonthlyRepository resolverMonthlyRepo;
    private final ClientHourlyRepository clientHourlyRepo;
    private final ClientDailyRepository clientDailyRepo;
    private final ClientMonthlyRepository clientMonthlyRepo;
    private final ClientCategoryHourlyRepository categoryHourlyRepo;
    private final ClientTopDomainsHourlyRepository topDomainsHourlyRepo;

    public AnalyticsApiController(ResolverHourlyRepository resolverHourlyRepo,
                                  ResolverDailyRepository resolverDailyRepo,
                                  ResolverMonthlyRepository resolverMonthlyRepo,
                                  ClientHourlyRepository clientHourlyRepo,
                                  ClientDailyRepository clientDailyRepo,
                                  ClientMonthlyRepository clientMonthlyRepo,
                                  ClientCategoryHourlyRepository categoryHourlyRepo,
                                  ClientTopDomainsHourlyRepository topDomainsHourlyRepo) {
        this.resolverHourlyRepo = resolverHourlyRepo;
        this.resolverDailyRepo = resolverDailyRepo;
        this.resolverMonthlyRepo = resolverMonthlyRepo;
        this.clientHourlyRepo = clientHourlyRepo;
        this.clientDailyRepo = clientDailyRepo;
        this.clientMonthlyRepo = clientMonthlyRepo;
        this.categoryHourlyRepo = categoryHourlyRepo;
        this.topDomainsHourlyRepo = topDomainsHourlyRepo;
    }

    /**
     * GET /api/v1/analytics/summary
     * 
     * Computes the global, monthly, or custom range-filtered DNS traffic summary across
     * hourly, daily, and monthly retention tiers.
     */
    @GetMapping("/summary")
    public ResponseEntity<AnalyticsSummaryResponse> getSummary(
            @RequestParam(required = false, defaultValue = "24H") String range,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate
    ) {
        TimeWindow window = resolveTimeWindow(range, month, startDate, endDate);

        // 1. Direct precomputed Monthly Snapshot if a specific month was requested
        if (window.isSpecificMonth() && window.yearMonth() != null) {
            Optional<ResolverMonthlyStats> monthlyOpt = resolverMonthlyRepo.findByYearMonth(window.yearMonth());
            if (monthlyOpt.isPresent()) {
                ResolverMonthlyStats m = monthlyOpt.get();
                long total = m.getTotalQueries();
                long blocked = m.getBlockedQueries();
                double blockRate = total > 0 ? (blocked * 100.0) / total : 0.0;
                double cacheHitRate = total > 0 ? (m.getCacheHits() * 100.0) / total : 0.0;
                Long clientsObj = clientMonthlyRepo.countDistinctClientsByYearMonth(window.yearMonth());
                long activeClients = clientsObj != null && clientsObj > 0 ? clientsObj : 1L;

                return ResponseEntity.ok(new AnalyticsSummaryResponse(
                        total,
                        blocked,
                        Math.round(blockRate * 100.0) / 100.0,
                        Math.round(cacheHitRate * 100.0) / 100.0,
                        Math.round(m.getAvgLatencyMs() * 100.0) / 100.0,
                        activeClients
                ));
            }
        }

        // 2. Multi-tier aggregation across Daily (for rolled up past data) and Hourly (for active data)
        LocalDate startD = window.start().toLocalDate();
        LocalDate endD = window.end().toLocalDate();

        Long hourlyTotal = resolverHourlyRepo.sumTotalQueriesBetween(window.start(), window.end());
        Long hourlyBlocked = resolverHourlyRepo.sumBlockedQueriesBetween(window.start(), window.end());
        Long hourlyHits = resolverHourlyRepo.sumCacheHitsBetween(window.start(), window.end());
        Double hourlyLat = resolverHourlyRepo.avgLatencyMsBetween(window.start(), window.end());

        Long dailyTotal = resolverDailyRepo.sumTotalQueriesBetween(startD, endD);
        Long dailyBlocked = resolverDailyRepo.sumBlockedQueriesBetween(startD, endD);
        Long dailyHits = resolverDailyRepo.sumCacheHitsBetween(startD, endD);
        Double dailyLat = resolverDailyRepo.avgLatencyMsBetween(startD, endD);

        long hTot = hourlyTotal != null ? hourlyTotal : 0L;
        long hBlk = hourlyBlocked != null ? hourlyBlocked : 0L;
        long hHit = hourlyHits != null ? hourlyHits : 0L;
        double hLat = hourlyLat != null ? hourlyLat : 0.0;

        long dTot = dailyTotal != null ? dailyTotal : 0L;
        long dBlk = dailyBlocked != null ? dailyBlocked : 0L;
        long dHit = dailyHits != null ? dailyHits : 0L;
        double dLat = dailyLat != null ? dailyLat : 0.0;

        long totalQueries = hTot + dTot;
        long blockedQueries = hBlk + dBlk;
        long cacheHits = hHit + dHit;

        // Fallback to all-time if window was empty or ALL requested
        if (totalQueries == 0 && (window.isAllTime() || "ALL".equalsIgnoreCase(range))) {
            Long allHourly = resolverHourlyRepo.sumTotalQueries();
            Long allDaily = resolverDailyRepo.sumTotalQueries();
            totalQueries = (allHourly != null ? allHourly : 0L) + (allDaily != null ? allDaily : 0L);
            Long allHBlk = resolverHourlyRepo.sumBlockedQueries();
            Long allDBlk = resolverDailyRepo.sumBlockedQueries();
            blockedQueries = (allHBlk != null ? allHBlk : 0L) + (allDBlk != null ? allDBlk : 0L);
            Long allHHit = resolverHourlyRepo.sumCacheHits();
            Long allDHit = resolverDailyRepo.sumCacheHits();
            cacheHits = (allHHit != null ? allHHit : 0L) + (allDHit != null ? allDHit : 0L);
        }

        double avgLatency = 0.0;
        if (totalQueries > 0) {
            avgLatency = ((hLat * hTot) + (dLat * dTot)) / (double) totalQueries;
        }

        double blockRate = totalQueries > 0 ? (blockedQueries * 100.0) / totalQueries : 0.0;
        double cacheHitRate = totalQueries > 0 ? (cacheHits * 100.0) / totalQueries : 0.0;
        
        long activeClients = clientHourlyRepo.countDistinctClientsBetween(window.start(), window.end());
        if (activeClients == 0) {
            Long dailyClients = clientDailyRepo.countDistinctClientsBetween(startD, endD);
            activeClients = dailyClients != null && dailyClients > 0 ? dailyClients : clientHourlyRepo.countDistinctClients();
        }

        AnalyticsSummaryResponse summary = new AnalyticsSummaryResponse(
                totalQueries,
                blockedQueries,
                Math.round(blockRate * 100.0) / 100.0,
                Math.round(cacheHitRate * 100.0) / 100.0,
                Math.round(avgLatency * 100.0) / 100.0,
                Math.max(activeClients, 1L)
        );

        return ResponseEntity.ok(summary);
    }

    /**
     * GET /api/v1/analytics/traffic
     * 
     * Returns chronological time-series query & block trends based on requested range and granularity.
     * Merges hourly live stats with daily historical rollups automatically.
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
        List<TrafficPointResponse> traffic = new ArrayList<>();

        boolean isDailyGranularity = "MONTH".equalsIgnoreCase(range)
                || "30D".equalsIgnoreCase(range)
                || "7D".equalsIgnoreCase(range)
                || "DAILY".equalsIgnoreCase(granularity);

        if (isDailyGranularity) {
            // Daily bucket mapping (key: LocalDate)
            Map<LocalDate, long[]> dailyMap = new TreeMap<>();
            LocalDate startD = window.start().toLocalDate();
            LocalDate endD = window.end().toLocalDate();

            // 1. Load from daily historical rollups
            List<ResolverDailyStats> dailyStats = resolverDailyRepo.findByDateTimestampBetweenOrderByDateTimestampAsc(startD, endD);
            for (ResolverDailyStats d : dailyStats) {
                if (d.getDateTimestamp() != null) {
                    dailyMap.put(d.getDateTimestamp(), new long[]{d.getTotalQueries(), d.getBlockedQueries()});
                }
            }

            // 2. Load and aggregate any active hourly rows
            List<ResolverHourlyStats> hourlyStats = resolverHourlyRepo.findByHourTimestampBetweenOrderByHourTimestampAsc(window.start(), window.end());
            for (ResolverHourlyStats h : hourlyStats) {
                if (h.getHourTimestamp() != null) {
                    LocalDate dayKey = h.getHourTimestamp().toLocalDate();
                    dailyMap.computeIfAbsent(dayKey, k -> new long[2]);
                    dailyMap.get(dayKey)[0] += h.getTotalQueries();
                    dailyMap.get(dayKey)[1] += h.getBlockedQueries();
                }
            }

            DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("MMM dd");
            for (Map.Entry<LocalDate, long[]> entry : dailyMap.entrySet()) {
                traffic.add(new TrafficPointResponse(
                        entry.getKey().format(dayFormatter),
                        entry.getValue()[0],
                        entry.getValue()[1]
                ));
            }
        } else {
            // Hourly series format
            List<ResolverHourlyStats> stats = resolverHourlyRepo.findByHourTimestampBetweenOrderByHourTimestampAsc(window.start(), window.end());

            if (!stats.isEmpty()) {
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
            } else {
                // Graceful fallback for past dates where hourly was rolled up to daily:
                // Return daily points so the user doesn't see a blank chart
                LocalDate startD = window.start().toLocalDate();
                LocalDate endD = window.end().toLocalDate();
                List<ResolverDailyStats> dailyStats = resolverDailyRepo.findByDateTimestampBetweenOrderByDateTimestampAsc(startD, endD);
                DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("MMM dd");

                for (ResolverDailyStats d : dailyStats) {
                    if (d.getDateTimestamp() != null) {
                        traffic.add(new TrafficPointResponse(
                                d.getDateTimestamp().format(dayFormatter),
                                d.getTotalQueries(),
                                d.getBlockedQueries()
                        ));
                    }
                }
            }
        }

        return ResponseEntity.ok(traffic);
    }

    /**
     * GET /api/v1/analytics/categories
     */
    @GetMapping("/categories")
    public ResponseEntity<List<CategoryBreakdownResponse>> getCategoryBreakdown(
            @RequestParam(required = false) String range,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate
    ) {
        TimeWindow window = resolveTimeWindow(range, month, startDate, endDate);
        List<Object[]> aggregates = categoryHourlyRepo.getCategorySummaryAggregateBetween(window.start(), window.end());
        if (aggregates.isEmpty()) {
            aggregates = categoryHourlyRepo.getCategorySummaryAggregate();
        }

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
    public ResponseEntity<List<TopBlockedDomainResponse>> getTopBlockedDomains(
            @RequestParam(required = false) String range,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate
    ) {
        TimeWindow window = resolveTimeWindow(range, month, startDate, endDate);
        List<Object[]> aggregates = topDomainsHourlyRepo.getTopDomainsAggregateBetween(window.start(), window.end());
        if (aggregates.isEmpty()) {
            aggregates = topDomainsHourlyRepo.getTopDomainsAggregate();
        }

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
    public ResponseEntity<List<TopClientResponse>> getTopClients(
            @RequestParam(required = false) String range,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate
    ) {
        TimeWindow window = resolveTimeWindow(range, month, startDate, endDate);
        List<Object[]> aggregates = clientHourlyRepo.getClientSummaryAggregateBetween(window.start(), window.end());
        if (aggregates.isEmpty()) {
            aggregates = clientHourlyRepo.getClientSummaryAggregate();
        }

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

    private record TimeWindow(LocalDateTime start, LocalDateTime end, boolean isAllTime, boolean isSpecificMonth, String yearMonth) {}

    private TimeWindow resolveTimeWindow(String range, String month, String startDate, String endDate) {
        LocalDateTime now = LocalDateTime.now();

        if (month != null && !month.trim().isEmpty()) {
            try {
                YearMonth ym = YearMonth.parse(month.trim());
                LocalDateTime start = ym.atDay(1).atStartOfDay();
                LocalDateTime end = ym.atEndOfMonth().atTime(23, 59, 59);
                return new TimeWindow(start, end, false, true, month.trim());
            } catch (Exception ignored) {}
        }

        if (startDate != null && endDate != null && !startDate.trim().isEmpty() && !endDate.trim().isEmpty()) {
            try {
                LocalDateTime start = LocalDate.parse(startDate.trim()).atStartOfDay();
                LocalDateTime end = LocalDate.parse(endDate.trim()).atTime(23, 59, 59);
                return new TimeWindow(start, end, false, false, null);
            } catch (Exception ignored) {}
        }

        if ("1H".equalsIgnoreCase(range)) {
            return new TimeWindow(now.minusHours(1), now, false, false, null);
        } else if ("7D".equalsIgnoreCase(range)) {
            return new TimeWindow(now.minusDays(7), now, false, false, null);
        } else if ("30D".equalsIgnoreCase(range)) {
            return new TimeWindow(now.minusDays(30), now, false, false, null);
        } else if ("ALL".equalsIgnoreCase(range)) {
            return new TimeWindow(now.minusYears(10), now, true, false, null);
        }

        // Default 24H
        return new TimeWindow(now.minusHours(24), now, false, false, null);
    }
}
