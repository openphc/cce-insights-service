# Developer Setup & Configuration

## 1. Prerequisites

| Tool | Version | Required | Purpose |
|---|---|---|---|
| **Java JDK** | 21 LTS | Yes | Build and runtime |
| **Gradle** | 8.x | Yes | Build tool (via wrapper) |
| **Docker** | 24+ | Recommended | Run PostgreSQL locally |
| **Docker Compose** | 2.x | Recommended | Orchestrate infrastructure |
| **PostgreSQL** | 16+ | Yes | Shared database (with Compliance Service) |
| **Git** | 2.x | Yes | Version control |

> **Note:** No Kafka is required — the Insights Service does not consume from or produce to Kafka.

## 2. Quick Start

### 2.1 Clone & Build

```bash
# Clone the repository
git clone <repository-url>
cd cce-insights-service

# Build (skip tests for fast iteration)
./gradlew build -x test

# Build with tests
./gradlew build
```

### 2.2 Start Infrastructure

PostgreSQL and the shared database (`cce_collector`) are deployed by the **CCE Collector Service**. All CCE services share the same database.

```bash
# Start shared infrastructure (PostgreSQL on port 5433 + Kafka on port 9092)
cd /path/to/cce-collector-service
docker compose up -d

# Verify shared services are running
docker compose ps
```

> **Note:** The Insights Service only needs PostgreSQL (no Kafka), but using the Collector Service's Docker Compose is the standard way to start shared infrastructure.

### 2.3 Shared Database Requirement

The Insights Service connects to the **same PostgreSQL database** (`cce_collector`) as all other CCE services. The database and infrastructure are deployed by the **CCE Collector Service**. All tables must exist before the Insights Service can function. The Insights Service does **not run Flyway migrations** — it has no owned tables.

> **Event Volume Queries:** The event volume analytics feature relies on JSONB path queries against `event_log.data` (e.g., `data->>'resourceType'` for resource type grouping, `data->'participant'->0->'individual'->>'reference'` for practitioner extraction). Ensure the `event_log` table is populated with realistic event data (including FHIR resources with practitioner references) to test these endpoints. See `data-dictionary.md` §3.3 for the full list of JSONB extraction paths.

> **Ingestion Analytics:** The ingestion analytics endpoints query the `inbound_event` table (owned by the Collector Service). This table must be populated with event intake records to test the ingestion funnel, rejection analytics, source quality, and pipeline loss endpoints.

**Development options:**
1. **Run Compliance Service first** — its Flyway migrations create all tables
2. **Use init script** — apply the Compliance Service schema manually
3. **Seed test data** — use the provided seed script to populate sample data for dashboard development

### 2.4 Run the Application

```bash
# Run with defaults (connects to localhost:5433)
./gradlew bootRun

# Verify health
curl localhost:8084/actuator/health

# Test an endpoint
curl localhost:8084/v1/insights/deviations?limit=5

# Test protocol analytics
curl localhost:8084/v1/insights/protocols/{protocolDefinitionId}/step-analytics
curl localhost:8084/v1/insights/protocols/{protocolDefinitionId}/completion-funnel
curl localhost:8084/v1/insights/protocols/{protocolDefinitionId}/outcome-distribution
curl localhost:8084/v1/insights/protocols/{protocolDefinitionId}/enrollment-trends?interval=weekly

# Test facility ranking and deviation analytics
curl localhost:8084/v1/insights/facilities/ranking?rankBy=complianceRate&order=desc
curl localhost:8084/v1/insights/deviations/by-action?limit=10
curl localhost:8084/v1/insights/deviations/resolution-rate

# Test event processing quality and patient risk
curl localhost:8084/v1/insights/events/processing-quality
curl localhost:8084/v1/insights/patients/at-risk-hotspots
curl localhost:8084/v1/insights/patients/repeat-deviations?minDeviations=3

# Test patient events and deviations
curl localhost:8084/v1/insights/patients/{patientId}/events?limit=10
curl localhost:8084/v1/insights/patients/{patientId}/deviations

# Test source comparison
curl "localhost:8084/v1/insights/events/compare-sources?sourceA=ehr-system-a&sourceB=ehr-system-b&windowSeconds=300"

# Test ingestion analytics
curl localhost:8084/v1/insights/ingestion/funnel
curl localhost:8084/v1/insights/ingestion/rejections
curl localhost:8084/v1/insights/ingestion/source-quality
curl localhost:8084/v1/insights/ingestion/pipeline-loss

# Test lookup/filter endpoints
curl localhost:8084/v1/insights/lookups/protocols
curl localhost:8084/v1/insights/lookups/facilities
curl localhost:8084/v1/insights/lookups/practitioners
curl localhost:8084/v1/insights/lookups/sources
curl localhost:8084/v1/insights/lookups/patients
```

## 3. Configuration Reference

