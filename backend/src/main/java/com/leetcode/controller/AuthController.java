package com.leetcode.controller;

import com.leetcode.dto.*;
import com.leetcode.model.User;
import com.leetcode.security.JwtTokenProvider;
import com.leetcode.security.UserPrincipal;
import com.leetcode.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider      tokenProvider;
    private final UserService           userService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = userService.registerUser(request);

        // Auto-login after registration
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );
        String token = tokenProvider.generateToken(authentication);
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        return ResponseEntity.ok(new AuthResponse(
            token, principal.getId(), principal.getUsername(), principal.getEmail(), "USER"
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.usernameOrEmail(), request.password())
        );

        String token = tokenProvider.generateToken(authentication);
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        return ResponseEntity.ok(new AuthResponse(
            token, principal.getId(), principal.getUsername(), principal.getEmail(),
            principal.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "")
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout() {
        // JWT is stateless; client should discard the token
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @GetMapping("/me")
    public ResponseEntity<UserDtos.UserProfileDto> getCurrentUser(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(userService.getUserProfile(currentUser.getId()));
    }
}
