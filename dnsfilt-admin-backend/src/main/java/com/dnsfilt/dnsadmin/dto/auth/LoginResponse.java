package com.dnsfilt.dnsadmin.dto.auth;

/**
 * Login authentication response containing JWT access & refresh tokens.
 */
public record LoginResponse(
    String accessToken,
    String refreshToken,
    String username,
    String role
) {}
