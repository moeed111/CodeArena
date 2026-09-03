package com.leetcode.service;

import com.leetcode.dto.*;
import com.leetcode.model.User;
import com.leetcode.repository.ProblemRepository;
import com.leetcode.repository.SubmissionRepository;
import com.leetcode.repository.UserRepository;
import com.leetcode.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final SubmissionRepository submissionRepository;
    private final ProblemRepository problemRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(usernameOrEmail)
            .or(() -> userRepository.findByEmail(usernameOrEmail))
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + usernameOrEmail));
        return UserPrincipal.create(user);
    }

    public User registerUser(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new IllegalArgumentException("Username is already taken");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email is already registered");
        }

        User user = User.builder()
            .username(request.username())
            .email(request.email())
            .password(passwordEncoder.encode(request.password()))
            .role(User.Role.USER)
            .streak(0)
            .build();

        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public UserDtos.UserProfileDto getUserProfile(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));

        UserDtos.DashboardStatsDto stats = buildStats(userId);

        return new UserDtos.UserProfileDto(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getRole().name(),
            user.getAvatarUrl(),
            user.getBio(),
            user.getGithubUrl(),
            user.getStreak(),
            user.getCreatedAt(),
            stats
        );
    }

    @Transactional(readOnly = true)
    public UserDtos.UserProfileDto getPublicProfile(String username) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("User not found"));

        UserDtos.DashboardStatsDto stats = buildStats(user.getId());

        return new UserDtos.UserProfileDto(
            user.getId(),
            user.getUsername(),
            null, // hide email for public profile
            user.getRole().name(),
            user.getAvatarUrl(),
            user.getBio(),
            user.getGithubUrl(),
            user.getStreak(),
            user.getCreatedAt(),
            stats
        );
    }

    public UserDtos.UserProfileDto updateProfile(Long userId, UserDtos.UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));

        if (request.bio() != null)       user.setBio(request.bio());
        if (request.avatarUrl() != null)  user.setAvatarUrl(request.avatarUrl());
        if (request.githubUrl() != null)  user.setGithubUrl(request.githubUrl());

        userRepository.save(user);
        return getUserProfile(userId);
    }

    public void updateStreak(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        LocalDate today = LocalDate.now();
        LocalDate lastActive = user.getLastActive();

        if (lastActive == null || lastActive.isBefore(today.minusDays(1))) {
            user.setStreak(1);
        } else if (lastActive.equals(today.minusDays(1))) {
            user.setStreak(user.getStreak() + 1);
        }
        // if lastActive == today, streak unchanged

        user.setLastActive(today);
        userRepository.save(user);
    }

    private UserDtos.DashboardStatsDto buildStats(Long userId) {
        long total      = submissionRepository.countTotalByUserId(userId);
        long accepted   = submissionRepository.countAcceptedByUserId(userId);
        long solved     = submissionRepository.countDistinctSolvedByUserId(userId);

        // Per-difficulty solved counts via native-ish approach
        long easySolved   = countSolvedByDifficulty(userId, "EASY");
        long mediumSolved = countSolvedByDifficulty(userId, "MEDIUM");
        long hardSolved   = countSolvedByDifficulty(userId, "HARD");

        double rate = total == 0 ? 0.0 : (double) accepted / total * 100;

        return new UserDtos.DashboardStatsDto(total, accepted, solved, easySolved, mediumSolved, hardSolved, rate);
    }

    private long countSolvedByDifficulty(Long userId, String difficulty) {
        // Use a cross-join query through repositories
        return submissionRepository.findAll().stream()
            .filter(s -> s.getUser().getId().equals(userId))
            .filter(s -> s.getStatus().name().equals("ACCEPTED"))
            .filter(s -> s.getProblem().getDifficulty().name().equals(difficulty))
            .map(s -> s.getProblem().getId())
            .distinct()
            .count();
    }

    // ------------------------------------------------------------------ //
    //  Admin operations                                                    //
    // ------------------------------------------------------------------ //

    @Transactional(readOnly = true)
    public List<UserDtos.AdminUserDto> getAllUsers() {
        return userRepository.findAll().stream()
            .map(u -> new UserDtos.AdminUserDto(
                u.getId(),
                u.getUsername(),
                u.getEmail(),
                u.getRole().name(),
                u.getStreak(),
                u.getCreatedAt(),
                submissionRepository.countByUserId(u.getId())
            ))
            .collect(Collectors.toList());
    }

    public UserDtos.AdminUserDto updateUserRole(Long userId, User.Role role) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));
        user.setRole(role);
        userRepository.save(user);
        return new UserDtos.AdminUserDto(
            user.getId(), user.getUsername(), user.getEmail(),
            user.getRole().name(), user.getStreak(), user.getCreatedAt(),
            submissionRepository.countByUserId(user.getId())
        );
    }

    public void deleteUser(Long userId, Long requestingAdminId) {
        if (userId.equals(requestingAdminId)) {
            throw new IllegalArgumentException("Admins cannot delete their own account");
        }
        userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));
        userRepository.deleteById(userId);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getAdminStats() {
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("totalUsers",       userRepository.count());
        stats.put("totalProblems",    problemRepository.count());
        stats.put("totalSubmissions", submissionRepository.count());
        return stats;
    }
}
