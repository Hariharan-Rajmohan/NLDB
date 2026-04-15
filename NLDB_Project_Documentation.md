# NLDB — Natural Language Database Interface

## Full Project Documentation

---

## Chapter 1: Introduction

### 1.1 Project Overview
NLDB (Natural Language Database Interface) is a full-stack web application that allows users to query a relational database using everyday English. The system leverages **Google Gemini AI** to convert natural language queries into SQL, executes them securely on an H2 in-memory database, and displays results in a premium dark-themed web interface.

### 1.2 Key Features
| Feature | Description |
|---------|-------------|
| **Natural Language Queries** | Type questions in plain English (e.g., "Show all students from Computer Science") |
| **AI-Powered SQL Generation** | Google Gemini converts natural language to valid SQL |
| **CRUD Operations** | Full Create, Read, Update, Delete support for all tables |
| **Voice Input (Speech-to-Text)** | Speak your queries using the browser microphone |
| **Voice Output (Text-to-Speech)** | Have query results read aloud |
| **Sign-In Page** | Email/Password, Google, GitHub, and Guest login options |
| **Persistent Sessions** | Login state saved to localStorage across browser sessions |
| **Chat Dataset Creator** | Conversational UI to create tables, add sample data, and modify schemas |
| **Security** | SQL injection prevention, pattern blocking (SHUTDOWN, GRANT, etc.), dynamic metadata validation |
| **Cloud Ready** | Optimized for Docker and public deployment on platforms like Render.com |

### 1.3 Technology Stack
| Layer | Technology |
|-------|-----------|
| Backend Framework | Spring Boot 3.4.4 |
| Language | Java 17+ |
| Database | H2 (In-Memory, MySQL compatibility mode) |
| Build Tool | Gradle 8.12 |
| AI Service | Google Gemini API (gemini-2.5-flash-lite, gemini-2.5-flash, gemma-3-4b-it) |
| Frontend | HTML5, CSS3, Vanilla JavaScript |
| Voice APIs | Web Speech API (SpeechRecognition + SpeechSynthesis) |
| Environment | dotenv-java 3.1.0 |

---

## Chapter 2: Project Structure

4Project/
├── build.gradle                          # Gradle build configuration
├── Dockerfile                            # Multi-stage Docker deployment config
├── system.properties                     # Java version for cloud environments
├── .gitignore                            # Updated to exclude secrets but keep build wrappers
├── gradlew.bat / gradlew                 # Gradle wrapper scripts
├── .env                                  # Environment variables (GEMINI_API_KEY)
├── src/
│   └── main/
│       ├── java/com/nldb/
│       │   ├── NldbApplication.java      # Spring Boot entry point (Env-aware)
│       │   ├── GeminiService.java        # AI service for SQL & Chat generation
│       │   ├── QueryController.java      # REST controller for NL queries
│       │   ├── ChatController.java       # REST controller for Dataset Chat
│       │   ├── ChatRequest/Response.java # DTOs for Chat feature
│       │   ├── QueryRequest.java         # Request DTO
│       │   ├── QueryResponse.java        # Response DTO
│       │   └── CrudController.java       # REST controller for Dynamic CRUD ops
│       └── resources/
│           ├── application.properties    # Spring Boot config (Port-aware)
│           ├── schema.sql                # Database table definitions
│           ├── data.sql                  # Seed data
│           └── static/
│               ├── index.html            # Main HTML page (Search-optimized)
│               ├── style.css             # UI styles (Glassmorphism + Chat)
│               └── app.js                # Frontend logic (Managed views)
```

---

## Chapter 3: Backend Architecture

### 3.1 NldbApplication.java — Entry Point
The main Spring Boot application class. It is **Cloud-Ready**, reading the `GEMINI_API_KEY` from the `.env` file for local development or from **System Environment Variables** for production (Render/Docker).

```java
@SpringBootApplication
public class NldbApplication {
    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        String apiKey = dotenv.get("GEMINI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getenv("GEMINI_API_KEY");
        }
        if (apiKey != null && !apiKey.isBlank()) {
            System.setProperty("GEMINI_API_KEY", apiKey);
        }
        SpringApplication.run(NldbApplication.class, args);
    }
}
```

### 3.2 GeminiService.java — AI SQL Generation

This is the core service that interacts with the Google Gemini API.

**Key Responsibilities:**
1. **Schema Discovery** — Dynamically reads the database schema from `DatabaseMetaData` (tables, columns)
2. **Prompt Engineering** — Constructs separate prompts for NL Querying and Chat-based Dataset Creation
3. **Conversation Context** — (New) `generateSqlFromChat()` tracks conversation history to allow follow-up questions during table creation
4. **Model Fallback** — Tries 3 Gemini models in order: `gemini-2.5-flash-lite` → `gemini-2.5-flash` → `gemma-3-4b-it`
5. **Rate Limiting** — Enforces 1.5-second minimum interval between API calls
6. **Retry Logic** — Exponential backoff (2s, 4s, 8s) on 429 errors, up to 3 retries per model
7. **Response Formatting** — Uses `---REPLY---` separator to distinguish generated SQL from AI chat dialogue

**Prompt Template:**
```
Convert the following natural language request into a MySQL SQL query.

