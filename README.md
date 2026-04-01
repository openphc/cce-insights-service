# CCE Insights Service

**Read-only compliance analytics API** for the Clinical Care Engine (CCE) platform. Provides 38 REST endpoints serving protocol adherence metrics, deviation analytics, event volume trends, ingestion pipeline monitoring, patient risk analysis, and lookup/filter data for dashboards.

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

## API Endpoints (38)

| Group | Endpoints | Path Prefix |
|-------|-----------|-------------|
| Compliance Summaries | 3 | `/v1/insights/protocols/`, `/v1/insights/facilities/` |
| Patient Compliance | 5 | `/v1/insights/patients/` |
| Deviations & Intelligence | 5 | `/v1/insights/deviations/`, `/v1/insights/intelligence/` |
| Event Volume | 7 | `/v1/insights/events/` |
| Protocol Analytics | 4 | `/v1/insights/protocols/{id}/` |
| Facility Analytics | 1 | `/v1/insights/facilities/ranking` |
| Processing Quality | 1 | `/v1/insights/events/processing-quality` |
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

## Documentation

| Document | Description |
|----------|-------------|
| [Architecture Overview](docs/architecture-overview.md) | System context, package structure, data access patterns |
| [API Reference](docs/api-reference.md) | All 38 endpoints with request/response schemas |
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
├── config/           # JpaConfig, ObservabilityConfig, MetricsConfig, CacheConfig
├── domain/
│   ├── entity/       # 6 @Immutable JPA entities
│   ├── enums/        # 5 enums
│   └── repository/   # 7 repositories (ReadOnlyRepository base)
├── health/           # DatabaseHealthIndicator
├── service/          # 10 services + DateUtil
└── web/
    ├── controller/   # 11 REST controllers (incl. LookupController)
    └── dto/          # ~30 DTOs + ApiResponse
```