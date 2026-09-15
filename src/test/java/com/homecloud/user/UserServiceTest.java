package com.homecloud.user;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

public class UserServiceTest {
    UserRepository userRepository = mock(UserRepository.class);

    UserService userService = new UserService(userRepository);

    @Test
    void shouldRejectDuplicateUsername() {

        when(userRepository.existsByUsername("kavin"))
                .thenReturn(true);

        assertThrows(
                UsernameAlreadyExistsException.class,
                () -> userService.createUser("kavin", "kavin@example.com")
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
                () -> userService.createUser("kavin", "kavin@example.com")
        );
    }

    @Test
    void shouldCreateUser() {

        when(userRepository.existsByUsername("kavin"))
                .thenReturn(false);

        when(userRepository.existsByEmail("kavin@example.com"))
                .thenReturn(false);

        userService.createUser("kavin", "kavin@example.com");

        verify(userRepository, times(1)).save(any(User.class));
    }

}
