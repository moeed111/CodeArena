package com.leetcode.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.leetcode.model.Problem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProblemSummaryDto(
    Long id,
    String title,
    String slug,
    String difficulty,
    BigDecimal acceptance,
    int submissions,
    Set<String> tags,
    Boolean solved
) {}
