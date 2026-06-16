# Developer Setup & Configuration

## 1. Prerequisites

| Tool | Version | Required | Purpose |
|---|---|---|---|
| **Java JDK** | 21 LTS | Yes | Build and runtime |
| **Gradle** | 8.x | Yes | Build tool (via wrapper) |
| **Docker** | 24+ | Yes | Run ClickHouse locally |
| **Docker Compose** | 2.x | Yes | Orchestrate infrastructure |
| **Git** | 2.x | Yes | Version control |

> **No Kafka, no PostgreSQL, no JPA.** The Insights Service connects to ClickHouse only and uses jOOQ for type-safe SQL. There is no ORM, no schema migration tool, and no Kafka integration.

---

## 2. Quick Start

### 2.1 Clone & Build

```bash
git clone <repository-url>
cd cce-insights-service

# Build executable JAR (skips JOOQ generation — pre-generated sources are committed)
./gradlew bootJar -x generateJooq

# Run unit tests
./gradlew test
```

### 2.2 Start Infrastructure

ClickHouse is deployed via the **deploy-scripts** repository. The `cce-clickhouse` container is part of the shared `deploy-scripts_cce-net` Docker network.

```bash
# In the deploy-scripts repo
cd /path/to/deploy-scripts
docker compose up -d cce-clickhouse

# Verify
curl http://localhost:8123/ping   # should respond: Ok.
```

### 2.3 Run the Application

```bash
# Run with defaults (connects to localhost:8123)
./gradlew bootRun

# Or run the JAR directly
java -jar build/libs/cce-insights-service-1.0.0-SNAPSHOT.jar

# Verify health
curl http://localhost:8084/actuator/health
```

---

## 3. Configuration Reference

### 3.1 Application Properties

```yaml
server:
  port: ${SERVER_PORT:8084}

spring:
  application:
    name: cce-insights-service
  jooq:
    sql-dialect: DEFAULT
  datasource:
    url: jdbc:clickhouse://${DB_HOST:localhost}:${DB_PORT:8123}/${DB_NAME:cce_analytics}
    username: ${DB_USERNAME:cce_pipeline}
    password: ${DB_PASSWORD:cce_analytics_dev}
    driver-class-name: com.clickhouse.jdbc.ClickHouseDriver
    hikari:
      maximum-pool-size: ${DB_POOL_SIZE:10}

cce:
  clickhouse:
    use-final: ${CLICKHOUSE_USE_FINAL:false}   # append FINAL to ReplacingMergeTree queries
  cache:
    ttl:
      lookups: ${CACHE_TTL_LOOKUPS:60}
      analytics: ${CACHE_TTL_ANALYTICS:30}
      metrics: ${CACHE_TTL_METRICS:15}
```

### 3.2 Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | `8084` | HTTP port |
| `DB_HOST` | `localhost` | ClickHouse host |
| `DB_PORT` | `8123` | ClickHouse HTTP port |
| `DB_NAME` | `cce_analytics` | ClickHouse database |
| `DB_USERNAME` | `cce_pipeline` | ClickHouse username |
| `DB_PASSWORD` | `cce_analytics_dev` | ClickHouse password |
| `DB_POOL_SIZE` | `10` | HikariCP max pool size |
| `CLICKHOUSE_USE_FINAL` | `false` | Append FINAL clause to ReplacingMergeTree queries |
| `CACHE_TTL_LOOKUPS` | `60` | Lookup cache TTL (minutes) |
| `CACHE_TTL_ANALYTICS` | `30` | Analytics cache TTL (minutes) |
| `CACHE_TTL_METRICS` | `15` | Metrics cache TTL (minutes) |

---

## 4. Project Structure

