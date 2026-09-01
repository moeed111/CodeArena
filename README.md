# ⚡ CodeArena — LeetCode-Style Coding Platform

A fully functional, production-grade coding platform built with:
**Spring Boot 3** · **React 18** · **PostgreSQL 16** · **Docker** · **JWT Auth** · **Monaco/CodeMirror Editor**

---

## 📁 Project Structure

```
leetcode-platform/
├── backend/                          # Spring Boot 3 application
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/leetcode/
│       ├── LeetCodePlatformApplication.java
│       ├── config/
│       │   ├── AppConfig.java           # ObjectMapper bean
│       │   ├── SecurityConfig.java      # JWT + CORS + route security
│       │   └── GlobalExceptionHandler.java
│       ├── controller/
│       │   ├── AuthController.java      # POST /api/auth/{login,register,logout}
│       │   ├── ProblemController.java   # GET|POST /api/problems
│       │   ├── SubmissionController.java # POST /api/submissions{,/run}
│       │   └── UserController.java      # GET|PUT /api/users/profile
│       ├── service/
│       │   ├── UserService.java
│       │   ├── ProblemService.java
│       │   └── SubmissionService.java   # Core judging logic
│       ├── sandbox/
│       │   └── DockerSandboxService.java # Isolated execution engine
│       ├── security/
│       │   ├── JwtTokenProvider.java
│       │   ├── JwtAuthenticationFilter.java
│       │   └── UserPrincipal.java
│       ├── model/
│       │   ├── User.java, Problem.java, Submission.java
│       │   ├── TestCase.java, Tag.java, ProblemExample.java
│       ├── dto/                         # Request/Response records
│       └── repository/                  # Spring Data JPA interfaces
│
├── frontend/                         # React 18 SPA
│   ├── Dockerfile
│   ├── package.json
│   ├── tailwind.config.js
│   └── src/
│       ├── App.js                       # Router
│       ├── api/                         # Axios modules
│       │   ├── axios.js, auth.js, problems.js
│       │   ├── submissions.js, users.js
│       ├── context/AuthContext.js       # JWT auth state
│       ├── pages/
│       │   ├── LoginPage.js
│       │   ├── RegisterPage.js
│       │   ├── ProblemListPage.js       # Filter by diff/tag/search
│       │   ├── ProblemDetailPage.js     # CodeMirror editor + split view
│       │   ├── DashboardPage.js         # Stats + progress bars
│       │   └── SubmissionsPage.js       # Submission history
│       └── components/
│           ├── Navbar.js, LoadingSpinner.js
│           ├── DifficultyBadge.js, StatusBadge.js
│
├── docker/
│   ├── sandbox/Dockerfile            # Minimal JDK sandbox image
│   └── nginx.conf                    # Reverse proxy config
└── docker-compose.yml                # Full stack orchestration
```

---

## 🗄️ Database Schema

```
users            → id, username, email, password, role, streak, last_active
problems         → id, title, slug, description, difficulty, constraints, starter_code
tags             → id, name
problem_tags     → problem_id ⟶ tag_id  (M:N junction)
problem_examples → id, problem_id, input, output, explanation
test_cases       → id, problem_id, input, expected, is_hidden
submissions      → id, user_id, problem_id, code, language, status,
                   runtime_ms, memory_kb, passed_tests, total_tests,
                   test_results (JSONB), error_message
```

---

## 🔌 REST API Reference

| Method | Endpoint                          | Auth | Description                        |
|--------|-----------------------------------|------|------------------------------------|
| POST   | /api/auth/register                | ✗    | Register + auto-login              |
| POST   | /api/auth/login                   | ✗    | Login → JWT token                  |
| POST   | /api/auth/logout                  | ✓    | Invalidate (stateless: discard)    |
| GET    | /api/auth/me                      | ✓    | Current user profile               |
| GET    | /api/problems                     | ✗    | List with filters (diff/tag/search)|
| GET    | /api/problems/stats               | ✗    | Problem count by difficulty        |
| GET    | /api/problems/{slug}              | ✗    | Problem detail                     |
| POST   | /api/problems                     | ADMIN| Create problem                     |
| GET    | /api/problems/tags/all            | ✗    | All available tags                 |
| POST   | /api/submissions                  | ✓    | Submit code (all test cases)       |
| POST   | /api/submissions/run              | ✓    | Run code (visible cases only)      |
| GET    | /api/submissions                  | ✓    | Submission history                 |
| GET    | /api/submissions/{id}             | ✓    | Submission detail                  |
| GET    | /api/submissions/problem/{id}     | ✓    | Submissions for one problem        |
| GET    | /api/users/profile                | ✓    | My profile + stats                 |
| PUT    | /api/users/profile                | ✓    | Update bio/avatar/github           |
| GET    | /api/users/{username}             | ✗    | Public profile                     |

---

## 🔒 Security Architecture

