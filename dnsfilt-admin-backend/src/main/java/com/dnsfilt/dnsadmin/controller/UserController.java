package com.dnsfilt.dnsadmin.controller;

import com.dnsfilt.dnsadmin.dto.user.CreateUserRequest;
import com.dnsfilt.dnsadmin.dto.user.UserResponse;
import com.dnsfilt.dnsadmin.entity.Role;
import com.dnsfilt.dnsadmin.entity.UserEntity;
import com.dnsfilt.dnsadmin.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * UserController
 * 
 * Provides administrative user account management, password provisioning,
 * and Role-Based Access Control (RBAC) assignments (ROLE_ADMIN, ROLE_OPERATOR, ROLE_VIEWER).
 * 
 * Access: Strictly restricted to administrators with ROLE_ADMIN authority.
 */
@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Lists all registered user accounts with sanitized password hashes.
     */
    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        List<UserEntity> users = userRepository.findAll();
        List<UserResponse> responseList = users.stream()
                .map(UserResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(responseList);
    }

    /**
     * Creates a new user account with the specified role.
     * Supported roles: ROLE_ADMIN, ROLE_OPERATOR, ROLE_VIEWER.
     */
    @PostMapping
    public ResponseEntity<UserResponse> createUser(@RequestBody CreateUserRequest request) {
        String username = request.username();
        String rawPassword = request.password();
        String email = request.email() != null && !request.email().trim().isEmpty() 
                ? request.email() 
                : username + "@dnsfilt.internal";
        String roleStr = request.role();

        if (username == null || rawPassword == null || userRepository.existsByUsername(username)) {
            return ResponseEntity.badRequest().build();
        }

        Role role = Role.fromString(roleStr);
        UserEntity newUser = new UserEntity(username, passwordEncoder.encode(rawPassword), email, role);
        UserEntity saved = userRepository.save(newUser);

        return ResponseEntity.ok(UserResponse.fromEntity(saved));
    }

    /**
     * Deletes a user account by unique user ID.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        if (userRepository.existsById(id)) {
            userRepository.deleteById(id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }
}
