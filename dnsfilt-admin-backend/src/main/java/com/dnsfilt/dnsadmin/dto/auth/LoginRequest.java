package com.dnsfilt.dnsadmin.dto.auth;

/**
 * Login request payload.
 */
public record LoginRequest(String username, String password) {}
