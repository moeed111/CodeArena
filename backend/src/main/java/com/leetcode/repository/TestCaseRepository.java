package com.leetcode.repository;

import com.leetcode.model.TestCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TestCaseRepository extends JpaRepository<TestCase, Long> {
    List<TestCase> findByProblemIdOrderByOrderIndexAsc(Long problemId);
    List<TestCase> findByProblemIdAndHiddenFalseOrderByOrderIndexAsc(Long problemId);
}
