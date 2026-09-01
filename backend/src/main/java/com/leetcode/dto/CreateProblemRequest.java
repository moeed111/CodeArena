package com.leetcode.dto;

import com.leetcode.model.Problem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Set;

public record CreateProblemRequest(
    @NotBlank String title,
    @NotBlank String description,
    @NotNull Problem.Difficulty difficulty,
    String constraints,
    String starterCode,
    String solution,
    Set<String> tags,
    List<CreateExampleRequest> examples,
    List<CreateTestCaseRequest> testCases
) {
    public record CreateExampleRequest(String input, String output, String explanation, int orderIndex) {}
    public record CreateTestCaseRequest(@NotBlank String input, @NotBlank String expected, boolean hidden, int orderIndex) {}
}
