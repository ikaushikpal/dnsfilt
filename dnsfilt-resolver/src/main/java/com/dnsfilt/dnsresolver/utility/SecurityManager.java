package com.dnsfilt.dnsresolver.utility;

import com.dnsfilt.dnsresolver.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class SecurityManager {
    private static final Logger logger = LoggerFactory.getLogger(SecurityManager.class);
    private static volatile SecurityManager instance;

    // Rate Limiting per Client IP (Queries per second limit)
    private final int maxQpsPerIp;
    private final Map<String, IpRateLimitWindow> rateLimitMap = new ConcurrentHashMap<>();

    // Packet Size Limits
    public static final int MIN_PACKET_SIZE = 12; // Minimum DNS Header length
    public static final int MAX_PACKET_SIZE = 4096; // Maximum EDNS0 packet length

    private SecurityManager() {
        AppConfig config = AppConfig.getInstance();
        String qpsStr = config.getEnvVariable("dns.security.rate_limit_qps");
        this.maxQpsPerIp = (qpsStr != null) ? Integer.parseInt(qpsStr) : 50;
        logger.info("SecurityManager initialized (Max QPS per IP = {})", maxQpsPerIp);
    }

    public static SecurityManager getInstance() {
        if (instance == null) {
            synchronized (SecurityManager.class) {
                if (instance == null) {
                    instance = new SecurityManager();
                }
            }
        }
        return instance;
    }

    /**
     * Validates packet size (12 to 4096 bytes)
     */
    public boolean isValidPacketSize(int length) {
        if (length < MIN_PACKET_SIZE || length > MAX_PACKET_SIZE) {
            logger.warn("Security Alert: Invalid DNS packet length {} bytes (Allowed: 12-4096B)", length);
            return false;
        }
        return true;
    }

    /**
     * Response Rate Limiting (RRL) - Returns true if query is allowed, false if rate limited
     */
    public boolean allowClientIp(SocketAddress clientAddress) {
        if (!(clientAddress instanceof InetSocketAddress inetAddress)) {
            return true;
        }

        String clientIp = inetAddress.getAddress().getHostAddress();
        long currentSecond = System.currentTimeMillis() / 1000;

        IpRateLimitWindow window = rateLimitMap.compute(clientIp, (ip, oldWindow) -> {
            if (oldWindow == null || oldWindow.timestampSecond != currentSecond) {
                return new IpRateLimitWindow(currentSecond, 1);
            } else {
                oldWindow.count.incrementAndGet();
                return oldWindow;
            }
        });

        if (window.count.get() > maxQpsPerIp) {
            logger.warn("Security Alert: Rate limit exceeded for client IP {} ({} QPS > max {})",
                    clientIp, window.count.get(), maxQpsPerIp);
            return false;
        }

        return true;
    }

    /**
     * Checks if query type is ANY (which can be used for DNS Amplification DDoS)
     */
    public boolean isAnyQuery(int qType) {
        return qType == 255; // 255 = ANY
    }

    private static class IpRateLimitWindow {
        final long timestampSecond;
        final AtomicInteger count;

        IpRateLimitWindow(long timestampSecond, int initialCount) {
            this.timestampSecond = timestampSecond;
            this.count = new AtomicInteger(initialCount);
        }
    }
}
