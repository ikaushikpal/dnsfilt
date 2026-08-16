package com.dnsfilt.dnsadmin.dto.auth;

/**
 * Logout request payload.
 */
public record LogoutRequest(String refreshToken) {}
