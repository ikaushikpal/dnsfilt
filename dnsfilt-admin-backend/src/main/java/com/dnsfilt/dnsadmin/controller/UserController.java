package com.dnsfilt.dnsadmin.controller;

import com.dnsfilt.dnsadmin.dto.user.CreateUserRequest;
import com.dnsfilt.dnsadmin.dto.user.UserResponse;
import com.dnsfilt.dnsadmin.entity.Role;
import com.dnsfilt.dnsadmin.entity.UserEntity;
import com.dnsfilt.dnsadmin.repository.RefreshTokenRepository;
import com.dnsfilt.dnsadmin.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * UserController
 * 
 * Provides administrative user account management, password provisioning,
 * and Role-Based Access Control (RBAC) assignments (ROLE_ADMIN, ROLE_OPERATOR, ROLE_VIEWER).
 */
@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserController {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    public UserController(UserRepository userRepository,
                          RefreshTokenRepository refreshTokenRepository,
                          PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Lists all registered user accounts with sanitized password hashes.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        List<UserEntity> users = userRepository.findAll();
        List<UserResponse> responseList = users.stream()
                .map(UserResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(responseList);
    }

    /**
     * Creates a new user account with the specified role.
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> createUser(@RequestBody CreateUserRequest request, Authentication authentication) {
        String username = request.username();
        String rawPassword = request.password();
        String email = request.email() != null && !request.email().trim().isEmpty() 
                ? request.email() 
                : username + "@dnsfilt.internal";
        String roleStr = request.role();

        if (username == null || rawPassword == null || userRepository.existsByUsername(username)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username already exists or invalid"));
        }

        Role targetRole = Role.fromString(roleStr);
        UserEntity newUser = new UserEntity(username, passwordEncoder.encode(rawPassword), email, targetRole);
        UserEntity saved = userRepository.save(newUser);

        return ResponseEntity.ok(UserResponse.fromEntity(saved));
    }

    /**
     * Deletes a user account by unique user ID.
     * Rule: Regular Admin cannot delete other Admins. Only kaushik (Super Admin) can.
     */
    @Transactional
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> deleteUser(@PathVariable Long id, Authentication authentication) {
        Optional<UserEntity> targetOpt = userRepository.findById(id);
        if (targetOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        UserEntity targetUser = targetOpt.get();
        String callerUsername = authentication != null ? authentication.getName() : "";
        boolean isCallerSuperAdmin = callerUsername.equalsIgnoreCase("kaushik");

        // Protect primary super admin account
        if (targetUser.getUsername().equalsIgnoreCase("kaushik")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot delete the primary Super Admin account (kaushik)"));
        }

        // If target is an Admin and caller is NOT kaushik (Super Admin) and not deleting self:
        boolean isTargetAdmin = targetUser.getRole() == Role.ROLE_ADMIN;
        boolean isSelf = targetUser.getUsername().equalsIgnoreCase(callerUsername);

        if (isTargetAdmin && !isCallerSuperAdmin && !isSelf) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Only Super Admin (kaushik) can remove other Admin accounts."));
        }

        // 1. Delete child refresh tokens first to prevent ORA-02292 foreign key violation
        try {
            refreshTokenRepository.deleteByUser(targetUser);
        } catch (Exception e) {
            // Ignore if no child tokens
        }

        // 2. Delete user record
        userRepository.delete(targetUser);
        return ResponseEntity.ok(Map.of("message", "User account deleted successfully."));
    }

    /**
     * Deletes the currently authenticated user's own account.
     */
    @Transactional
    @DeleteMapping("/me")
    public ResponseEntity<?> deleteSelfAccount(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String callerUsername = authentication.getName();
        if (callerUsername.equalsIgnoreCase("kaushik")) {
            return ResponseEntity.badRequest().body(Map.of("error", "The primary Super Admin account (kaushik) cannot be deleted."));
        }

        Optional<UserEntity> userOpt = userRepository.findByUsername(callerUsername);
        if (userOpt.isPresent()) {
            UserEntity user = userOpt.get();
            // Delete child refresh tokens first to avoid ORA-02292 foreign key constraint violation
            try {
                refreshTokenRepository.deleteByUser(user);
            } catch (Exception e) {
                // Ignore if no tokens
            }
            userRepository.delete(user);
            return ResponseEntity.ok(Map.of("message", "Your account has been deleted."));
        }

        return ResponseEntity.notFound().build();
    }
}
