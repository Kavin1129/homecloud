package com.homecloud.auth;

import com.homecloud.auth.dto.LoginResponse;
import com.homecloud.user.User;
import com.homecloud.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Repository;

@Repository
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public LoginResponse login(String username, String password){

        User user = userRepository.findByUsername(username)
                .orElseThrow(()-> new InvalidCredentialsException("Invalid username or password"));

        if(!passwordEncoder.matches(password, user.getPasswordHash())){
            throw new InvalidCredentialsException("Invalid username or password");
        }

        return new LoginResponse(jwtService.generateToken(user.getUsername()));

    }

}
