package com.homecloud.user;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User createUser(String username, String email, String password){

        String normalizedUsername =
                username.trim().toLowerCase(Locale.ROOT);

        String normalizedEmail =
                email.trim().toLowerCase(Locale.ROOT);

        if(userRepository.existsByUsername(normalizedUsername)){
            throw new UsernameAlreadyExistsException("Username already exists: " + normalizedUsername);
        }

        if(userRepository.existsByEmail(normalizedEmail)){
            throw new EmailAlreadyExistsException("Email already exists: " + normalizedEmail);
        }

        String passwordHash = passwordEncoder.encode(password);
        User user = new User(normalizedUsername, normalizedEmail);
        user.setPasswordHash(passwordHash);
        return userRepository.save(user);
    }
}
