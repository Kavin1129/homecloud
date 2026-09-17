package com.homecloud.auth;

import com.homecloud.auth.dto.LoginResponse;
import com.homecloud.user.User;
import com.homecloud.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    UserRepository userRepository = mock(UserRepository.class);
    PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    JwtService jwtService = mock(JwtService.class);

    AuthService authService =
            new AuthService(userRepository, passwordEncoder, jwtService);

    @Test
    void shouldLoginWithCorrectPassword() {

        User user = new User("kavin-auth", "kavin-auth@example.com");
        user.setPasswordHash("stored-hash");

        when(userRepository.findByUsername("kavin-auth"))
                .thenReturn(Optional.of(user));

        when(jwtService.generateToken("kavin-auth"))
                .thenReturn("test-token");

        when(passwordEncoder.matches(
                "test-password-123",
                "stored-hash"))
                .thenReturn(true);

        LoginResponse result = authService.login(
                "kavin-auth",
                "test-password-123"
        );

        assertEquals("test-token", result.getToken());

        verify(passwordEncoder)
                .matches("test-password-123", "stored-hash");
    }

    @Test
    void shouldRejectWrongPassword() {

        User user = new User("kavin-auth", "kavin-auth@example.com");
        user.setPasswordHash("stored-hash");

        when(userRepository.findByUsername("kavin-auth"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "wrong-password",
                "stored-hash"))
                .thenReturn(false);

        assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(
                        "kavin-auth",
                        "wrong-password"
                )
        );
    }

    @Test
    void shouldRejectUnknownUsername() {

        when(userRepository.findByUsername("unknown"))
                .thenReturn(Optional.empty());

        assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(
                        "unknown",
                        "some-password"
                )
        );

        verify(passwordEncoder, never())
                .matches(anyString(), anyString());
    }
}