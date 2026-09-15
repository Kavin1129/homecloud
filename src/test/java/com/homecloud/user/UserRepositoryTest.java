package com.homecloud.user;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@Transactional
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldSaveAndFindUser() {

        User user = new User("kavin-test", "kavin-test@example.com");

        User savedUser = userRepository.save(user);

        User foundUser = userRepository.findById(savedUser.getId())
                .orElseThrow();

        assertEquals(savedUser.getId(), foundUser.getId());
        assertEquals("kavin-test", foundUser.getUsername());
        assertEquals("kavin-test@example.com", foundUser.getEmail());
    }

    @Test
    void shouldSaveUser() {

        User user = new User("kavin-test", "kavin-test@example.com");

        User savedUser = userRepository.save(user);

        assertNotNull(savedUser.getId());
        assertEquals("kavin-test", savedUser.getUsername());
        assertEquals("kavin-test@example.com", savedUser.getEmail());
    }


}