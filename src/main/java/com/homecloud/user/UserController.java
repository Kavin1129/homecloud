package com.homecloud.user;

import com.homecloud.user.dto.CreateUserRequest;
import com.homecloud.user.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public UserResponse createUser(@Valid @RequestBody CreateUserRequest request) {

         User user = userService.createUser(
                request.getUsername(),
                request.getEmail()
        );
        return new UserResponse(user);
    }

}