### JWT Authentication
- Tokens signed with HMAC-SHA256 using a 64+ character secret
- 24-hour expiration (configurable via `JWT_EXPIRATION` env var)
- `Authorization: Bearer <token>` header required for protected routes
- Stateless — no server-side session storage

### Docker Sandbox Security

Each code submission runs inside an isolated Docker container with:

```
--network none              No network access whatsoever
--memory 256m               Hard memory cap (OOM kill on exceed)
--memory-swap 256m          Disables swap (prevents memory escape)
--cpu-quota 50000           Max 50% of one CPU core
--cpu-period 100000
--security-opt no-new-privileges  Blocks setuid/privilege escalation
--cap-drop ALL              Drops all Linux capabilities
--read-only                 Immutable root filesystem
--tmpfs /tmp:size=64m,noexec  64MB writable temp, non-executable
-v /code:ro                 Source mounted read-only
--rm                        Container auto-deleted on exit
```

Wall-clock timeout (default 5s) enforced by `Process.waitFor(timeout, SECONDS)`.

---

## 🚀 Setup & Running

### Prerequisites
- Docker ≥ 24.0
- Docker Compose ≥ 2.20
- Java 17+ (for running tests locally)
- Node.js 20+ (for local frontend dev)

---

### 1. Clone & Configure

```bash
git clone https://github.com/your-org/leetcode-platform.git
cd leetcode-platform
```

Copy and edit environment variables (optional — defaults work for dev):
```bash
cp .env.example .env
# Edit JWT_SECRET to a random 64+ character string in production!
```

---

### 2. Build the Sandbox Image

This only needs to be done once:
```bash
docker build -t leetcode-sandbox:latest ./docker/sandbox/
```

---

### 3. Start the Full Stack

```bash
docker compose up --build -d
```

Services started:
- **PostgreSQL** → `localhost:5432`  (db: `leetcodedb`, user: `leetcode`)
- **Backend API** → `http://localhost:8080`
- **Frontend**    → `http://localhost:3000`

The database schema and seed data (10 problems, 20 tags) are applied automatically on first run.

Watch logs:
```bash
docker compose logs -f backend
docker compose logs -f frontend
```

---

### 4. Verify the System

```bash
# Health check
curl http://localhost:8080/actuator/health

# Login with seeded admin account
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"usernameOrEmail":"admin","password":"admin123"}'
# → { "token": "eyJ...", "username": "admin", ... }

# Fetch problems (no auth required)
curl http://localhost:8080/api/problems

# Open the UI
open http://localhost:3000
```

---

### 5. Test a Submission End-to-End

```bash
# 1. Login and capture token
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"usernameOrEmail":"admin","password":"admin123"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

# 2. Get problem ID
PROBLEM_ID=$(curl -s "http://localhost:8080/api/problems?search=two+sum" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['content'][0]['id'])")

# 3. Run code (visible test cases, no history saved)
curl -X POST http://localhost:8080/api/submissions/run \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"code\": \"import java.util.*;\nclass Solution {\n  public int[] twoSum(int[] nums, int target) {\n    Map<Integer,Integer> map = new HashMap<>();\n    for (int i = 0; i < nums.length; i++) {\n      int c = target - nums[i];\n      if (map.containsKey(c)) return new int[]{map.get(c), i};\n      map.put(nums[i], i);\n    }\n    return new int[]{};\n  }\n}\",
    \"language\": \"java\",
    \"problemId\": $PROBLEM_ID
  }"

# 4. Submit code (all test cases, saved to history)
curl -X POST http://localhost:8080/api/submissions \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"code\": \"import java.util.*;\nclass Solution {\n  public int[] twoSum(int[] nums, int target) {\n    Map<Integer,Integer> map = new HashMap<>();\n    for (int i = 0; i < nums.length; i++) {\n      int c = target - nums[i];\n      if (map.containsKey(c)) return new int[]{map.get(c), i};\n      map.put(nums[i], i);\n    }\n    return new int[]{};\n  }\n}\",
    \"language\": \"java\",
    \"problemId\": $PROBLEM_ID
  }"
# → { "status": "ACCEPTED", "passedTests": 4, "totalTests": 4, ... }

# 5. View submission history
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/submissions
```

---

### 6. Run Tests

```bash
# Unit + integration tests (uses H2 in-memory DB)
cd backend
mvn test

# Run specific test class
mvn test -Dtest=SubmissionServiceTest
mvn test -Dtest=DockerSandboxServiceTest
mvn test -Dtest=SubmissionIntegrationTest

# Test report
open target/surefire-reports/index.html
```

---

### 7. Local Development (without Docker)

**Backend:**
```bash
# Requires a local PostgreSQL instance
export DB_USERNAME=leetcode
export DB_PASSWORD=leetcode123
export spring.datasource.url=jdbc:postgresql://localhost:5432/leetcodedb

cd backend
mvn spring-boot:run
```

