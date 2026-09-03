package com.leetcode.dto;

import com.leetcode.model.Problem;

import java.util.List;
import java.util.Set;

/**
 * Admin: partial update request for an existing problem.
 * Any null field is ignored (keep existing value).
 */
public record UpdateProblemRequest(
    String title,
    String description,
    Problem.Difficulty difficulty,
    String constraints,
    String starterCode,
    String solution,
    Boolean active,
    Set<String> tags,
    List<CreateProblemRequest.CreateExampleRequest> examples,
    List<CreateProblemRequest.CreateTestCaseRequest> testCases
) {}
