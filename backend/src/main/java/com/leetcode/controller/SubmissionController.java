package com.leetcode.controller;

import static com.leetcode.dto.SubmissionDtos.*;
import com.leetcode.security.UserPrincipal;
import com.leetcode.service.SubmissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;

    /**
     * POST /api/submissions
     * Submit code for judging (all test cases including hidden)
     */
    @PostMapping
    public ResponseEntity<SubmissionResultDto> submit(
            @Valid @RequestBody SubmitCodeRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        SubmissionResultDto result = submissionService.submitCode(request, currentUser.getId());
        return ResponseEntity.ok(result);
    }

    /**
     * POST /api/submissions/run
     * Run code against visible test cases only (no history saved)
     */
    @PostMapping("/run")
    public ResponseEntity<RunCodeResultDto> runCode(
            @Valid @RequestBody RunCodeRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        RunCodeResultDto result = submissionService.runCode(request, currentUser.getId());
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/submissions
     * Current user's submission history
     */
    @GetMapping
    public ResponseEntity<Page<SubmissionSummaryDto>> getMySubmissions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(
            submissionService.getUserSubmissions(currentUser.getId(), page, size)
        );
    }

    /**
     * GET /api/submissions/problem/{problemId}
     * Current user's submissions for a specific problem
     */
    @GetMapping("/problem/{problemId}")
    public ResponseEntity<Page<SubmissionSummaryDto>> getSubmissionsForProblem(
            @PathVariable Long problemId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(
            submissionService.getUserSubmissionsForProblem(currentUser.getId(), problemId, page, size)
        );
    }

    /**
     * GET /api/submissions/{id}
     * Detailed result of one submission
     */
    @GetMapping("/{id}")
    public ResponseEntity<SubmissionResultDto> getSubmission(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(
            submissionService.getSubmissionDetail(id, currentUser.getId())
        );
    }
}
