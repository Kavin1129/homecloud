package com.homecloud.user;

import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User createUser(String username, String email){

        if(userRepository.existsByUsername(username)){
            throw new UsernameAlreadyExistsException("Username already exists: " + username);
        }

        if(userRepository.existsByEmail(email)){
            throw new EmailAlreadyExistsException("Email already exists: " + email);
        }
        User user = new User(username, email);
        return userRepository.save(user);
    }
}
