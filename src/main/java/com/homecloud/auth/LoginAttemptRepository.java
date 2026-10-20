package com.homecloud.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.Instant;
import java.util.Optional;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<LoginAttempt> findByUsernameAndClientIp(String username, String clientIp);

    void deleteByFirstAttemptAtBefore(Instant time);
}