package com.dnsfilt.dnsadmin.dto.user;

/**
 * User creation request payload.
 */
public record CreateUserRequest(
    String username,
    String password,
    String email,
    String role
) {}
