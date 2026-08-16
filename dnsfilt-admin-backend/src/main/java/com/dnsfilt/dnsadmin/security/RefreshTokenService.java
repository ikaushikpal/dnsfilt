package com.dnsfilt.dnsadmin.security;

import com.dnsfilt.dnsadmin.entity.RefreshTokenEntity;
import com.dnsfilt.dnsadmin.entity.UserEntity;
import com.dnsfilt.dnsadmin.repository.RefreshTokenRepository;
import com.dnsfilt.dnsadmin.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    private final long refreshTokenDurationMs = 604_800_000; // 7 days

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, UserRepository userRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public RefreshTokenEntity createRefreshToken(String username) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        // Find existing token for this user to update in-place, or create a new entity if none exists
        Optional<RefreshTokenEntity> existingTokenOpt = refreshTokenRepository.findByUser(user);
        RefreshTokenEntity refreshToken = existingTokenOpt.orElseGet(RefreshTokenEntity::new);

        refreshToken.setUser(user);
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setExpiryDate(Instant.now().plusMillis(refreshTokenDurationMs));

        return refreshTokenRepository.save(refreshToken);
    }

    public RefreshTokenEntity verifyExpiration(RefreshTokenEntity token) {
        if (token.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(token);
            throw new RuntimeException("Refresh token was expired. Please make a new login request.");
        }
        return token;
    }

    public RefreshTokenEntity findByToken(String token) {
        return refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Refresh token not found"));
    }

    @Transactional
    public void deleteByUsername(String username) {
        userRepository.findByUsername(username).ifPresent(refreshTokenRepository::deleteByUser);
    }
}
