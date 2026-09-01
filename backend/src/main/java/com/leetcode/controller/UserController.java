package com.leetcode.controller;

import static com.leetcode.dto.UserDtos.*;
import com.leetcode.security.UserPrincipal;
import com.leetcode.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/profile")
    public ResponseEntity<UserProfileDto> getMyProfile(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(userService.getUserProfile(currentUser.getId()));
    }

    @PutMapping("/profile")
    public ResponseEntity<UserProfileDto> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(userService.updateProfile(currentUser.getId(), request));
    }

    @GetMapping("/{username}")
    public ResponseEntity<UserProfileDto> getPublicProfile(@PathVariable String username) {
        return ResponseEntity.ok(userService.getPublicProfile(username));
    }
}
