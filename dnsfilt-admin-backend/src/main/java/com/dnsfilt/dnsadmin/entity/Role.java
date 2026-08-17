package com.dnsfilt.dnsadmin.entity;

/**
 * Role
 * 
 * Defines enterprise Role-Based Access Control (RBAC) user privileges for DNSFilt:
 * 1. ROLE_ADMIN: Full administrative control.
 * 2. ROLE_OPERATOR: DNS rules and telemetry operations.
 * 3. ROLE_VIEWER: Read-only access.
 */
public enum Role {
    ROLE_ADMIN,
    ROLE_OPERATOR,
    ROLE_VIEWER;

    /**
     * Helper to safely parse role strings ignoring casing and optional "ROLE_" prefix.
     */
    public static Role fromString(String roleStr) {
        if (roleStr == null || roleStr.trim().isEmpty()) {
            return ROLE_VIEWER;
        }
        String normalized = roleStr.trim().toUpperCase();
        if (!normalized.startsWith("ROLE_")) {
            normalized = "ROLE_" + normalized;
        }
        try {
            return Role.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return ROLE_VIEWER;
        }
    }
}
