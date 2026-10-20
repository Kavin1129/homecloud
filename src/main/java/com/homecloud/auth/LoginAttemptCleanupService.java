package com.homecloud.auth;

import jakarta.transaction.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class LoginAttemptCleanupService {

    private final LoginAttemptRepository loginAttemptRepository;

    public LoginAttemptCleanupService(
            LoginAttemptRepository loginAttemptRepository) {
        this.loginAttemptRepository = loginAttemptRepository;
    }

    @Scheduled(fixedRate = 60 * 60 * 1000)
    @Transactional
    public void cleanupExpiredAttempts() {
        Instant cutoff = Instant.now().minusSeconds(15 * 60);

        loginAttemptRepository.deleteByFirstAttemptAtBefore(cutoff);
    }
}