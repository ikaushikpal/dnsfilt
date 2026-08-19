package com.dnsfilt.dnsadmin.controller;

import com.dnsfilt.dnsadmin.dto.auth.ChangePasswordRequest;
import com.dnsfilt.dnsadmin.dto.auth.LoginRequest;
import com.dnsfilt.dnsadmin.dto.auth.LoginResponse;
import com.dnsfilt.dnsadmin.dto.auth.LogoutRequest;
import com.dnsfilt.dnsadmin.dto.auth.RefreshTokenRequest;
import com.dnsfilt.dnsadmin.dto.auth.RefreshTokenResponse;
import com.dnsfilt.dnsadmin.dto.common.MessageResponse;
import com.dnsfilt.dnsadmin.entity.RefreshTokenEntity;
import com.dnsfilt.dnsadmin.entity.UserEntity;
import com.dnsfilt.dnsadmin.repository.UserRepository;
import com.dnsfilt.dnsadmin.security.JwtTokenProvider;
import com.dnsfilt.dnsadmin.security.RefreshTokenService;
import com.dnsfilt.dnsadmin.security.TokenBlacklistService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AuthController
 * 
 * Handles user authentication, token issuance, refresh token rotation,
 * password updates, and instantaneous in-memory logout revocation via Caffeine.
 */
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;
    private final TokenBlacklistService tokenBlacklistService;
    private final PasswordEncoder passwordEncoder;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtTokenProvider tokenProvider,
                          RefreshTokenService refreshTokenService,
                          UserRepository userRepository,
                          TokenBlacklistService tokenBlacklistService,
                          PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
        this.refreshTokenService = refreshTokenService;
        this.userRepository = userRepository;
        this.tokenBlacklistService = tokenBlacklistService;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest loginRequest) {
        String username = loginRequest.username();
        String password = loginRequest.password();

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password)
        );

        UserEntity user = userRepository.findByUsername(username).orElseThrow();
        String accessToken = tokenProvider.generateToken(username, user.getRole().name());
        RefreshTokenEntity refreshToken = refreshTokenService.createRefreshToken(username);

        LoginResponse response = new LoginResponse(
                accessToken,
                refreshToken.getToken(),
                username,
                user.getRole().name()
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshTokenResponse> refresh(@RequestBody RefreshTokenRequest refreshRequest) {
        String tokenStr = refreshRequest.refreshToken();
        if (tokenStr == null || tokenStr.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        // Check if refresh token was blacklisted via logout
        if (tokenBlacklistService.isBlacklisted(tokenStr)) {
            return ResponseEntity.status(401).build();
        }

        RefreshTokenEntity refreshToken = refreshTokenService.findByToken(tokenStr);
        refreshTokenService.verifyExpiration(refreshToken);

        UserEntity user = refreshToken.getUser();
        String newAccessToken = tokenProvider.generateToken(user.getUsername(), user.getRole().name());

        RefreshTokenResponse response = new RefreshTokenResponse(
                newAccessToken,
                refreshToken.getToken()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/auth/validate
     * 
     * Validates client session. If accessToken is still valid, returns valid: true.
     * If accessToken is expired, attempts to exchange the refreshToken for a new accessToken.
     * If both are invalid, returns 401 Unauthorized so client can cleanly log out.
     */
    @PostMapping("/validate")
    public ResponseEntity<?> validateToken(@RequestBody(required = false) Map<String, String> request) {
        if (request == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("valid", false, "message", "No tokens provided"));
        }

        String accessToken = request.get("accessToken");
        String refreshTokenStr = request.get("refreshToken");

        // 1. Check if accessToken is valid and not blacklisted
        if (accessToken != null && !accessToken.trim().isEmpty() && !tokenBlacklistService.isBlacklisted(accessToken)) {
            if (tokenProvider.validateToken(accessToken)) {
                try {
                    String username = tokenProvider.getUsernameFromJWT(accessToken);
                    UserEntity user = userRepository.findByUsername(username).orElse(null);
                    if (user != null) {
                        return ResponseEntity.ok(Map.of(
                                "valid", true,
                                "refreshed", false,
                                "accessToken", accessToken,
                                "refreshToken", refreshTokenStr != null ? refreshTokenStr : "",
                                "username", user.getUsername(),
                                "role", user.getRole().name()
                        ));
                    }
                } catch (Exception ignored) {}
            }
        }

        // 2. AccessToken is expired/invalid -> Attempt to use refreshToken
        if (refreshTokenStr != null && !refreshTokenStr.trim().isEmpty() && !tokenBlacklistService.isBlacklisted(refreshTokenStr)) {
            try {
                RefreshTokenEntity refreshToken = refreshTokenService.findByToken(refreshTokenStr);
                refreshTokenService.verifyExpiration(refreshToken);
                UserEntity user = refreshToken.getUser();
                if (user != null) {
                    String newAccessToken = tokenProvider.generateToken(user.getUsername(), user.getRole().name());
                    return ResponseEntity.ok(Map.of(
                            "valid", true,
                            "refreshed", true,
                            "accessToken", newAccessToken,
                            "refreshToken", refreshToken.getToken(),
                            "username", user.getUsername(),
                            "role", user.getRole().name()
                    ));
                }
            } catch (Exception ignored) {
                // Refresh token also invalid or expired
            }
        }

        // 3. Both tokens are invalid -> Return 401
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "valid", false,
                "refreshed", false,
                "message", "Session expired. Please log in again."
        ));
    }

    /**
     * POST /api/auth/change-password
     * 
     * Allows an authenticated user to change their password securely.
     */
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(Authentication authentication, @RequestBody ChangePasswordRequest req) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        String username = authentication.getName();
        UserEntity user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        if (req.newPassword() == null || req.newPassword().trim().length() < 4) {
            return ResponseEntity.badRequest().body(Map.of("error", "New password must be at least 4 characters"));
        }

        if (req.oldPassword() == null || !passwordEncoder.matches(req.oldPassword(), user.getPassword())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Current password is incorrect"));
        }

        user.setPassword(passwordEncoder.encode(req.newPassword().trim()));
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
    }

    /**
     * POST /api/auth/logout
     * 
     * Immediately revokes the client's current JWT Access Token and associated Refresh Token
     * by pushing them to the in-memory Caffeine Token Blacklist.
     */
    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(HttpServletRequest request,
                                                  @RequestBody(required = false) LogoutRequest body) {
        // 1. Blacklist Access Token from Authorization header
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String accessToken = authHeader.substring(7).trim();
            tokenBlacklistService.blacklistToken(accessToken);
        }

        // 2. Blacklist Refresh Token if provided in body
        if (body != null && body.refreshToken() != null && !body.refreshToken().trim().isEmpty()) {
            tokenBlacklistService.blacklistToken(body.refreshToken());
        }

        return ResponseEntity.ok(new MessageResponse("Successfully logged out. Tokens invalidated immediately."));
    }
}
