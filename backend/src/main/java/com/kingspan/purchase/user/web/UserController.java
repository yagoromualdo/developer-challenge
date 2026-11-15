package com.kingspan.purchase.user.web;

import com.kingspan.purchase.user.application.UserService;
import com.kingspan.purchase.user.domain.UserProfile;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Exclusivo de ADMIN; a restrição fica no SecurityFilterChain ({@code /users/**}). */
@RestController
@RequestMapping("/users")
class UserController {

    private final UserService userService;

    UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    List<UserResponse> list() {
        return userService.list().stream().map(UserResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        UserProfile profile = new UserProfile(request.role(), request.approverLevel(), request.costCenterId());
        return UserResponse.from(userService.create(request.name(), request.email(), request.password(), profile));
    }

    @PatchMapping("/{id}")
    UserResponse changeProfile(@PathVariable Long id, @Valid @RequestBody ChangeUserProfileRequest request) {
        return UserResponse.from(
                userService.changeProfile(id, request.role(), request.approverLevel(), request.costCenterId()));
    }
}
