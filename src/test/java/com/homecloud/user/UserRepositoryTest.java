package com.homecloud.user;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Transactional
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldSaveUser() {

        User user = new User("kavin-test", "kavin-test@example.com");

        User savedUser = userRepository.save(user);

        System.out.println("Generated ID: " + savedUser.getId());
    }

    @Test
    void shouldSaveAndFindUser() {

        User user = new User("kavin-test", "kavin-test@example.com");

        User savedUser = userRepository.save(user);

        User foundUser = userRepository.findById(savedUser.getId())
                .orElseThrow();

        System.out.println("ID: " + foundUser.getId());
        System.out.println("Username: " + foundUser.getUsername());
        System.out.println("Email: " + foundUser.getEmail());
    }

}