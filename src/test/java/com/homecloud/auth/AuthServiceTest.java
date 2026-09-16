package com.homecloud.auth;

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

    AuthService authService =
            new AuthService(userRepository, passwordEncoder);

    @Test
    void shouldLoginWithCorrectPassword() {

        User user = new User("kavin-auth", "kavin-auth@example.com");
        user.setPasswordHash("stored-hash");

        when(userRepository.findByUsername("kavin-auth"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "test-password-123",
                "stored-hash"))
                .thenReturn(true);

        User result = authService.login(
                "kavin-auth",
                "test-password-123"
        );

        assertEquals("kavin-auth", result.getUsername());

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