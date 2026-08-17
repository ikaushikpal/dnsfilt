package com.dnsfilt.dnsadmin.dto.auth;

public record ChangePasswordRequest(
    String oldPassword,
    String newPassword
) {}