Database Schema:
Table students(id, name, department, enrollment_year)
Table employees(id, name, department, hire_date)

User Request: <user's question>

Rules:
- Return ONLY the SQL query, nothing else.
- Do not include any markdown formatting, code blocks, or backticks.
- Use only SELECT statements.
- The query must be valid MySQL syntax.
```

### 3.3 QueryController.java — NL Query Endpoint

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/query` | POST | Accepts `{ "userQuery": "..." }`, generates SQL via Gemini, executes it, returns results |
| `/schema` | GET | Returns the current database schema |

**Security Pipeline (for `/query`):**
1. Validate non-empty input
2. Generate SQL via Gemini
3. Verify SQL starts with `SELECT`
4. Scan for destructive keywords (`DROP`, `DELETE`, `ALTER`, `TRUNCATE`, etc.)
5. Execute query via `JdbcTemplate`
6. Return results as JSON

### 3.4 ChatController.java — Dataset Creator Chat

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/chat` | POST | Accepts `{ "message": "...", "history": [...] }`, generates SQL/Reply, executes change-making SQL |
| `/api/chat/tables` | GET | Returns all existing tables in the database |

**Conversational Logic:**
1. Gemini generates DDL/DML (CREATE, INSERT, ALTER).
2. ChatController splits and executes multi-statement SQL.
3. Automatically triggers table list refreshes on the frontend.

### 3.5 CrudController.java — Dynamic Operations

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/crud/{table}` | GET | Read all rows from a table |
| `/api/crud/{table}` | POST | Create a new row (Dynamic fields) |
| `/api/crud/{table}/{id}` | DELETE | Delete a row by ID |

**Security Measures:**
- **Dynamic Meta-Validation** — Instead of a whitelist, it queries `INFORMATION_SCHEMA` to validate table existence at runtime.
- **Identifier Sanitization** — `[^a-zA-Z0-9_]` characters stripped from names.
- **Parameterized Queries** — All user values passed via `?` placeholders.

### 3.5 DTOs (Data Transfer Objects)

**QueryRequest.java:**
```java
public class QueryRequest {
    private String userQuery;   // The natural language query from the user
}
```

**QueryResponse.java:**
```java
public class QueryResponse {
    private String sql;                           // Generated SQL
    private List<Map<String, Object>> results;    // Query results
    private int rowCount;                         // Number of rows
    private String error;                         // Error message (if any)
}
```

---

## Chapter 4: Database Schema

### 4.1 Tables

**Students Table:**
```sql
CREATE TABLE students (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    department VARCHAR(100) NOT NULL,
    enrollment_year INT NOT NULL
);
```

**Employees Table:**
```sql
CREATE TABLE employees (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    department VARCHAR(100) NOT NULL,
    hire_date DATE NOT NULL
);
```

### 4.2 Seed Data
- **12 students** across departments: Computer Science, Cyber Security, Data Science, Artificial Intelligence
- **10 employees** across departments: Engineering, Human Resources, Marketing, Finance
- Data uses realistic Indian names for demonstration

