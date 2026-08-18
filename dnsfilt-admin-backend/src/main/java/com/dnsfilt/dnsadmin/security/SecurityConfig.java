package com.dnsfilt.dnsadmin.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * SecurityConfig
 * 
 * Enterprise Role-Based Access Control (RBAC) Security Filter Chain:
 * 
 * Access Policy Matrix:
 * - Public UI: All static files (.js, .css, .ico, .png, .jpg, .svg, index.html) & SPA routes.
 * - Public API: /api/auth/**, /actuator/health
 * - ROLE_SUPER_ADMIN & ROLE_ADMIN: Access to user management and cluster infrastructure.
 * - ROLE_OPERATOR: Manage domain rules and records.
 * - ROLE_VIEWER: Read-only access to metrics and telemetry.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // 1. Static UI Assets & SPA Routes (Public)
                .requestMatchers(
                    "/",
                    "/index.html",
                    "/favicon.ico",
                    "/site.webmanifest",
                    "/*.js",
                    "/*.css",
                    "/*.png",
                    "/*.jpg",
                    "/*.svg",
                    "/*.ico",
                    "/*.json",
                    "/assets/**",
                    "/media/**",
                    "/images/**",
                    "/browser/**"
                ).permitAll()
                .requestMatchers(
                    HttpMethod.GET,
                    "/home/**",
                    "/dashboard/**",
                    "/rules/**",
                    "/admin-rules/**",
                    "/clients/**",
                    "/resolvers/**",
                    "/threats/**",
                    "/about/**",
                    "/contact/**",
                    "/learn/**",
                    "/login/**",
                    "/services/**",
                    "/users/**",
                    "/admin/**"
                ).permitAll()

                // 2. Public API Authentication & Health Probes & Internal Config Polling
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/resolver/config").permitAll()

                // 3. Self Account Management (Authenticated)
                .requestMatchers(HttpMethod.DELETE, "/api/users/me").authenticated()

                // 4. User management & Resolver cluster scaling (SUPER_ADMIN & ADMIN)
                .requestMatchers("/api/users/**").hasAnyAuthority("ROLE_SUPER_ADMIN", "ROLE_ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/resolver/**").hasAnyAuthority("ROLE_SUPER_ADMIN", "ROLE_ADMIN")

                // 5. Rule & Domain mutations (SUPER_ADMIN, ADMIN & OPERATOR)
                .requestMatchers(HttpMethod.POST, "/api/rules/**", "/api/v1/domains/**").hasAnyAuthority("ROLE_SUPER_ADMIN", "ROLE_ADMIN", "ROLE_OPERATOR")
                .requestMatchers(HttpMethod.PUT, "/api/rules/**", "/api/v1/domains/**").hasAnyAuthority("ROLE_SUPER_ADMIN", "ROLE_ADMIN", "ROLE_OPERATOR")
                .requestMatchers(HttpMethod.DELETE, "/api/rules/**", "/api/v1/domains/**").hasAnyAuthority("ROLE_SUPER_ADMIN", "ROLE_ADMIN", "ROLE_OPERATOR")

                // 6. Analytics & Read-only inspections
                .requestMatchers(HttpMethod.GET, "/api/v1/analytics/**").hasAnyAuthority("ROLE_SUPER_ADMIN", "ROLE_ADMIN", "ROLE_OPERATOR", "ROLE_VIEWER")
                .requestMatchers(HttpMethod.GET, "/api/rules/**").hasAnyAuthority("ROLE_SUPER_ADMIN", "ROLE_ADMIN", "ROLE_OPERATOR", "ROLE_VIEWER")
                .requestMatchers(HttpMethod.GET, "/api/v1/domains/**").hasAnyAuthority("ROLE_SUPER_ADMIN", "ROLE_ADMIN", "ROLE_OPERATOR", "ROLE_VIEWER")

                // 7. All other API endpoints require authentication
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll()
            );

        http.headers(headers -> headers.frameOptions(frame -> frame.disable()));
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
