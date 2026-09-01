package com.leetcode.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "problem_examples")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProblemExample {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String input;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String output;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "order_index", nullable = false)
    private int orderIndex = 0;
}