### 4.3 Database Configuration
```properties
spring.datasource.url=jdbc:h2:mem:nldb;MODE=MySQL;DB_CLOSE_DELAY=-1
spring.datasource.username=sa
spring.sql.init.mode=always
# Server port — Dynamic for Cloud, fallback to 8080 local
server.port=${PORT:8080}
```

- **H2 In-Memory** — Data resets on server restart
- **MySQL Compatibility Mode** — SQL syntax compatible with MySQL
- **H2 Console** — Accessible at `http://localhost:8080/h2-console`

---

## Chapter 5: Frontend Architecture

### 5.1 Sign-In Page (index.html)
The landing page users see first, featuring:
- **Title:** "Welcome to Talk to Your Database"
- **Email + Password** form with validation
- **Remember Me** checkbox (controls localStorage persistence)
- **Forgot Password** link
- **Sign in with Google** button (official Google branding)
- **Sign in with GitHub** button
- **Continue as Guest** button
- **Sign In / Sign Up toggle** to switch between modes

### 5.2 Main Application Views

**View 1: NL Query (Default)**
- Text input for natural language questions
- Microphone button for voice input
- "Run Query" button
- Example query chips (clickable)
- Generated SQL display with copy button
- Results table with row count
- "Read Aloud" button for text-to-speech

**View 2: Manage Data (CRUD)**
- Dynamic tab navigation based on current database tables
- Data table with all records
- Edit and Delete action buttons per row
- Dynamic form modal (auto-discovers columns for new tables)

**View 3: Create Dataset (Chat)**
- AI Chatbot interface for building databases
- Message bubbles for User and Assistant
- SQL code blocks displayed inside bubbles
- Starter prompts (Chips) for quick table creation
- Automatic scrolling and loading indicators

### 5.3 JavaScript Logic (app.js)

The entire frontend is structured as a single IIFE (Immediately Invoked Function Expression) with these modules:

| Module | Description |
|--------|-------------|
| **Login & Persistence** | Handles all 4 sign-in methods, stores session in localStorage, auto-skips sign-in on revisit |
| **Query Handler** | Sends NL queries to `/query`, displays SQL and results |
| **Voice Input (STT)** | Uses `SpeechRecognition` API for microphone input with visual overlay |
| **Voice Output (TTS)** | Uses `SpeechSynthesis` API to read results aloud |
| **View Switching** | Toggles between "NL Query" and "Manage Data" views |
| **CRUD Operations** | Loads data, renders tables, handles add/edit/delete via `/api/crud/` endpoints |
| **Form Modal** | Dynamically builds form fields based on table schema |

### 5.4 CSS Design System (style.css)

**Theme: Ocean Teal Dark**

| Token | Value |
|-------|-------|
| `--bg-primary` | `#050d14` (deep navy) |
| `--accent-primary` | `#00c8b4` (teal) |
| `--accent-secondary` | `#0ea5e9` (cyan) |
| `--glass-bg` | `rgba(10, 25, 41, 0.65)` |
| `--success` | `#22d3ee` |
| `--error` | `#fb7185` |
| `--font-ui` | Inter |
| `--font-code` | JetBrains Mono |

**Visual Effects:**
- Floating background orbs with blur filters
- Glassmorphism panels with `backdrop-filter: blur(20px)`
- Gradient buttons and headings
- `fadeInUp` / `fadeInDown` entry animations
- Teal glow focus states on inputs
- Hover micro-animations

---

## Chapter 6: Voice Features

### 6.1 Speech-to-Text (Voice Input)
- **API:** `webkitSpeechRecognition` / `SpeechRecognition`
- **Trigger:** Microphone button in the query input row
- **Flow:** Click mic → Full-screen listening overlay → Speak query → Auto-transcription → Auto-submit
- **Visual:** Animated ripple rings + pulsing mic icon + real-time transcript display

### 6.2 Text-to-Speech (Voice Output)
- **API:** `SpeechSynthesis` (Web Speech API)
- **Trigger:** "Read Aloud" button in results header
- **Flow:** Builds a text summary of results (up to 5 rows) → Speaks via selected English voice
- **Configuration:** Rate 0.95, Pitch 1, Volume 1; prefers Google voices

