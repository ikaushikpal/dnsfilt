package com.dnsfilt.dnsresolver.config;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class BLDomain {
    private static final String FILE_PATH = "src/main/resources/BlackListedDomains.dat"; // Adjust the path as necessary
    private Set<String> domains;

    // Logger initialization
    private static final Logger logger = Logger.getLogger(BLDomain.class.getName());

    // Step 1: Private static instance of the class (Singleton instance)
    private static BLDomain instance;

    // Step 2: Private constructor to prevent direct instantiation
    private BLDomain() {
        this.domains = new HashSet<>();
        loadDomainsFromDAT();
    }

    // Step 3: Public method to provide access to the single instance
    public static BLDomain getInstance() {
        if (instance == null) {
            synchronized (BLDomain.class) {
                if (instance == null) { // Double-checked locking for thread-safety
                    instance = new BLDomain();
                }
            }
        }
        return instance;
    }

    // Step 4: Loading domains from the .dat file
    private void loadDomainsFromDAT() {
        try (BufferedReader reader = new BufferedReader(new FileReader(FILE_PATH))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String domain = line.trim();
                if (!domain.isEmpty()) {
                    this.domains.add(domain);
                    logger.info("Loaded domain: " + domain); // Logging domain being loaded
                }
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error reading the blacklist file", e); // Logging error with exception
        }
    }

    // Step 5: Method to check if a domain is blacklisted
    public boolean isDomainBL(String domain) {
        return this.domains.contains(domain.trim()); // Ensuring trimming for consistency
    }

    // Step 6: Check if multiple domains are blacklisted
    public List<Boolean> isDomainsBL(List<String> domains) {
        return domains.parallelStream()
                       .map(this::isDomainBL)
                       .collect(Collectors.toList());
    }

    // Optionally, you can add a method to refresh the domains if needed
    public void refreshDomains() {
        this.domains.clear();
        loadDomainsFromDAT();
    }
}
