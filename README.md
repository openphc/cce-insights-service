# CCE Insights Service

**Read-only compliance analytics API** for the Clinical Care Engine (CCE) platform. Provides 33 REST endpoints serving protocol adherence metrics, deviation analytics, event volume trends, ingestion pipeline monitoring, patient risk analysis, intelligence delivery tracking, and lookup/filter data for dashboards.

## Architecture

```
Analytics UI → CCE Gateway (OAuth) → CCE Insights Service → PostgreSQL (cce_collector)
```

| Component | Technology |
|-----------|------------|
| Language | Java 21 |
| Framework | Spring Boot 3.4.4 |
| Build | Gradle 8.12 |
| Database | PostgreSQL 16 (shared, read-only) |
| Caching | Caffeine (3-tier: lookups/analytics/metrics) |
| Observability | Micrometer + Prometheus |
| Testing | JUnit 5 + Testcontainers |

## Quick Start

```bash
# Build
./gradlew build -x test

# Run (requires PostgreSQL with cce_collector schema)
./gradlew bootRun

# Docker
docker compose up -d
```

## API Endpoints (33)

| Group | Endpoints | Path Prefix |
|-------|-----------|-------------|
| Compliance Summaries | 4 | `/v1/insights/protocols/`, `/v1/insights/facilities/` |
| Dashboard | 2 | `/v1/insights/dashboard/` |
| Patient Compliance | 5 | `/v1/insights/patients/` |
| Deviations | 4 | `/v1/insights/deviations/` |
| Intelligence | 1 | `/v1/insights/intelligence/` |
| Event Volume | 5 | `/v1/insights/events/` |
| Protocol Analytics | 5 | `/v1/insights/protocols/{id}/` |
| Facility Analytics | 1 | `/v1/insights/facilities/ranking` |
| Practitioner Analytics | 1 | `/v1/insights/practitioners/ranking` |
| Patient Risk | 2 | `/v1/insights/patients/` |
| Ingestion Analytics | 4 | `/v1/insights/ingestion/` |
| Lookups | 5 | `/v1/insights/lookups/` |
| Export | 1 | `/v1/insights/exports/` |

See [docs/api-reference.md](docs/api-reference.md) for full request/response schemas.

## Database Tables (Read-Only)

| Table | Owner |
|-------|-------|
| `protocol_definition` | Compliance Service |
| `protocol_instance` | Compliance Service |
| `step_instance` | Compliance Service |
| `deviation` | Compliance Service |
| `event_log` | Compliance Service |
| `inbound_event` | Collector Service |
| `intelligence_delivery` | Intelligence Service |
| `receiver_adaptor` | Intelligence Service |
| `destination_adaptor_mapping` | Intelligence Service |

## Documentation

| Document | Description |
|----------|-------------|
| [Architecture Overview](docs/architecture-overview.md) | System context, package structure, data access patterns |
| [API Reference](docs/api-reference.md) | All 37 endpoints with request/response schemas |
| [Data Dictionary](docs/data-dictionary.md) | Table schemas, enums, aggregation formulas |
| [Developer Setup](docs/developer-setup.md) | Prerequisites, configuration, testing |
| [Flow Diagrams](docs/flow-diagrams.md) | Mermaid sequence/flow diagrams for all subsystems |
| [Deployment Guide](docs/deployment-guide.md) | Docker, Kubernetes, bare-metal deployment |
| [Release Notes](RELEASE_NOTES.md) | Version history and changelog |

## Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | `8084` | HTTP port |
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `cce_collector` | Shared database |
| `DB_USERNAME` | `cce_user` | Database user |
| `DB_PASSWORD` | `cce_pass` | Database password |
| `DB_POOL_SIZE` | `10` | HikariCP pool size |
| `CACHE_TTL_LOOKUPS` | `60` | Lookup cache TTL (minutes) |
| `CACHE_TTL_ANALYTICS` | `30` | Analytics cache TTL (minutes) |
| `CACHE_TTL_METRICS` | `15` | Metrics cache TTL (minutes) |

## Testing

```bash
./gradlew test                  # Unit tests
./gradlew integrationTest       # Integration tests (Testcontainers)
./gradlew test jacocoTestReport # Coverage report
```

## Project Structure

```
src/main/java/org/openphc/cce/insights/
├── InsightsServiceApplication.java
├── config/           # CacheConfig, JpaConfig, MetricsConfig, ObservabilityConfig
├── domain/
│   ├── entity/       # 9 @Immutable JPA entities
│   ├── enums/        # 5 enums
│   └── repository/   # 9 repositories (ReadOnlyRepository base)
├── health/           # DatabaseHealthIndicator
├── service/          # 12 services + DateUtil utility
└── web/
    ├── controller/   # 14 REST controllers
    ├── dto/          # ~35 DTOs + ApiResponse
    └── GlobalExceptionHandler.java
```