package com.dnsfilt.dnsadmin.dto.auth;

/**
 * Token refresh response payload.
 */
public record RefreshTokenResponse(String accessToken, String refreshToken) {}
