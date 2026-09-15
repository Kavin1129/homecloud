package com.homecloud.user;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User createUser(String username, String email, String password){

        if(userRepository.existsByUsername(username)){
            throw new UsernameAlreadyExistsException("Username already exists: " + username);
        }

        if(userRepository.existsByEmail(email)){
            throw new EmailAlreadyExistsException("Email already exists: " + email);
        }

        String passwordHash = passwordEncoder.encode(password);
        User user = new User(username, email);
        user.setPasswordHash(passwordHash);
        return userRepository.save(user);
    }
}
