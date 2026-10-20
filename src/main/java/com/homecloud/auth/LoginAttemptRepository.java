package com.homecloud.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {

    Optional<LoginAttempt> findByUsernameAndClientIp(String username, String clientIp);

    void deleteByFirstAttemptAtBefore(Instant time);
}