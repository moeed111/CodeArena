package com.leetcode.repository;

import com.leetcode.model.Problem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProblemRepository extends JpaRepository<Problem, Long> {

    Optional<Problem> findBySlug(String slug);

    Page<Problem> findByActiveTrue(Pageable pageable);

    @Query("""
        SELECT DISTINCT p FROM Problem p
        LEFT JOIN p.tags t
        WHERE p.active = true
        AND (:difficulty IS NULL OR p.difficulty = :difficulty)
        AND (:tagName IS NULL OR t.name = :tagName)
        AND (:search IS NULL OR LOWER(p.title) LIKE CONCAT('%', LOWER(CAST(:search AS string)), '%'))
        ORDER BY p.id ASC
    """)
    Page<Problem> findWithFilters(
        @Param("difficulty") Problem.Difficulty difficulty,
        @Param("tagName") String tagName,
        @Param("search") String search,
        Pageable pageable
    );

    @Query("SELECT COUNT(p) FROM Problem p WHERE p.active = true AND p.difficulty = :difficulty")
    long countByDifficulty(@Param("difficulty") Problem.Difficulty difficulty);
}
