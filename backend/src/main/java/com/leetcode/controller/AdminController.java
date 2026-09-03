package com.leetcode.controller;

import com.leetcode.dto.*;
import com.leetcode.model.User;
import com.leetcode.repository.UserRepository;
import com.leetcode.security.UserPrincipal;
import com.leetcode.service.ProblemService;
import com.leetcode.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final UserService       userService;
    private final ProblemService    problemService;
    private final UserRepository    userRepository;

    // ------------------------------------------------------------------ //
    //  Dashboard                                                           //
    // ------------------------------------------------------------------ //

    /**
     * GET /api/admin/stats
     * Returns aggregate stats: totalUsers, totalProblems, totalSubmissions.
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getStats() {
        return ResponseEntity.ok(userService.getAdminStats());
    }

    // ------------------------------------------------------------------ //
    //  User management                                                     //
    // ------------------------------------------------------------------ //

    /**
     * GET /api/admin/users
     * Returns all users as admin DTOs.
     */
    @GetMapping("/users")
    public ResponseEntity<List<UserDtos.AdminUserDto>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    /**
     * PUT /api/admin/users/{id}/role
     * Change a user's role.
     */
    @PutMapping("/users/{id}/role")
    public ResponseEntity<UserDtos.AdminUserDto> updateUserRole(
            @PathVariable Long id,
            @RequestBody UserDtos.UpdateUserRoleRequest request) {
        return ResponseEntity.ok(userService.updateUserRole(id, request.role()));
    }

    /**
     * DELETE /api/admin/users/{id}
     * Delete a user. Admins cannot delete themselves.
     */
    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        userService.deleteUser(id, currentUser.getId());
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------ //
    //  Problem management                                                  //
    // ------------------------------------------------------------------ //

    /**
     * PUT /api/admin/problems/{id}
     * Partial update of an existing problem.
     */
    @PutMapping("/problems/{id}")
    public ResponseEntity<ProblemDetailDto> updateProblem(
            @PathVariable Long id,
            @RequestBody UpdateProblemRequest request) {
        return ResponseEntity.ok(problemService.updateProblem(id, request));
    }

    /**
     * DELETE /api/admin/problems/{id}
     * Delete a problem and all its test cases/submissions.
     */
    @DeleteMapping("/problems/{id}")
    public ResponseEntity<Void> deleteProblem(@PathVariable Long id) {
        problemService.deleteProblem(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * PATCH /api/admin/problems/{id}/toggle
     * Toggle active/inactive status of a problem.
     */
    @PatchMapping("/problems/{id}/toggle")
    public ResponseEntity<ProblemDetailDto> toggleProblem(@PathVariable Long id) {
        return ResponseEntity.ok(problemService.toggleActive(id));
    }
}
