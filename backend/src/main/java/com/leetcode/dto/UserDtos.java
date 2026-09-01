package com.leetcode.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public class UserDtos {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UserProfileDto(
        Long id,
        String username,
        String email,
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
}
