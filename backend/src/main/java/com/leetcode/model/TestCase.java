package com.leetcode.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "test_cases")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TestCase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String input;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String expected;

    @Column(name = "is_hidden", nullable = false)
    private boolean hidden = false;

    @Column(name = "order_index", nullable = false)
    private int orderIndex = 0;
}
