package com.dnsfilt.dnsadmin.entity;

/**
 * Role
 * 
 * Defines enterprise Role-Based Access Control (RBAC) user privileges for DNSFilt:
 * 
 * 1. ROLE_ADMIN:
 *    - Full administrative control.
 *    - Manage user accounts, password resets, resolver cluster scaling, and domain rules.
 * 
 * 2. ROLE_OPERATOR:
 *    - Day-to-day DNS operational management.
 *    - Create, modify, and delete domain block rules and custom DNS records.
 *    - Cannot manage users or modify resolver cluster infrastructure.
 * 
 * 3. ROLE_VIEWER:
 *    - Read-only dashboard and analytics auditor.
 *    - Can inspect real-time query metrics, 24h traffic charts, security categories, and top clients.
 *    - Blocked from adding, editing, or deleting rules, records, or users.
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
