package com.dnsfilt.dnsresolver.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AppConfig
 * 
 * Centralized Configuration Manager for dnsfilt-resolver.
 * Implements the Bill Pugh Singleton Pattern.
 */
public class AppConfig {
    private static final Logger logger = LoggerFactory.getLogger(AppConfig.class);
    private final Map<String, String> envVariables = new ConcurrentHashMap<>();

    private AppConfig() {
        loadEnvVariables();
    }

    private static class InstanceHolder {
        private static final AppConfig INSTANCE = new AppConfig();
    }

    public static AppConfig getInstance() {
        return InstanceHolder.INSTANCE;
    }

    private void loadEnvVariables() {
        String targetProfile = System.getProperty("app.env");
        if (targetProfile == null || targetProfile.trim().isEmpty()) {
            targetProfile = System.getProperty("env");
        }
        if (targetProfile == null || targetProfile.trim().isEmpty()) {
            targetProfile = System.getenv("APP_ENV");
        }
        if (targetProfile == null || targetProfile.trim().isEmpty()) {
            targetProfile = System.getenv("ENV");
        }

        String specificEnvFile = System.getProperty("env.file");
        if (specificEnvFile == null || specificEnvFile.trim().isEmpty()) {
            specificEnvFile = System.getenv("ENV_FILE");
        }

        List<String> candidateFileNames = new ArrayList<>();
        if (specificEnvFile != null && !specificEnvFile.trim().isEmpty()) {
            candidateFileNames.add(specificEnvFile.trim());
        }
        if (targetProfile != null && !targetProfile.trim().isEmpty()) {
            String cleanProfile = targetProfile.trim().toLowerCase();
            candidateFileNames.add(cleanProfile + ".env");
            candidateFileNames.add("." + cleanProfile + ".env");
        }
        
        // Standard priority files
        candidateFileNames.add(".env");
        candidateFileNames.add(".env.local");
        candidateFileNames.add("local.env");
        candidateFileNames.add("prod.env");

        Dotenv dotenv = null;
        for (String fileName : candidateFileNames) {
            String[] searchPaths = {
                    new File(fileName).getAbsolutePath(),
                    new File("dnsfilt-resolver/" + fileName).getAbsolutePath(),
                    new File("../" + fileName).getAbsolutePath()
            };

            for (String envPath : searchPaths) {
                File envFile = new File(envPath);
                if (envFile.exists() && envFile.isFile() && envFile.length() > 0) {
                    try {
                        dotenv = Dotenv.configure()
                                .directory(envFile.getParent())
                                .filename(envFile.getName())
                                .ignoreIfMissing()
                                .load();
                        logger.info("Active configuration loaded from profile/file: {}", envPath);
                        break;
                    } catch (Exception e) {
                        logger.warn("Failed loading env file at {}: {}", envPath, e.getMessage());
                    }
                }
            }
            if (dotenv != null)
                break;
        }

        if (dotenv != null) {
            for (io.github.cdimascio.dotenv.DotenvEntry entry : dotenv.entries()) {
                String key = entry.getKey();
                String val = entry.getValue();
                envVariables.put(key, val);
                envVariables.put(key.toLowerCase().replace('_', '.'), val);
            }
            logger.info("Loaded {} environment properties into AppConfig.", envVariables.size() / 2);
        } else {
            logger.info("No .env file found on disk. Reading directly from OS environment variables.");
        }
    }

    public String getEnvVariable(String key) {
        if (key == null)
            return null;

        // 1. Check loaded .env file map
        String envKey = key.toUpperCase().replace('.', '_');
        String val = envVariables.get(envKey);
        if (val != null && !val.trim().isEmpty())
            return val.trim();

        val = envVariables.get(key);
        if (val != null && !val.trim().isEmpty())
            return val.trim();

        val = envVariables.get(key.toLowerCase());
        if (val != null && !val.trim().isEmpty())
            return val.trim();

        // 2. Fallback to OS Environment variables
        val = System.getenv(envKey);
        if (val != null && !val.trim().isEmpty())
            return val.trim();

        val = System.getenv(key);
        if (val != null && !val.trim().isEmpty())
            return val.trim();

        // 3. Fallback to JVM System properties (-Dkey=value)
        val = System.getProperty(key);
        if (val != null && !val.trim().isEmpty())
            return val.trim();

        return null;
    }

    public String getEnvVariable(String key, String defaultValue) {
        String val = getEnvVariable(key);
        return (val != null && !val.isEmpty()) ? val : defaultValue;
    }
}
