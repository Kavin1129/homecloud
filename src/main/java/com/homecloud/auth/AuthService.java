package com.homecloud.auth;

import com.homecloud.auth.dto.LoginResponse;
import com.homecloud.user.User;
import com.homecloud.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RevokedTokenRepository revokedTokenRepository;
    private final LoginAttemptService loginAttemptService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService, RevokedTokenRepository revokedTokenRepository, LoginAttemptService loginAttemptService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.revokedTokenRepository = revokedTokenRepository;
        this.loginAttemptService = loginAttemptService;
    }

    public LoginResponse login(String username, String password, String clientIp) {

        if (loginAttemptService.isBlocked(username, clientIp)) {
            throw new TooManyLoginAttemptsException(
                    "Too many login attempts. Try again later."
            );
        }

        User user = userRepository.findByUsername(username).orElse(null);

        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {

            loginAttemptService.recordFailure(username, clientIp);

            throw new InvalidCredentialsException(
                    "Invalid username or password"
            );
        }

        loginAttemptService.recordSuccess(username, clientIp);

        return new LoginResponse(jwtService.generateToken(user.getUsername()));
    }

    public void logout(String token) {

        String tokenHash = TokenHashUtil.hash(token);

        Instant expiresAt = jwtService.extractExpiration(token);

        if (!revokedTokenRepository.existsByTokenHash(tokenHash)) {

            RevokedToken revokedToken = new RevokedToken(tokenHash, expiresAt);

            revokedTokenRepository.save(revokedToken);
        }
    }
}