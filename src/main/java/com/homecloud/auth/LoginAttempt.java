package com.homecloud.auth;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(
        name = "login_attempts",
        indexes = {
                @Index(
                        name = "idx_login_attempts_lookup",
                        columnList = "username, client_ip"
                )
        }
)
public class LoginAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    @Column(name = "client_ip", nullable = false, length = 45)
    private String clientIp;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "first_attempt_at", nullable = false)
    private Instant firstAttemptAt;

    @Column(name = "blocked_until")
    private Instant blockedUntil;

    protected LoginAttempt() {
    }

    public LoginAttempt(
            String username,
            String clientIp,
            Instant firstAttemptAt
    ) {
        this.username = username;
        this.clientIp = clientIp;
        this.attemptCount = 1;
        this.firstAttemptAt = firstAttemptAt;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getClientIp() {
        return clientIp;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Instant getFirstAttemptAt() {
        return firstAttemptAt;
    }

    public Instant getBlockedUntil() {
        return blockedUntil;
    }

    public void incrementAttemptCount() {
        this.attemptCount++;
    }

    public void reset(Instant now) {
        this.attemptCount = 0;
        this.firstAttemptAt = now;
        this.blockedUntil = null;
    }

    public void blockUntil(Instant blockedUntil) {
        this.blockedUntil = blockedUntil;
    }
}