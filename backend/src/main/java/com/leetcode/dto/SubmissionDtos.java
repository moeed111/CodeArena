package com.leetcode.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public class SubmissionDtos {

    public record SubmitCodeRequest(
        @NotBlank String code,
        @NotBlank String language,
        @NotNull Long problemId
    ) {}

    public record RunCodeRequest(
        @NotBlank String code,
        @NotBlank String language,
        @NotNull Long problemId,
        String customInput
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SubmissionResultDto(
        Long id,
        String status,
        Integer runtimeMs,
        Integer memoryKb,
        String errorMessage,
        int passedTests,
        int totalTests,
        List<TestResultDto> testResults,
        LocalDateTime submittedAt
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SubmissionSummaryDto(
        Long id,
        Long problemId,
        String problemTitle,
        String problemSlug,
        String status,
        String language,
        Integer runtimeMs,
        int passedTests,
        int totalTests,
        LocalDateTime submittedAt
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TestResultDto(
        int index,
        String input,
        String expected,
        String actual,
        boolean passed,
        String status,
        long runtimeMs,
        String errorMessage,
        boolean hidden
    ) {}

    public record RunCodeResultDto(
        String status,
        List<TestResultDto> results,
        String stdout,
        String stderr,
        String errorMessage,
        long runtimeMs
    ) {}
}
