# ⚡ CodeArena — LeetCode-Style Coding Platform

A fully functional, production-ready coding platform built with:
**Spring Boot 3** · **React 18** · **PostgreSQL / Neon Cloud** · **Docker Sandbox Engine** · **JWT Auth** · **CodeMirror 6 Editor** · **Tailwind CSS**

---

## 📁 Project Structure

```text
leetcode-platform/
├── backend/                          # Spring Boot 3 application
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/leetcode/
│       ├── LeetCodePlatformApplication.java
│       ├── config/
│       │   ├── AppConfig.java           # ObjectMapper & PasswordEncoder beans
│       │   ├── SecurityConfig.java      # JWT + CORS + route security
│       │   └── GlobalExceptionHandler.java
│       ├── controller/
│       │   ├── AuthController.java      # POST /api/auth/{login,register,logout,me}
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
│       │   └── Dto classes
│       └── repository/                  # Spring Data JPA interfaces
│
├── frontend/                         # React 18 SPA
│   ├── Dockerfile
│   ├── package.json
│   ├── tailwind.config.js
│   └── src/
│       ├── App.js                       # React Router v6
│       ├── api/                         # Axios modules (auth, problems, submissions, users)
│       ├── context/AuthContext.js       # JWT auth state & context
│       ├── pages/
│       │   ├── LoginPage.js
│       │   ├── RegisterPage.js
│       │   ├── ProblemListPage.js       # Filter by diff/tag/search
│       │   ├── ProblemDetailPage.js     # CodeMirror editor + test run/submit view
│       │   ├── DashboardPage.js         # User stats & solved progress
│       │   └── SubmissionsPage.js       # Submission history
│       └── components/
│           ├── Navbar.js, LoadingSpinner.js
│           ├── DifficultyBadge.js, StatusBadge.js
│
├── docker/
│   ├── sandbox/Dockerfile            # Minimal JDK sandbox image
│   └── nginx.conf                    # Reverse proxy config (production profile)
└── docker-compose.yml                # Full-stack orchestration
```

---

## 🗄️ Database Schema & Seed Data

The platform automatically manages relational entities via PostgreSQL / Neon Cloud:
- **`users`** → ID, username, email, hashed password (BCrypt), role (`USER` / `ADMIN`), streak, last active
- **`problems`** → ID, title, slug, description, difficulty (`EASY`, `MEDIUM`, `HARD`), constraints, starter code
- **`tags`** & **`problem_tags`** → M:N junction for problem categories (Arrays, Dynamic Programming, Strings, Trees, etc.)
- **`problem_examples`** → Example inputs, outputs, and explanations
- **`test_cases`** → Visible & hidden test cases for judging
- **`submissions`** → User submissions, code, language, status (`ACCEPTED`, `WRONG_ANSWER`, `TIME_LIMIT_EXCEEDED`, etc.), runtime, memory, test results JSON

---

## 🔌 REST API Reference

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| **POST** | `/api/auth/register` | Public | Register new user & return JWT token |
| **POST** | `/api/auth/login` | Public | Authenticate user & return JWT token |
| **POST** | `/api/auth/logout` | User | Invalidate session client-side |
| **GET** | `/api/auth/me` | User | Get current logged-in user profile |
| **GET** | `/api/problems` | Public | Paged problem list with filters (difficulty, tag, search) |
| **GET** | `/api/problems/stats` | Public | Problem statistics by difficulty |
| **GET** | `/api/problems/{slug}` | Public | Problem details with examples and visible test cases |
| **POST** | `/api/problems` | Admin | Create a new coding problem with test cases |
| **GET** | `/api/problems/tags/all` | Public | List all problem tags |
| **POST** | `/api/submissions` | User | Submit code (evaluated against all test cases, saved to history) |
| **POST** | `/api/submissions/run` | User | Run code (evaluated against visible test cases only, unsaved) |
| **GET** | `/api/submissions` | User | Get user submission history |
| **GET** | `/api/submissions/{id}` | User | Get detailed submission report |
| **GET** | `/api/users/profile` | User | User dashboard metrics and solved count |
| **PUT** | `/api/users/profile` | User | Update user bio, avatar, or GitHub URL |

---

## 💻 Local Development Setup

### 1. Prerequisites
- **Java 17+ (JDK)**
- **Maven 3.9+**
- **Node.js 20+** and `npm`
- **Docker Desktop** *(for code sandbox execution)*

---

### 2. Database Setup (Neon PostgreSQL or Local Docker)

