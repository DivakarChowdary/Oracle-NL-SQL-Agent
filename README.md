# 🔍 Oracle NL-to-SQL Agent

A Spring Boot AI agent that converts natural-language questions into optimized Oracle SQL — reading live schema metadata (indexes, constraints, PK/FK) from the Oracle data dictionary to generate performant queries — and returns results through a real-time chat UI.

## What It Does
User asks a question  →  Fetch schema metadata  →  Claude generates SQL  →  Execute  →  Return results
     (plain English)        (indexes, PK, FK)       (optimized for schema)    (Oracle)    (chat + table)

Example:
> "How many new patients were added to GLOBAL_PATIENT this week?"

Claude reads the actual indexes, constraints, and column types from Oracle's data dictionary, then generates:
sql
SELECT COUNT(*) AS new_patients
FROM GLOBAL_PATIENT
WHERE TRUNC(CREATED_DATE) >= TRUNC(SYSDATE) - 7

## Architecture

┌──────────────────────────────────────────────────────────┐
│                    Chat UI (Browser)                      │
│          Split pane: Conversation + SQL + Results         │
├──────────────────────────────────────────────────────────┤
│              NlSqlController (REST API)                   │
│          POST /api/query  GET /api/tables                 │
├──────────┬───────────────────────────────────────────────┤
│          │         NlSqlAgentService (Orchestrator)       │
│          ├─────────────────┬─────────────────────────────┤
│ Schema   │  Claude AI      │   Query Executor             │
│ Metadata │  SQL Generator  │   (JDBC + Safety Gate)       │
│ Service  │  (Anthropic SDK)│   Max 500 rows               │
│          │                 │   SELECT only                │
└──────────┴─────────────────┴─────────────────────────────┘
              Oracle Database (via HikariCP pool)

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Framework | Spring Boot 3.3, Java 17 |
| AI | Anthropic Claude (claude-sonnet-4-6) |
| Database | Oracle JDBC (ojdbc11) |
| Connection Pool | HikariCP (max 10 connections) |
| Schema Introspection | Oracle Data Dictionary (ALL_COLUMNS, ALL_CONSTRAINTS, ALL_INDEXES) |
| UI | Vanilla HTML/CSS/JS (dark terminal theme) |
| Build | Maven |
| Test | JUnit 5, Mockito |

## Setup

### Prerequisites
- Java 17+
- Maven 3.8+
- Oracle SQL Developer running locally (port 1521)
- Anthropic API Key

### 1. Install Oracle JDBC Driver

Download `ojdbc11.jar` from [Oracle's website](https://www.oracle.com/database/technologies/appdev/jdbc-downloads.html) and install it locally:

bash
mvn install:install-file \
  -Dfile=ojdbc11.jar \
  -DgroupId=com.oracle.database.jdbc \
  -DartifactId=ojdbc11 \
  -Dversion=23.4.0 \
  -Dpackaging=jar


### 2. Configure Oracle connection in `application.yml`

yaml
spring:
  datasource:
    url: jdbc:oracle:thin:@localhost:1521:XE    # or :ORCL
    username: your_username
    password: your_password


Or via environment variables:
bash
export ORACLE_USER=your_username
export ORACLE_PASSWORD=your_password
export ANTHROPIC_API_KEY=sk-ant-your_key


### 3. Build and Run

bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
mvn clean package -DskipTests
java -jar target/oracle-nl-sql-agent-1.0.0.jar


### 4. Open the Chat UI

Navigate to: http://localhost:8081

## Using the Chat UI

The interface is a split pane:
- Left — conversation with the agent
- Right — generated SQL (with syntax highlighting + copy button) and results table

Type a question in plain English and press Enter or Ask →.

Example questions:
- "How many rows are in the EMPLOYEES table?"
- "Show me the top 10 records from ORDERS ordered by created date"
- "Count records inserted today in GLOBAL_PATIENT"
- "What are the distinct values in the STATUS column?"

Optional:Select a target table from the dropdown, or leave it on "Auto-detect" and Claude will figure out the right table from your question.

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/query` | Submit a natural-language question |
| `GET` | `/api/tables` | List available tables in the schema |
| `GET` | `/api/schema/{tableName}` | Get schema metadata for a table |
| `GET` | `/api/health` | Health check |

### Example request
bash
curl -X POST http://localhost:8081/api/query \
  -H "Content-Type: application/json" \
  -d '{"question": "How many rows are in EMPLOYEES?", "tableName": "EMPLOYEES"}'


## Safety Features

- SELECT only — the executor blocks any query that isn't a SELECT or WITH statement
- Keyword blocklist — DROP, DELETE, INSERT, UPDATE, TRUNCATE, ALTER, GRANT, EXEC are all rejected
- Row limit — max 500 rows returned per query (configurable)
- Connection pool — HikariCP limits concurrent database connections (default max 10)

## How Schema Metadata Improves SQL Quality

Before generating SQL, the agent fetches:

| Oracle View | What it provides |
|-------------|-----------------|
| `ALL_TAB_COLUMNS` | Column names, data types, nullability |
| `ALL_CONSTRAINTS` | PK, FK, UNIQUE, CHECK constraints |
| `ALL_CONS_COLUMNS` | Which columns belong to each constraint |
| `ALL_INDEXES` | Index names, columns, uniqueness |
| `ALL_IND_COLUMNS` | Index column order and composition |

Claude uses this context to:
- Use indexed columns in WHERE clauses (faster queries)
- Use correct Oracle date functions (TRUNC, SYSDATE, TO_DATE)
- Follow foreign key relationships for JOINs
- Avoid full table scans where possible

## Running Tests

bash
mvn test

## Author

Divakar Chowdary Kamma
- Website: [divakarchowdary.com](https://divakarchowdary.com)
- LinkedIn: [linkedin.com/in/divakar-chowdary-kamma](https://www.linkedin.com/in/divakar-chowdary-kamma-b6543490/)
- GitHub: [github.com/DivakarChowdary](https://github.com/DivakarChowdary)

## License

MIT License