### 3.1 Application Properties

```yaml
server:
  port: ${SERVER_PORT:8084}

spring:
  application:
    name: cce-insights-service
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5433}/${DB_NAME:cce_collector}
    username: ${DB_USERNAME:cce_user}
    password: ${DB_PASSWORD:cce_pass}
    hikari:
      maximum-pool-size: ${DB_POOL_SIZE:10}
      read-only: true
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
    properties:
      hibernate:
        jdbc:
          time_zone: UTC
        default_read_only: true

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  endpoint:
    health:
      show-details: always
      probes:
        enabled: true
  metrics:
    tags:
      application: cce-insights-service
```

### 3.2 Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | `8084` | HTTP port |
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5433` | PostgreSQL port (shared with Collector Service) |
| `DB_NAME` | `cce_collector` | Shared database name (all CCE services) |
| `DB_USERNAME` | `cce_user` | Database username (shared with Collector Service) |
| `DB_PASSWORD` | `cce_pass` | Database password (shared with Collector Service) |
| `DB_POOL_SIZE` | `10` | HikariCP max pool size |
| `CACHE_TTL_LOOKUPS` | `60` | Lookup cache TTL in minutes |
| `CACHE_TTL_ANALYTICS` | `30` | Analytics cache TTL in minutes |
| `CACHE_TTL_METRICS` | `15` | Metrics cache TTL in minutes |

## 4. Project Structure

```
cce-insights-service/
├── build.gradle
├── settings.gradle
├── gradlew / gradlew.bat
├── gradle/wrapper/
├── Dockerfile
├── docker-compose.yml
├── .dockerignore
├── .env.example
├── .gitignore
├── README.md
├── RELEASE_NOTES.md
├── docs/
│   ├── architecture-overview.md
│   ├── api-reference.md
│   ├── data-dictionary.md
│   ├── developer-setup.md
│   ├── deployment-guide.md
│   └── flow-diagrams.md
├── artifacts/
│   └── subtasks.md
└── src/
    ├── main/
    │   ├── java/org/openphc/cce/insights/
    │   │   ├── InsightsServiceApplication.java
    │   │   ├── config/          # JpaConfig, MetricsConfig, ObservabilityConfig
    │   │   ├── domain/entity/   # 6 @Immutable entities (incl. InboundEvent)
    │   │   ├── domain/enums/    # 5 enums
    │   │   ├── domain/repository/ # 7 repos (ReadOnlyRepository + 6)
    │   │   ├── health/          # DatabaseHealthIndicator
    │   │   ├── service/         # 10 services + DateUtil
    │   │   └── web/controller/ + web/dto/  # 10 controllers, ~30 DTOs
    │   └── resources/
    │       ├── application.yml
    │       ├── application-local.yml
    │       ├── application-docker.yml
    │       ├── application-test.yml
    │       └── logback-spring.xml
    ├── test/java/                    # Unit tests
    └── integrationTest/
        ├── java/                    # Integration tests (Testcontainers)
        └── resources/
            ├── init-schema.sql      # DDL for all 6 tables
            └── seed-data.sql        # Sample data
```

## 5. Testing

### 5.1 Commands

```bash
# Unit tests
./gradlew test

# Integration tests
./gradlew integrationTest

# All tests
./gradlew build

# Specific test
./gradlew test --tests ComplianceSummaryServiceTest

# Coverage report
./gradlew test jacocoTestReport
```

### 5.2 Test Strategy

- **Unit tests:** Mock repositories, test service layer aggregation logic
- **API tests:** `@WebMvcTest` with MockMvc — test controllers, request validation, response serialization
- **Integration tests:** Testcontainers PostgreSQL with seeded compliance data — test end-to-end query execution

### 5.3 Test Data Seeding

Integration tests use SQL scripts to seed the Compliance Service schema and sample data:

```sql
-- init-schema.sql: Create Compliance Service tables
-- seed-data.sql: Insert sample protocols, instances, steps, deviations
```

## 6. Docker Build

The project includes a multi-stage Dockerfile and docker-compose.yml for containerized development.

### 6.1 Docker Compose (Recommended)

```bash
# Copy and configure environment
cp .env.example .env

# Start PostgreSQL + Insights Service
docker compose up -d

# Verify
curl localhost:8084/actuator/health
```

### 6.2 Standalone Docker Build

```bash
# Build Docker image
docker build -t cce-insights-service .

# Run with Docker
docker run -p 8084:8084 \
  -e SPRING_PROFILES_ACTIVE=docker \
  -e DB_HOST=host.docker.internal \
  -e DB_PORT=5432 \
  -e DB_NAME=cce_collector \
  -e DB_USERNAME=cce_user \
  -e DB_PASSWORD=cce_pass \
  cce-insights-service
```

> See [deployment-guide.md](deployment-guide.md) for production deployment options including Kubernetes manifests.
