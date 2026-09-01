package com.leetcode.controller;

import com.leetcode.dto.*;
import com.leetcode.model.Problem;
import com.leetcode.model.Tag;
import com.leetcode.model.User;
import com.leetcode.repository.UserRepository;
import com.leetcode.security.UserPrincipal;
import com.leetcode.service.ProblemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/problems")
@RequiredArgsConstructor
public class ProblemController {

    private final ProblemService   problemService;
    private final UserRepository   userRepository;

    /**
     * GET /api/problems
     * List problems with optional filters
     */
    @GetMapping
    public ResponseEntity<Page<ProblemSummaryDto>> getProblems(
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        Problem.Difficulty diff = difficulty != null
            ? Problem.Difficulty.valueOf(difficulty.toUpperCase()) : null;
        Long userId = currentUser != null ? currentUser.getId() : null;

        Page<ProblemSummaryDto> problems = problemService.getProblems(diff, tag, search, page, size, userId);
        return ResponseEntity.ok(problems);
    }

    /**
     * GET /api/problems/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getStats() {
        return ResponseEntity.ok(problemService.getProblemStats());
    }

    /**
     * GET /api/problems/{slug}
     * Get problem detail by slug
     */
    @GetMapping("/{slug}")
    public ResponseEntity<ProblemDetailDto> getProblem(
            @PathVariable String slug,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        Long userId = currentUser != null ? currentUser.getId() : null;
        return ResponseEntity.ok(problemService.getProblemBySlug(slug, userId));
    }

    /**
     * POST /api/problems  (Admin only)
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProblemDetailDto> createProblem(
            @Valid @RequestBody CreateProblemRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        User creator = userRepository.findById(currentUser.getId()).orElseThrow();
        ProblemDetailDto created = problemService.createProblem(request, creator);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * GET /api/tags
     */
    @GetMapping("/tags/all")
    public ResponseEntity<List<String>> getAllTags() {
        List<String> tagNames = problemService.getAllTags()
            .stream()
            .map(Tag::getName)
            .toList();
        return ResponseEntity.ok(tagNames);
    }
}