**Frontend:**
```bash
cd frontend
npm install
REACT_APP_API_URL=http://localhost:8080 npm start
# Opens http://localhost:3000
```

---

### 8. Add a New Problem (Admin API)

```bash
curl -X POST http://localhost:8080/api/problems \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "FizzBuzz",
    "description": "Given an integer n, return all numbers from 1 to n...",
    "difficulty": "EASY",
    "constraints": "1 <= n <= 10^4",
    "starterCode": "class Solution {\n  public List<String> fizzBuzz(int n) {\n    // Write your solution here\n  }\n}",
    "tags": ["Math", "String", "Simulation"],
    "examples": [
      { "input": "n = 3", "output": "[\"1\",\"2\",\"Fizz\"]", "orderIndex": 0 }
    ],
    "testCases": [
      { "input": "{\"n\": 3}",  "expected": "[1, 2, Fizz]",           "hidden": false, "orderIndex": 0 },
      { "input": "{\"n\": 5}",  "expected": "[1, 2, Fizz, 4, Buzz]",  "hidden": false, "orderIndex": 1 },
      { "input": "{\"n\": 15}", "expected": "[..., FizzBuzz]",         "hidden": true,  "orderIndex": 2 }
    ]
  }'
```

---

## 🧪 Testing Strategy

### Unit Tests (`SubmissionServiceTest`)
Tests the judging logic in isolation using Mockito mocks:
- `submitCode_allPass_returnsAccepted` — happy path
- `submitCode_wrongAnswer_returnsWrongAnswer`
- `submitCode_compileError`
- `submitCode_timeLimitExceeded`
- `submitCode_noTestCases_returnsError`
- `runCode_doesNotPersist` — run-mode never writes to DB
- `getUserSubmissions_returnsPaged`

### Unit Tests (`DockerSandboxServiceTest`)
Tests sandbox result types and `runTestCases` dispatch via a spy:
- All `ExecutionResult` factory methods
- `TestCaseResult` pass/fail logic
- `runTestCases` returns correct count per input

### Integration Tests (`SubmissionIntegrationTest`)
Full Spring Boot context + H2 in-memory DB + MockMvc:
- `POST /api/submissions/run` → 200 with results
- `POST /api/submissions` → 200 ACCEPTED
- `POST /api/submissions` → 200 WRONG_ANSWER
- `POST /api/submissions` without auth → 401
- `POST /api/submissions` with invalid body → 400

---

## ⚙️ Configuration Reference

| Env Variable      | Default                | Description                        |
|-------------------|------------------------|------------------------------------|
| `DB_USERNAME`     | `leetcode`             | PostgreSQL username                |
| `DB_PASSWORD`     | `leetcode123`          | PostgreSQL password                |
| `JWT_SECRET`      | (dev secret)           | HMAC key — **change in prod!**     |
| `JWT_EXPIRATION`  | `86400000` (24h)       | Token TTL in milliseconds          |
| `SANDBOX_IMAGE`   | `leetcode-sandbox:latest` | Docker image for code execution |
| `SANDBOX_TIMEOUT` | `5`                    | Max execution seconds              |
| `SANDBOX_MEMORY`  | `256m`                 | Container memory limit             |
| `SANDBOX_CPU`     | `50000`                | CPU quota (50% of one core)        |
| `CORS_ORIGINS`    | `http://localhost:3000` | Allowed CORS origins              |

---

## 🛡️ Production Checklist

- [ ] Set a strong random `JWT_SECRET` (64+ chars)
- [ ] Use a managed PostgreSQL instance (AWS RDS, etc.)
- [ ] Enable HTTPS via Nginx + Let's Encrypt
- [ ] Set `spring.jpa.hibernate.ddl-auto=none` and use Flyway migrations
- [ ] Configure rate limiting on the submission endpoint
- [ ] Set up log aggregation (ELK / Datadog)
- [ ] Add Redis for submission queue (async judging)
- [ ] Add support for more languages (Python, C++, JavaScript)
- [ ] Enable the `production` Docker Compose profile for Nginx

---

## 🔧 Tech Stack Summary

| Layer        | Technology                   | Version |
|--------------|------------------------------|---------|
| Backend      | Spring Boot                  | 3.2.0   |
| ORM          | Spring Data JPA / Hibernate  | 6.4     |
| Database     | PostgreSQL                   | 16      |
| Auth         | JWT (jjwt)                   | 0.12.3  |
| Frontend     | React                        | 18.2    |
| Router       | React Router                 | 6.21    |
| HTTP Client  | Axios                        | 1.6     |
| Code Editor  | CodeMirror 6 (via uiw)       | 4.21    |
| Styling      | Tailwind CSS                 | 3.4     |
| Markdown     | react-markdown + remark-gfm  | 9.0     |
| Containers   | Docker + Compose             | 24+     |
| Build        | Maven                        | 3.9     |
| Testing      | JUnit 5 + Mockito + MockMvc  | –       |
