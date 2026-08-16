package com.dnsfilt.dnsanalytics.service;

import com.dnsfilt.dnsanalytics.entity.*;
import com.dnsfilt.dnsanalytics.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RetentionRollupService
 * 
 * Manages the long-term historical data lifecycle, precomputations, and retention policies.
 * 
 * Multi-Tier Retention Architecture:
 * - Active Month: Retains fine-grained 1-hour resolution data (`ResolverHourlyStats`, `ClientHourlyStats`).
 * - Daily Summary: Preserved indefinitely in `ResolverDailyStats` & `ClientDailyStats`.
 * - Monthly Precomputed Summary: Automatically compiled into `ResolverMonthlyStats` & `ClientMonthlyStats`
 *   to allow instant (< 2ms) multi-month analytics retrieval without computing heavy SQL aggregations over millions of rows.
 * - At 01:00 AM on the 1st of every month, rolls up completed hourly data into daily and monthly records,
 *   then purges expired raw hourly entries.
 */
@Service
public class RetentionRollupService {
    private static final Logger logger = LoggerFactory.getLogger(RetentionRollupService.class);

    private final ResolverHourlyRepository resolverHourlyRepo;
    private final ClientHourlyRepository clientHourlyRepo;
    private final ClientCategoryHourlyRepository categoryHourlyRepo;
    private final ClientTopDomainsHourlyRepository topDomainsHourlyRepo;

    private final ResolverDailyRepository resolverDailyRepo;
    private final ClientDailyRepository clientDailyRepo;

    private final ResolverMonthlyRepository resolverMonthlyRepo;
    private final ClientMonthlyRepository clientMonthlyRepo;

    public RetentionRollupService(ResolverHourlyRepository resolverHourlyRepo,
                                  ClientHourlyRepository clientHourlyRepo,
                                  ClientCategoryHourlyRepository categoryHourlyRepo,
                                  ClientTopDomainsHourlyRepository topDomainsHourlyRepo,
                                  ResolverDailyRepository resolverDailyRepo,
                                  ClientDailyRepository clientDailyRepo,
                                  ResolverMonthlyRepository resolverMonthlyRepo,
                                  ClientMonthlyRepository clientMonthlyRepo) {
        this.resolverHourlyRepo = resolverHourlyRepo;
        this.clientHourlyRepo = clientHourlyRepo;
        this.categoryHourlyRepo = categoryHourlyRepo;
        this.topDomainsHourlyRepo = topDomainsHourlyRepo;
        this.resolverDailyRepo = resolverDailyRepo;
        this.clientDailyRepo = clientDailyRepo;
        this.resolverMonthlyRepo = resolverMonthlyRepo;
        this.clientMonthlyRepo = clientMonthlyRepo;
    }