---

## Chapter 7: Security

### 7.1 NL Query & Chat Security
1. **Administrative Blocklist** — Regex scans for `DROP DATABASE`, `TRUNCATE`, `GRANT`, `REVOKE`, `SHUTDOWN`, `DATABASE`, `SYSTEM`, `CALL`, `EXEC`.
2. **SELECT-Only Enforcement (NL Query)** — Standard queries must start with `SELECT`.
3. **Dynamic Table Validation** — CRUD operations validate table names against `INFORMATION_SCHEMA.TABLES`.
4. **Error Truncation** — All backend errors capped at 300 characters to prevent path/sensitive data exposure.

### 7.2 CRUD Security
1. **Table Whitelisting** — Only `STUDENTS` and `EMPLOYEES` tables are accessible
2. **Identifier Sanitization** — `[^a-zA-Z0-9_]` characters stripped from table/column names
3. **Parameterized Queries** — All data values use `?` placeholders in SQL
4. **Auto-ID Exclusion** — `id` fields are automatically removed from INSERT/UPDATE data

### 7.3 Frontend Security
- **HTML Escaping** — All user data escaped before DOM insertion via `escapeHtml()` function
- **No Inline Scripts** — All logic in external `app.js` file

---

## Chapter 8: API Reference

### 8.1 POST /query
**Request:**
```json
{
  "userQuery": "Show all students from Computer Science"
}
```

**Success Response:**
```json
{
  "sql": "SELECT * FROM students WHERE department = 'Computer Science'",
  "results": [
    { "ID": 1, "NAME": "Aarav Sharma", "DEPARTMENT": "Computer Science", "ENROLLMENT_YEAR": 2022 }
  ],
  "rowCount": 1,
  "error": null
}
```

**Error Response:**
```json
{
  "sql": null,
  "results": null,
  "rowCount": 0,
  "error": "Error processing your query: ..."
}
```

### 8.2 CRUD Endpoints

| Method | Endpoint | Request Body | Response |
|--------|----------|-------------|----------|
| GET | `/api/crud/students` | — | `{ "rows": [...], "count": 12 }` |
| GET | `/api/crud/students/1` | — | `{ "ID": 1, "NAME": "...", ... }` |
| POST | `/api/crud/students` | `{ "name": "X", "department": "Y", "enrollment_year": 2025 }` | `{ "message": "Record created successfully" }` |
| PUT | `/api/crud/students/1` | `{ "name": "X" }` | `{ "message": "Record updated successfully" }` |
| DELETE | `/api/crud/students/1` | — | `{ "message": "Record deleted successfully" }` |

---

## Chapter 9: Configuration & Setup

### 9.1 Prerequisites
- Java 17 or later
- Gradle (wrapper included)
- Google Gemini API Key

### 9.2 Setup Steps

**Step 1: Clone/Download the project**

**Step 2: Create `.env` file** in the project root:
```
GEMINI_API_KEY=your_actual_gemini_api_key_here
```

**Step 3: Run the application:**
```bash
.\gradlew.bat bootRun       # Windows
./gradlew bootRun            # Linux/macOS
```

**Step 4: Open browser:**
```
http://localhost:8080
```

