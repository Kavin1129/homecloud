package com.homecloud.auth;

import com.homecloud.user.User;
import com.homecloud.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Repository;

@Repository
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User login(String username, String password){

        User user = userRepository.findByUsername(username)
                .orElseThrow(()-> new InvalidCredentialsException("Invalid username or password"));

        if(!passwordEncoder.matches(password, user.getPasswordHash())){
            throw new InvalidCredentialsException("Invalid username or password");
        }

        return user;
    }

}
