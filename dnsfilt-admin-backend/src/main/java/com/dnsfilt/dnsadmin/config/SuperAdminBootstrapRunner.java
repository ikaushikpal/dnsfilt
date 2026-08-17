package com.dnsfilt.dnsadmin.config;

import com.dnsfilt.dnsadmin.entity.Role;
import com.dnsfilt.dnsadmin.entity.UserEntity;
import com.dnsfilt.dnsadmin.repository.RefreshTokenRepository;
import com.dnsfilt.dnsadmin.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * SuperAdminBootstrapRunner
 * 
 * Automatically provisions user 'kaushik' as the primary ROLE_ADMIN
 * and removes the legacy 'admin' user from the database upon application startup.
 */
@Component
public class SuperAdminBootstrapRunner implements CommandLineRunner {
    private static final Logger logger = LoggerFactory.getLogger(SuperAdminBootstrapRunner.class);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${KAUSHIK_SUPERADMIN_PASSWORD:}")
    private String kaushikPasswordEnv;

    public SuperAdminBootstrapRunner(UserRepository userRepository,
                                     RefreshTokenRepository refreshTokenRepository,
                                     PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        try {
            // 1. Ensure user 'kaushik' is ROLE_ADMIN
            Optional<UserEntity> kaushikOpt = userRepository.findByUsername("kaushik");
            if (kaushikOpt.isPresent()) {
                UserEntity kaushik = kaushikOpt.get();
                boolean modified = false;
                if (kaushik.getRole() != Role.ROLE_ADMIN) {
                    kaushik.setRole(Role.ROLE_ADMIN);
                    modified = true;
                }
                if (kaushikPasswordEnv != null && !kaushikPasswordEnv.trim().isEmpty()) {
                    kaushik.setPassword(passwordEncoder.encode(kaushikPasswordEnv.trim()));
                    modified = true;
                }
                if (modified) {
                    userRepository.save(kaushik);
                    logger.info("Successfully updated user 'kaushik' with ROLE_ADMIN privileges.");
                }
            } else {
                String pass = (kaushikPasswordEnv != null && !kaushikPasswordEnv.trim().isEmpty())
                        ? kaushikPasswordEnv.trim()
                        : "Kaushik@123";
                UserEntity kaushik = new UserEntity(
                        "kaushik",
                        passwordEncoder.encode(pass),
                        "kaushik@dnsfilt.com",
                        Role.ROLE_ADMIN
                );
                userRepository.save(kaushik);
                logger.info("Successfully created super admin user 'kaushik' with ROLE_ADMIN.");
            }

            // 2. Remove legacy user 'admin' if present
            Optional<UserEntity> adminOpt = userRepository.findByUsername("admin");
            if (adminOpt.isPresent()) {
                UserEntity adminUser = adminOpt.get();
                try {
                    refreshTokenRepository.deleteByUser(adminUser);
                } catch (Exception e) {
                    logger.warn("Notice: Cleaned up refresh tokens for admin user: {}", e.getMessage());
                }
                try {
                    userRepository.delete(adminUser);
                    logger.info("Successfully removed legacy user 'admin' from database.");
                } catch (Exception e) {
                    logger.warn("Could not delete user 'admin': {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.warn("SuperAdminBootstrapRunner warning: {}", e.getMessage());
        }
    }
}
