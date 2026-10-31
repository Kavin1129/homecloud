package com.homecloud.auth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(15);
    private static final Duration BLOCK_DURATION = Duration.ofMinutes(15);

    private final LoginAttemptRepository loginAttemptRepository;

    public LoginAttemptService(LoginAttemptRepository loginAttemptRepository) {
        this.loginAttemptRepository = loginAttemptRepository;
    }

    @Transactional
    public boolean isBlocked(String username, String clientIp) {
        return loginAttemptRepository
                .findByUsernameAndClientIp(username, clientIp)
                .map(attempt -> {
                    Instant blockedUntil = attempt.getBlockedUntil();

                    return blockedUntil != null
                            && blockedUntil.isAfter(Instant.now());
                })
                .orElse(false);
    }

    @Transactional
    public void recordFailure(String username, String clientIp) {

        Instant now = Instant.now();

        LoginAttempt attempt = loginAttemptRepository
                .findByUsernameAndClientIp(username, clientIp)
                .orElse(null);

        if (attempt == null) {
            loginAttemptRepository.save(
                    new LoginAttempt(username, clientIp, now)
            );
            return;
        }

        if (attempt.getFirstAttemptAt().plus(ATTEMPT_WINDOW).isBefore(now)) {
            attempt.reset(now);
            attempt.incrementAttemptCount();
            loginAttemptRepository.save(attempt);
            return;
        }

        attempt.incrementAttemptCount();

        if (attempt.getAttemptCount() >= MAX_ATTEMPTS) {
            attempt.blockUntil(now.plus(BLOCK_DURATION));
        }

        loginAttemptRepository.save(attempt);
    }

    @Transactional
    public void recordSuccess(String username, String clientIp) {

        loginAttemptRepository
                .findByUsernameAndClientIp(username, clientIp)
                .ifPresent(loginAttemptRepository::delete);
    }
}