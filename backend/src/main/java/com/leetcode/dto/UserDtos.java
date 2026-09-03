package com.leetcode.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.leetcode.model.User;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public class UserDtos {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UserProfileDto(
        Long id,
        String username,
        String email,
        String role,
        String avatarUrl,
        String bio,
        String githubUrl,
        int streak,
        LocalDateTime createdAt,
        DashboardStatsDto stats
    ) {}

    public record DashboardStatsDto(
        long totalSubmissions,
        long acceptedSubmissions,
        long solvedProblems,
        long easySolved,
        long mediumSolved,
        long hardSolved,
        double acceptanceRate
    ) {}

    public record UpdateProfileRequest(
        @Size(max = 500) String bio,
        @Size(max = 500) String avatarUrl,
        @Size(max = 200) String githubUrl
    ) {}

    /** Admin: full user row returned in the admin users table */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AdminUserDto(
        Long id,
        String username,
        String email,
        String role,
        int streak,
        LocalDateTime createdAt,
        long totalSubmissions
    ) {}

    /** Admin: request body to change a user's role */
    public record UpdateUserRoleRequest(
        @NotNull User.Role role
    ) {}
}