```
cce-insights-service/
├── build.gradle
├── settings.gradle
├── gradlew / gradlew.bat
├── gradle/wrapper/
├── Dockerfile
├── docker-compose.yml
├── docs/
│   ├── architecture-overview.md
│   ├── api-reference.md
│   ├── data-dictionary.md
│   ├── developer-setup.md        ← this file
│   ├── deployment-guide.md
│   └── flow-diagrams.md
└── src/
    ├── generated/
    │   └── jooq/                  # Auto-generated jOOQ table/field classes (committed)
    │       └── org/openphc/cce/insights/jooq/
    │           ├── Tables.java    # Static imports: Tables.STEP_INSTANCES etc.
    │           ├── Keys.java
    │           ├── CceAnalytics.java
    │           └── tables/        # One class per table/view
    ├── main/
    │   ├── java/org/openphc/cce/insights/
    │   │   ├── InsightsServiceApplication.java
    │   │   ├── config/
    │   │   │   ├── CacheConfig.java
    │   │   │   ├── JooqConfig.java         # DSLContext, render settings for ClickHouse
    │   │   │   ├── MetricsConfig.java
    │   │   │   └── ObservabilityConfig.java
    │   │   ├── domain/repository/          # jOOQ-based repositories
    │   │   ├── health/
    │   │   │   └── DatabaseHealthIndicator.java
    │   │   ├── service/
    │   │   └── web/
    │   └── resources/
    │       ├── application.yml
    │       └── logback-spring.xml
    ├── test/                      # @WebMvcTest unit tests (no ClickHouse needed)
    └── integrationTest/           # Integration tests
```

---

## 5. jOOQ Code Generation

jOOQ generates type-safe Java classes from the live ClickHouse schema. The generated sources are **committed** to `src/generated/jooq/` so the Docker build does not need a database connection.

Re-run `generateJooq` only when the ClickHouse schema changes (new tables, column renames, etc.).

### 5.1 Generate from Local ClickHouse

```bash
# Requires cce-clickhouse running on localhost:8123
./gradlew generateJooq
```

### 5.2 Generate from Remote ClickHouse (dev/uat/prod)

ClickHouse is not publicly exposed — access requires an SSH tunnel.

```bash
# 1. Open SSH tunnel (forward remote port 8123 to local 18123)
ssh -i ~/.ssh/id_ed25519 -L 18123:localhost:8123 ubuntu@<server-ip> -N -f

# 2. Generate against the remote schema
./gradlew generateJooq -PjooqUrl=jdbc:clickhouse://localhost:18123/cce_analytics

# 3. Close the tunnel
pkill -f "ssh.*18123"
```

> The remote ClickHouse credentials (`cce_pipeline` / `cce_analytics_dev`) are the same as local defaults, so no extra flags are needed unless credentials differ.

### 5.3 JooqConfig Settings

`JooqConfig` configures the DSLContext for ClickHouse compatibility:

| Setting | Value | Reason |
|---|---|---|
| `withRenderSchema(false)` | false | ClickHouse has no schema prefix |
| `withRenderQuotedNames` | `NEVER` | ClickHouse doesn't use quoted identifiers |
| `withRenderNameCase` | `LOWER` | Normalize to lowercase column names |

---

## 6. Testing

### 6.1 Commands

```bash
# Unit tests (no ClickHouse required — all mocked)
./gradlew test

# Integration tests
./gradlew integrationTest

# All tests
./gradlew test integrationTest

# Coverage report
./gradlew test jacocoTestReport
```

### 6.2 Test Strategy

- **Unit tests:** `@WebMvcTest` with mocked repositories — test controllers, request validation, response serialization. No ClickHouse needed.
- **Integration tests:** Test end-to-end query execution against a real database.

> Tests are skipped in the Docker build (`bootJar -x generateJooq`) since they require a ClickHouse connection for integration tests. Unit tests pass without any infrastructure.

---

## 7. Docker Build

### 7.1 Docker Compose (Recommended)

```bash
# Requires deploy-scripts_cce-net network to exist
docker compose up --build -d

# Verify (service runs on port 8088 in Docker)
curl http://localhost:8088/actuator/health
```

> The Docker Compose file maps `SERVER_PORT=8088` and exposes port `8088`. The bare `bootRun`/JAR uses `8084` (default).

### 7.2 Standalone Docker Build

```bash
docker build -t cce-insights-service .

docker run -p 8088:8088 \
  -e SERVER_PORT=8088 \
  -e DB_HOST=host.docker.internal \
  -e DB_PORT=8123 \
  -e DB_NAME=cce_analytics \
  -e DB_USERNAME=cce_pipeline \
  -e DB_PASSWORD=cce_analytics_dev \
  cce-insights-service
```

### 7.3 How the Dockerfile Works

The Dockerfile uses a **two-stage build**:

1. **Build stage** (`eclipse-temurin:21-jdk-alpine`) — caches Gradle dependencies, then runs `bootJar -x generateJooq` (skips JOOQ generation since no ClickHouse is available at build time; the pre-generated sources in `src/generated/jooq/` are copied from the repo).
2. **Runtime stage** (`eclipse-temurin:21-jre-alpine`) — copies only the fat JAR, runs as a non-root user.
