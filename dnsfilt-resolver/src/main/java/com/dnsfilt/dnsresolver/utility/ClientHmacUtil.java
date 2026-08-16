package com.dnsfilt.dnsresolver.utility;

import com.dnsfilt.dnsresolver.config.AppConfig;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * ClientHmacUtil
 * 
 * Hashes raw client IP addresses using HMAC-SHA256 for GDPR/privacy compliance
 * before recording metric aggregations or publishing to analytics pipelines.
 */
public class ClientHmacUtil {
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String DEFAULT_SECRET = "dnsfilt-secret-hmac-salt-key-2026";

    public static String hashClientIp(String clientIp) {
        if (clientIp == null || clientIp.trim().isEmpty()) {
            return "unknown";
        }

        AppConfig config = AppConfig.getInstance();
        String secretKey = config.getEnvVariable("CLIENT_HASH_SECRET");
        if (secretKey == null || secretKey.trim().isEmpty()) {
            secretKey = config.getEnvVariable("CLIENT_HMAC_SECRET", DEFAULT_SECRET);
        }

        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(clientIp.getBytes(StandardCharsets.UTF_8));
            
            // Hex string representation (first 16 chars for compact indexing)
            StringBuilder hexString = new StringBuilder();
            for (byte b : hmacBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.substring(0, 16);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            return "hash_error";
        }
    }
}
