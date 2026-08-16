-- =============================================================================
-- Oracle Autonomous Database (ATP) - Materialized Views & Fast Refresh Logs
-- =============================================================================

-- 1. Create Materialized View Log on Hourly Resolver Stats for Fast Refresh
CREATE MATERIALIZED VIEW LOG ON resolver_hourly_stats
WITH ROWID, SEQUENCE (hour_timestamp, total_queries, blocked_queries, cache_hits, avg_latency_ms)
INCLUDING NEW VALUES;

-- 2. Materialized View: Hourly Global Rollup Snapshot
CREATE MATERIALIZED VIEW mv_resolver_daily_summary
BUILD IMMEDIATE
REFRESH FAST ON COMMIT
AS
SELECT 
    TRUNC(hour_timestamp, 'DD') AS stat_date,
    COUNT(*) AS hourly_windows_count,
    SUM(total_queries) AS total_queries,
    SUM(blocked_queries) AS blocked_queries,
    SUM(cache_hits) AS cache_hits,
    AVG(avg_latency_ms) AS avg_latency_ms
FROM resolver_hourly_stats
GROUP BY TRUNC(hour_timestamp, 'DD');

-- 3. Materialized View: Top Threat Categories Aggregate
CREATE MATERIALIZED VIEW mv_top_threat_categories
BUILD IMMEDIATE
REFRESH COMPLETE ON DEMAND
AS
SELECT 
    category,
    SUM(total_queries) AS total_queries,
    SUM(blocked_queries) AS blocked_queries
FROM client_category_hourly
GROUP BY category;
