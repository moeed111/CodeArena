package com.leetcode.repository;

import com.leetcode.model.Submission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    Page<Submission> findByUserIdOrderBySubmittedAtDesc(Long userId, Pageable pageable);

    Page<Submission> findByUserIdAndProblemIdOrderBySubmittedAtDesc(Long userId, Long problemId, Pageable pageable);

    Optional<Submission> findFirstByUserIdAndProblemIdAndStatus(Long userId, Long problemId, Submission.Status status);

    boolean existsByUserIdAndProblemIdAndStatus(Long userId, Long problemId, Submission.Status status);

    @Query("SELECT COUNT(DISTINCT s.problem.id) FROM Submission s WHERE s.user.id = :userId AND s.status = 'ACCEPTED'")
    long countDistinctSolvedByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(s) FROM Submission s WHERE s.user.id = :userId")
    long countTotalByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(s) FROM Submission s WHERE s.user.id = :userId")
    long countByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(s) FROM Submission s WHERE s.user.id = :userId AND s.status = 'ACCEPTED'")
    long countAcceptedByUserId(@Param("userId") Long userId);

    @Query(value = """
        SELECT s.problem_id, COUNT(*) as cnt
        FROM submissions s
        WHERE s.user_id = :userId AND s.status = 'ACCEPTED'
        GROUP BY s.problem_id
        ORDER BY cnt DESC
        LIMIT 5
    """, nativeQuery = true)
    List<Object[]> findMostAttemptedProblems(@Param("userId") Long userId);
}