### 9.3 Getting a Gemini API Key
1. Visit [Google AI Studio](https://aistudio.google.com/app/apikey)
2. Click "Create API Key"
3. Copy the key and paste it into the `.env` file

---

## Chapter 10: Dependencies

| Dependency | Version | Purpose |
|-----------|---------|---------|
| `spring-boot-starter-web` | 3.4.4 | REST controllers, embedded Tomcat |
| `spring-boot-starter-jdbc` | 3.4.4 | JdbcTemplate for database access |
| `h2` | (managed) | In-memory database engine |
| `dotenv-java` | 3.1.0 | Load `.env` file for API keys |

### External APIs (No Library Required)
| API | Usage |
|-----|-------|
| Google Gemini REST API | SQL generation (called via `java.net.http.HttpClient`) |
| Web Speech API | Voice input/output (browser-native) |

---

## Chapter 11: Deployment (Render.com)

### 11.1 Docker Configuration
The application is fully containerized for easy deployment.

**Dockerfile:**
```dockerfile
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
RUN apt-get update && apt-get install -y dos2unix
COPY . .
RUN dos2unix gradlew && chmod +x gradlew
RUN ./gradlew bootJar --no-daemon
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 11.2 Deployment Steps
1. Push code to **GitHub**.
2. Connect repository to **Render.com** (Web Service).
3. Set **Runtime** to "Docker".
4. Add Environment Variable: `GEMINI_API_KEY`.
5. Deploy. The app adapts to the cloud-assigned port automatically.

---

## Chapter 12: Application Flow

### 12.1 Natural Language Query Flow
```
User types "Show all students" in English
        ↓
Frontend sends POST /query { "userQuery": "Show all students" }
        ↓
QueryController receives request
        ↓
GeminiService.generateSql()
  → Fetches DB schema dynamically
  → Constructs prompt with schema + query + rules
  → Calls Gemini API (with model fallback & retry)
  → Returns: "SELECT * FROM students"
        ↓
QueryController validates SQL (SELECT only, no destructive keywords)
        ↓
JdbcTemplate executes: SELECT * FROM students
        ↓
Results returned as JSON array
        ↓
Frontend renders results in styled table
```

### 12.2 CRUD Flow
```
User clicks "Manage Data" tab
        ↓
Frontend sends GET /api/crud/students
        ↓
CrudController validates table name (whitelist)
        ↓
JdbcTemplate executes: SELECT * FROM STUDENTS
        ↓
Results rendered in table with Edit/Delete buttons
        ↓
User clicks "Add Record" → Modal form → POST /api/crud/students
User clicks Edit → Fetch record → Modal → PUT /api/crud/students/1
User clicks Delete → Confirm → DELETE /api/crud/students/1
```

### 12.3 Authentication Flow
```
User opens http://localhost:8080
        ↓
Check localStorage for 'nldb_session'
  → If exists: Skip sign-in, show app directly
  → If not: Show sign-in page
        ↓
User chooses login method:
  1. Email + Password (form submit)
  2. Google (social button)
  3. GitHub (social button)
  4. Guest (quick access)
        ↓
Session saved to localStorage (if "Remember me" checked)
        ↓
Welcome page fades out → App container fades in
```

---

## Chapter 13: Sample Queries

| Natural Language Query | Generated SQL |
|----------------------|---------------|
| Show all students | `SELECT * FROM students` |
| Employees after 2020 | `SELECT * FROM employees WHERE hire_date > '2020-01-01'` |
| Cyber Security students | `SELECT * FROM students WHERE department = 'Cyber Security'` |
| Count by department | `SELECT department, COUNT(*) FROM students GROUP BY department` |
| Engineering team | `SELECT * FROM employees WHERE department = 'Engineering'` |

---

## Chapter 14: Troubleshooting

| Issue | Solution |
|-------|----------|
| "GEMINI_API_KEY is not configured" | Create `.env` file with `GEMINI_API_KEY=your_key` |
| 429 Rate Limited errors | Wait 30-60 seconds; the system auto-retries with fallback models |
| Port 8080 already in use | Kill the existing process or change `server.port` in `application.properties` |
| Voice input not working | Use Chrome/Edge (Firefox has limited Speech API support) |
| H2 Console access | Navigate to `http://localhost:8080/h2-console`, JDBC URL: `jdbc:h2:mem:nldb` |

---

## Chapter 15: Future Enhancements

1. **Real OAuth Integration** — Connect Google/GitHub OAuth 2.0 for actual authentication
2. **Persistent Database** — Switch from H2 in-memory to MySQL/PostgreSQL for data persistence
3. **User Management** — Role-based access control (admin, viewer)
4. **Query History** — Save and replay previous queries
5. **Export Results** — CSV/Excel export for query results
6. **Multi-Table Joins** — Support for complex cross-table queries
7. **Dark/Light Theme Toggle** — User preference for UI theme

---

*NLDB v1.0.0 — Built with Spring Boot, Google Gemini AI, and ☕*
