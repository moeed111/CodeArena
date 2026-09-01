package com.leetcode.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetcode.dto.SubmissionDtos;
import com.leetcode.dto.SubmissionDtos.RunCodeRequest;
import com.leetcode.dto.SubmissionDtos.SubmitCodeRequest;
import com.leetcode.model.*;
import com.leetcode.repository.*;
import com.leetcode.sandbox.DockerSandboxService;
import com.leetcode.sandbox.DockerSandboxService.*;
import com.leetcode.security.JwtTokenProvider;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SubmissionIntegrationTest {

    @Autowired private MockMvc       mvc;
    @Autowired private ObjectMapper  mapper;
    @Autowired private UserRepository   userRepo;
    @Autowired private ProblemRepository problemRepo;
    @Autowired private TestCaseRepository testCaseRepo;
    @Autowired private JwtTokenProvider tokenProvider;
    @Autowired private AuthenticationManager authManager;

    @MockBean  private DockerSandboxService dockerSandbox;

    private String jwtToken;
    private Long   problemId;

    @BeforeEach
    void setup() {
        // Create test user if needed
        if (!userRepo.existsByUsername("testuser")) {
            User u = User.builder()
                .username("testuser").email("test@test.com")
                .password("$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Jb1a") // "admin123"
                .role(User.Role.USER).streak(0).build();
            userRepo.save(u);
        }

        // Create test problem if needed
        Problem p = problemRepo.findBySlug("test-problem").orElseGet(() -> {
            Problem np = Problem.builder()
                .title("Test Problem").slug("test-problem")
                .description("desc").difficulty(Problem.Difficulty.EASY)
                .acceptance(BigDecimal.ZERO).submissions(0).active(true).build();
            np = problemRepo.save(np);

            TestCase tc = new TestCase();
            tc.setProblem(np); tc.setInput("{\"n\":2}");
            tc.setExpected("2"); tc.setHidden(false); tc.setOrderIndex(0);
            testCaseRepo.save(tc);
            return np;
        });
        problemId = p.getId();

        // Get JWT via authentication manager
        Authentication auth = authManager.authenticate(
            new UsernamePasswordAuthenticationToken("testuser", "admin123")
        );
        jwtToken = tokenProvider.generateToken(auth);
    }

    @Test
    @Order(1)
    @DisplayName("POST /api/submissions/run returns 200 with test results")
    void runCode_returns200() throws Exception {
        when(dockerSandbox.runTestCases(anyString(), anyList(), anyString())).thenReturn(List.of(
            new TestCaseResult(0, "{\"n\":2}", "2", "2", true, "SUCCESS", 40L, null, false)
        ));

        RunCodeRequest request = new RunCodeRequest("class Solution{}", "java", problemId, null);

        mvc.perform(post("/api/submissions/run")
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.results[0].passed").value(true));
    }

    @Test
    @Order(2)
    @DisplayName("POST /api/submissions returns 200 with ACCEPTED status")
    void submitCode_accepted() throws Exception {
        when(dockerSandbox.runTestCases(anyString(), anyList(), anyString())).thenReturn(List.of(
            new TestCaseResult(0, "{\"n\":2}", "2", "2", true, "SUCCESS", 45L, null, false)
        ));

        SubmitCodeRequest request = new SubmitCodeRequest("class Solution{}", "java", problemId);

        mvc.perform(post("/api/submissions")
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACCEPTED"))
            .andExpect(jsonPath("$.passedTests").value(1))
            .andExpect(jsonPath("$.totalTests").value(1));
    }

    @Test
    @Order(3)
    @DisplayName("POST /api/submissions returns 200 with WRONG_ANSWER status")
    void submitCode_wrongAnswer() throws Exception {
        when(dockerSandbox.runTestCases(anyString(), anyList(), anyString())).thenReturn(List.of(
            new TestCaseResult(0, "{\"n\":2}", "2", "999", false, "WRONG_ANSWER", 30L, null, false)
        ));

        SubmitCodeRequest request = new SubmitCodeRequest("class Solution{ public int climbStairs(int n){ return 999; } }", "java", problemId);

        mvc.perform(post("/api/submissions")
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("WRONG_ANSWER"))
            .andExpect(jsonPath("$.passedTests").value(0));
    }

    @Test
    @Order(4)
    @DisplayName("POST /api/submissions requires authentication")
    void submitCode_unauthenticated_returns401() throws Exception {
        SubmitCodeRequest request = new SubmitCodeRequest("class Solution{}", "java", problemId);

        mvc.perform(post("/api/submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(5)
    @DisplayName("POST /api/submissions with missing fields returns 400")
    void submitCode_invalidRequest_returns400() throws Exception {
        // Missing 'code' field
        String badJson = "{\"language\":\"java\",\"problemId\":" + problemId + "}";

        mvc.perform(post("/api/submissions")
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(badJson))
            .andExpect(status().isBadRequest());
    }
}
