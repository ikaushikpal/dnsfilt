package com.dnsfilt.dnsadmin.dto.user;

import com.dnsfilt.dnsadmin.entity.Role;
import com.dnsfilt.dnsadmin.entity.UserEntity;

/**
 * Sanitized user response payload.
 */
public record UserResponse(
    Long id,
    String username,
    String email,
    Role role
) {
    public static UserResponse fromEntity(UserEntity entity) {
        return new UserResponse(
            entity.getId(),
            entity.getUsername(),
            entity.getEmail(),
            entity.getRole()
        );
    }
}
