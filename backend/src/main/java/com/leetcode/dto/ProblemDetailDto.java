package com.leetcode.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProblemDetailDto(
    Long id,
    String title,
    String slug,
    String description,
    String difficulty,
    String constraints,
    String starterCode,
    BigDecimal acceptance,
    int submissions,
    Set<String> tags,
    List<ExampleDto> examples,
    List<TestCaseDto> visibleTestCases,
    Boolean solved
) {}
