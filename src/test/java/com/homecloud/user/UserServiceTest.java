package com.homecloud.user;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

public class UserServiceTest {
    UserRepository userRepository = mock(UserRepository.class);
    PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    UserService userService = new UserService(userRepository, passwordEncoder);

    @Test
    void shouldRejectDuplicateUsername() {

        when(userRepository.existsByUsername("kavin"))
                .thenReturn(true);

        assertThrows(
                UsernameAlreadyExistsException.class,
                () -> userService.createUser("kavin", "kavin@example.com", "password")
        );

    }

    @Test
    void shouldRejectDuplicateEmail() {

        when(userRepository.existsByUsername("kavin"))
                .thenReturn(false);

        when(userRepository.existsByEmail("kavin@example.com"))
                .thenReturn(true);

        assertThrows(
                EmailAlreadyExistsException.class,
                () -> userService.createUser("kavin", "kavin@example.com", "password")
        );
    }

    @Test
    void shouldCreateUser() {

        when(userRepository.existsByUsername("kavin"))
                .thenReturn(false);

        when(userRepository.existsByEmail("kavin@example.com"))
                .thenReturn(false);

        when(passwordEncoder.encode("password"))
                .thenReturn("encodedPassword");

        userService.createUser("kavin", "kavin@example.com", "password");

        verify(userRepository, times(1)).save(any(User.class));
    }

}
