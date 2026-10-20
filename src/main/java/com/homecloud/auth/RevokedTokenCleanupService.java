package com.homecloud.auth;

import jakarta.transaction.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class RevokedTokenCleanupService {

    private final RevokedTokenRepository revokedTokenRepository;

    public RevokedTokenCleanupService(RevokedTokenRepository revokedTokenRepository) {
        this.revokedTokenRepository = revokedTokenRepository;
    }

    @Scheduled(fixedRate = 60 * 60 * 1000)
    @Transactional
    public void cleanupExpiredTokens() {
        revokedTokenRepository.deleteByExpiresAtBefore(Instant.now());
    }
}