#### Option A: Neon Cloud PostgreSQL (Free & Recommended)
1. Sign up for free at [Neon.tech](https://neon.tech).
2. Create a project and run `backend/src/main/resources/schema.sql` in the **Neon SQL Editor**.
3. In `backend/src/main/resources/application.properties`, configure your connection string:
   ```properties
   spring.datasource.url=jdbc:postgresql://<neon-host>/<dbname>?sslmode=require
   spring.datasource.username=<neon-username>
   spring.datasource.password=<neon-password>
   ```

#### Option B: Local PostgreSQL Container
```bash
docker run -d --name leetcode-postgres \
  -p 5432:5432 \
  -e POSTGRES_DB=leetcodedb \
  -e POSTGRES_USER=leetcode \
  -e POSTGRES_PASSWORD=leetcode123 \
  -v $(pwd)/backend/src/main/resources/schema.sql:/docker-entrypoint-initdb.d/01-schema.sql \
  postgres:16-alpine
```

---

### 3. Build Code Sandbox Image
```bash
docker build -t leetcode-sandbox:latest ./docker/sandbox/
```

---

### 4. Start Backend & Frontend

**Terminal 1 — Backend (Spring Boot):**
```bash
cd backend
mvn spring-boot:run
```
*API runs on `http://localhost:8080`.*

**Terminal 2 — Frontend (React):**
```bash
cd frontend
npm install
npm start
```
*UI runs on `http://localhost:3000`.*

---

## ☁️ 100% Free Cloud Deployment Guide

You can deploy the complete platform online at **$0 / month** using **Neon + Render + Vercel**:

```text
┌──────────────────────────┐         ┌──────────────────────────┐         ┌──────────────────────────┐
│         Vercel           │ ──────> │        Render.com        │ ──────> │        Neon.tech         │
│  (React Frontend - FREE) │         │  (Spring Boot - FREE)    │         │ (PostgreSQL DB - FREE)   │
└──────────────────────────┘         └──────────────────────────┘         └──────────────────────────┘
```

### Step 1: Database on Neon.tech (Free)
1. Create a free project on [Neon.tech](https://neon.tech).
2. Run `backend/src/main/resources/schema.sql` in the Neon SQL Editor.
3. Note your JDBC connection string, username, and password.

---

### Step 2: Backend on Render.com (Free)
1. Push your repository to GitHub.
2. Sign up on [Render.com](https://render.com) → Click **New +** → **Web Service**.
3. Connect your repository (`CodeArena`).
4. Configure:
   - **Name**: `codearena-backend`
   - **Root Directory**: `backend`
   - **Runtime**: `Docker`
   - **Instance Type**: `Free`
5. Add **Environment Variables**:
   - `SPRING_DATASOURCE_URL` = `jdbc:postgresql://<your-neon-host>/<db>?sslmode=require`
   - `DB_USERNAME` = `<neon-username>`
   - `DB_PASSWORD` = `<neon-password>`
   - `JWT_SECRET` = `<random-64-char-secret-key>`
   - `JWT_EXPIRATION` = `86400000`
   - `CORS_ORIGINS` = `*` *(update to your Vercel URL after Step 3)*
6. Click **Create Web Service** and copy your live backend URL (e.g., `https://codearena-backend.onrender.com`).

---

### Step 3: Frontend on Vercel (Free)
1. Sign in to [Vercel.com](https://vercel.com) with GitHub.
2. Click **Add New...** → **Project** → Import `CodeArena`.
3. Configure settings:
   - **Root Directory**: `frontend`
   - **Framework Preset**: `Create React App`
4. Add **Environment Variable**:
   - `REACT_APP_API_URL` = `https://codearena-backend.onrender.com` *(Render backend URL)*
5. Click **Deploy**. Vercel will provide your live HTTPS domain (e.g., `https://codearena.vercel.app`).
6. Update `CORS_ORIGINS` on Render with your Vercel URL to secure API requests.

---

## 🐳 Self-Hosted VPS / Docker Deployment

For self-hosting on any Linux VPS (Ubuntu 22.04/24.04 on AWS EC2, DigitalOcean, Oracle Cloud Always Free, or Hetzner):

```bash
# 1. Clone repository
git clone https://github.com/moeed111/CodeArena.git
cd CodeArena

# 2. Configure environment
cp .env.example .env
nano .env

# 3. Build the sandbox image
docker build -t leetcode-sandbox:latest ./docker/sandbox/

# 4. Start all services with Nginx Reverse Proxy
docker compose --profile production up --build -d
```

---

## 🔒 Security Architecture

- **Stateless JWT Authentication**: Tokens signed with HMAC-SHA256 (24-hour expiration).
- **Docker Sandbox Isolation**:
  ```text
  --network none                     # Complete network block
  --memory 256m --memory-swap 256m   # Strict RAM cap
  --cpu-quota 50000                  # 50% CPU single-core quota
  --security-opt no-new-privileges   # Blocks privilege escalation
  --cap-drop ALL                     # Drops all Linux kernel capabilities
  --read-only                        # Read-only filesystem
  --tmpfs /tmp:size=64m,noexec       # Temporary non-executable storage
  -v /code:ro                        # Source mounted read-only
  --rm                               # Container deleted after execution
  ```
- **Execution Watchdog**: 5-second wall-clock JVM timeout to prevent infinite loops.

---

## ⚙️ Configuration Reference

| Variable | Default | Description |
|---|---|---|
| `spring.datasource.url` | (Neon JDBC URL) | PostgreSQL JDBC Connection String |
| `DB_USERNAME` | `neondb_owner` | PostgreSQL Username |
| `DB_PASSWORD` | (password) | PostgreSQL Password |
| `JWT_SECRET` | (dev secret) | 64+ char HMAC signing key (**change in prod**) |
| `JWT_EXPIRATION` | `86400000` (24h) | JWT Token TTL in milliseconds |
| `SANDBOX_IMAGE` | `leetcode-sandbox:latest` | Docker image for Java code judging |
| `SANDBOX_TIMEOUT` | `5` | Maximum execution timeout in seconds |
| `SANDBOX_MEMORY` | `256m` | Memory limit per execution container |
| `SANDBOX_CPU` | `50000` | CPU quota limit per container |
| `CORS_ORIGINS` | `http://localhost:3000` | Allowed CORS origins (comma-separated) |
| `REACT_APP_API_URL` | `http://localhost:8080` | Frontend backend API URL |

---

## 🧪 Testing

```bash
cd backend
mvn test
```
- **`SubmissionServiceTest`**: Tests judging logic, pass/fail status determination, runtime errors, and TLE.
- **`DockerSandboxServiceTest`**: Tests sandbox execution outcomes and test case results.
- **`SubmissionIntegrationTest`**: End-to-end MockMvc testing for `/api/submissions` and `/api/submissions/run`.