    /**
     * Executes on the 1st of every month at 01:00 AM (Cron: "0 0 1 1 * ?").
     * Compresses the finished month's hourly tables into daily and precomputed monthly summary tables.
     */
    @Scheduled(cron = "0 0 1 1 * ?")
    @Transactional
    public void performMonthRollover() {
        // Cutoff: First second of the current active month
        LocalDateTime firstOfCurrentMonth = YearMonth.now().atDay(1).atStartOfDay();
        String previousMonthStr = YearMonth.now().minusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"));
        logger.info("Starting automated Month Rollover Job for historical data before {} (Month: {})", firstOfCurrentMonth, previousMonthStr);

        // 1. Rollup Resolver Hourly -> Resolver Daily & Resolver Monthly
        List<ResolverHourlyStats> resolverHourlyList = resolverHourlyRepo.findByHourTimestampBefore(firstOfCurrentMonth);
        Map<LocalDate, ResolverDailyStats> resolverDailyMap = new HashMap<>();

        ResolverMonthlyStats monthlyResolver = resolverMonthlyRepo.findByYearMonth(previousMonthStr)
                .orElseGet(() -> new ResolverMonthlyStats(previousMonthStr, 0, 0, 0, 0, 0, 0, 0, 0.0));

        for (ResolverHourlyStats hourly : resolverHourlyList) {
            LocalDate date = hourly.getHourTimestamp().toLocalDate();
            ResolverDailyStats daily = resolverDailyMap.computeIfAbsent(date,
                    d -> resolverDailyRepo.findByDateTimestamp(d).orElseGet(() -> new ResolverDailyStats(d, 0, 0, 0, 0, 0, 0, 0, 0.0)));

            long newTotal = daily.getTotalQueries() + hourly.getTotalQueries();
            double newAvgLatency = newTotal > 0
                    ? ((daily.getAvgLatencyMs() * daily.getTotalQueries()) + (hourly.getAvgLatencyMs() * hourly.getTotalQueries())) / (double) newTotal
                    : 0.0;

            daily.setTotalQueries(newTotal);
            daily.setAllowedQueries(daily.getAllowedQueries() + hourly.getAllowedQueries());
            daily.setBlockedQueries(daily.getBlockedQueries() + hourly.getBlockedQueries());
            daily.setNxdomainQueries(daily.getNxdomainQueries() + hourly.getNxdomainQueries());
            daily.setServfailQueries(daily.getServfailQueries() + hourly.getServfailQueries());
            daily.setCacheHits(daily.getCacheHits() + hourly.getCacheHits());
            daily.setCacheMisses(daily.getCacheMisses() + hourly.getCacheMisses());
            daily.setAvgLatencyMs(newAvgLatency);

            // Accumulate into precomputed monthly record
            monthlyResolver.setTotalQueries(monthlyResolver.getTotalQueries() + hourly.getTotalQueries());
            monthlyResolver.setAllowedQueries(monthlyResolver.getAllowedQueries() + hourly.getAllowedQueries());
            monthlyResolver.setBlockedQueries(monthlyResolver.getBlockedQueries() + hourly.getBlockedQueries());
            monthlyResolver.setNxdomainQueries(monthlyResolver.getNxdomainQueries() + hourly.getNxdomainQueries());
            monthlyResolver.setServfailQueries(monthlyResolver.getServfailQueries() + hourly.getServfailQueries());
            monthlyResolver.setCacheHits(monthlyResolver.getCacheHits() + hourly.getCacheHits());
            monthlyResolver.setCacheMisses(monthlyResolver.getCacheMisses() + hourly.getCacheMisses());
        }

        resolverDailyRepo.saveAll(resolverDailyMap.values());
        resolverMonthlyRepo.save(monthlyResolver);
        resolverHourlyRepo.deleteAll(resolverHourlyList);
        logger.info("Rolled up {} resolver hourly rows into {} daily and 1 monthly summary records.",
                resolverHourlyList.size(), resolverDailyMap.size());

        // 2. Rollup Client Hourly -> Client Daily & Client Monthly
        List<ClientHourlyStats> clientHourlyList = clientHourlyRepo.findByHourTimestampBefore(firstOfCurrentMonth);
        Map<String, ClientDailyStats> clientDailyMap = new HashMap<>();
        Map<String, ClientMonthlyStats> clientMonthlyMap = new HashMap<>();

        for (ClientHourlyStats hourly : clientHourlyList) {
            LocalDate date = hourly.getHourTimestamp().toLocalDate();
            String dailyKey = hourly.getClientHash() + "_" + date.toString();

            ClientDailyStats daily = clientDailyMap.computeIfAbsent(dailyKey,
                    k -> clientDailyRepo.findByClientHashAndDateTimestamp(hourly.getClientHash(), date)
                            .orElseGet(() -> new ClientDailyStats(date, hourly.getClientHash(), 0, 0, 0, 0, 0, 0, 0)));

            daily.setTotalQueries(daily.getTotalQueries() + hourly.getTotalQueries());
            daily.setAllowedQueries(daily.getAllowedQueries() + hourly.getAllowedQueries());
            daily.setBlockedQueries(daily.getBlockedQueries() + hourly.getBlockedQueries());
            daily.setNxdomainQueries(daily.getNxdomainQueries() + hourly.getNxdomainQueries());
            daily.setServfailQueries(daily.getServfailQueries() + hourly.getServfailQueries());
            daily.setCacheHits(daily.getCacheHits() + hourly.getCacheHits());
            daily.setCacheMisses(daily.getCacheMisses() + hourly.getCacheMisses());

            // Accumulate into per-client monthly record
            ClientMonthlyStats monthlyClient = clientMonthlyMap.computeIfAbsent(hourly.getClientHash(),
                    ch -> clientMonthlyRepo.findByClientHashAndYearMonth(ch, previousMonthStr)
                            .orElseGet(() -> new ClientMonthlyStats(previousMonthStr, ch, 0, 0, 0, 0, 0, 0, 0)));

            monthlyClient.setTotalQueries(monthlyClient.getTotalQueries() + hourly.getTotalQueries());
            monthlyClient.setAllowedQueries(monthlyClient.getAllowedQueries() + hourly.getAllowedQueries());
            monthlyClient.setBlockedQueries(monthlyClient.getBlockedQueries() + hourly.getBlockedQueries());
            monthlyClient.setNxdomainQueries(monthlyClient.getNxdomainQueries() + hourly.getNxdomainQueries());
            monthlyClient.setServfailQueries(monthlyClient.getServfailQueries() + hourly.getServfailQueries());
            monthlyClient.setCacheHits(monthlyClient.getCacheHits() + hourly.getCacheHits());
            monthlyClient.setCacheMisses(monthlyClient.getCacheMisses() + hourly.getCacheMisses());
        }

        clientDailyRepo.saveAll(clientDailyMap.values());
        clientMonthlyRepo.saveAll(clientMonthlyMap.values());
        clientHourlyRepo.deleteAll(clientHourlyList);
        logger.info("Rolled up {} client hourly rows into {} daily and {} monthly summary records.",
                clientHourlyList.size(), clientDailyMap.size(), clientMonthlyMap.size());

        // 3. Purge fine-grained Category and Top Domain records older than 1 month
        categoryHourlyRepo.deleteByHourTimestampBefore(firstOfCurrentMonth);
        topDomainsHourlyRepo.deleteByHourTimestampBefore(firstOfCurrentMonth);

        logger.info("Month Rollover Job completed successfully.");
    }
}
