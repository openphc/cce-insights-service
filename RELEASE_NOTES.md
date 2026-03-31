# Release Notes

## v1.0.0 — Initial Release

**Release Date:** 2025  
**Sprint Target:** Release 1.0.0  

---

### Overview

First production release of the **CCE Insights Service** — a read-only analytics API providing compliance dashboards, deviation analytics, event volume metrics, ingestion monitoring, and patient risk analysis for the CCE platform.

---

### Highlights

- **32 REST endpoints** across 10 controllers
- **6 database tables** queried (read-only) from the shared `cce_collector` PostgreSQL database
- **Zero write operations** — fully read-only JPA entities with `@Immutable` annotations
- **Docker-ready** — multi-stage Dockerfile, docker-compose.yml, and Kubernetes manifests
- **Comprehensive observability** — Prometheus metrics, structured JSON logging, custom health indicators
- **Integration tested** — Testcontainers-based tests covering all endpoint groups

---

### API Endpoints (32 total)

#### Compliance Summaries (3 endpoints) — S4
| # | Endpoint |
|---|----------|
| 1 | `GET /v1/insights/protocols/{id}/compliance-summary` |
| 2 | `GET /v1/insights/facilities/{id}/compliance-summary` |
| 3 | `GET /v1/insights/protocols/{id}/patients` |

#### Patient Compliance (5 endpoints) — S5
| # | Endpoint |
|---|----------|
| 4 | `GET /v1/insights/patients/{id}/compliance-timeline` |
| 5 | `GET /v1/insights/patients/{id}/protocol-tracking` |
| 6 | `GET /v1/insights/patients/{id}/protocol-tracking/{piId}` |
| 7 | `GET /v1/insights/patients/{id}/events` |
| 8 | `GET /v1/insights/patients/{id}/deviations` |

#### Deviations & Intelligence (5 endpoints) — S6, S9
| # | Endpoint |
|---|----------|
| 9 | `GET /v1/insights/deviations` |
| 10 | `GET /v1/insights/deviations/trends` |
| 11 | `GET /v1/insights/intelligence/summary` |
| 12 | `GET /v1/insights/deviations/by-action` |
| 13 | `GET /v1/insights/deviations/resolution-rate` |

#### Event Volume & Activity Metrics (7 endpoints) — S7
| # | Endpoint |
|---|----------|
| 14 | `GET /v1/insights/events/summary` |
| 15 | `GET /v1/insights/events/trends` |
| 16 | `GET /v1/insights/events/by-resource-type` |
| 17 | `GET /v1/insights/events/by-facility` |
| 18 | `GET /v1/insights/events/by-practitioner` |
| 19 | `GET /v1/insights/events/by-source` |
| 20 | `GET /v1/insights/events/compare-sources` |

#### Protocol Analytics (4 endpoints) — S8
| # | Endpoint |
|---|----------|
| 21 | `GET /v1/insights/protocols/{id}/step-analytics` |
| 22 | `GET /v1/insights/protocols/{id}/completion-funnel` |
| 23 | `GET /v1/insights/protocols/{id}/outcome-distribution` |
| 24 | `GET /v1/insights/protocols/{id}/enrollment-trends` |

#### Facility Analytics (1 endpoint) — S9
| # | Endpoint |
|---|----------|
| 25 | `GET /v1/insights/facilities/ranking` |

#### Event Processing Quality (1 endpoint) — S10
| # | Endpoint |
|---|----------|
| 26 | `GET /v1/insights/events/processing-quality` |

#### Patient Risk Analytics (2 endpoints) — S10
| # | Endpoint |
|---|----------|
| 27 | `GET /v1/insights/patients/at-risk-hotspots` |
| 28 | `GET /v1/insights/patients/repeat-deviations` |

#### Ingestion Analytics (4 endpoints) — S14
| # | Endpoint |
|---|----------|
| 29 | `GET /v1/insights/ingestion/funnel` |
| 30 | `GET /v1/insights/ingestion/rejections` |
| 31 | `GET /v1/insights/ingestion/source-quality` |
| 32 | `GET /v1/insights/ingestion/pipeline-loss` |

#### Export (1 endpoint) — S11
| # | Endpoint |
|---|----------|
| 33 | `GET /v1/insights/exports/compliance-report` |

---

### Architecture

| Component | Details |
|-----------|---------|
| **Framework** | Spring Boot 3.4.4, Java 21 |
| **Build** | Gradle 8.12 with JaCoCo |
| **Database** | PostgreSQL 16 (shared `cce_collector`, read-only) |
| **Entities** | 6: ProtocolDefinition, ProtocolInstance, StepInstance, Deviation, EventLog, InboundEvent |
| **Repositories** | 7 (including ReadOnlyRepository base) |
| **Services** | 10 + DateUtil utility |
| **Controllers** | 10 |
| **DTOs** | ~30 |
| **Integration Tests** | 10 IT classes with Testcontainers |

---

### Tables Queried

| Table | Owner | Purpose |
|-------|-------|---------|
| `protocol_definition` | Compliance Service | Protocol metadata |
| `protocol_instance` | Compliance Service | Patient enrollments |
| `step_instance` | Compliance Service | Step states & timing |
| `deviation` | Compliance Service | Deviation records |
| `event_log` | Compliance Service | Event history & volume |
| `inbound_event` | Collector Service | Ingestion pipeline analytics |

---

### Bug Fixes in This Release

- **Fixed:** `FacilityRankingService.deviationCountMap` was never populated — compliance rate always returned 100%. Now queries deviation counts per facility.
- **Fixed:** Missing `logstash-logback-encoder` dependency — JSON logging (docker profile) would fail at runtime.

### Code Quality Improvements

- Extracted `DateUtil` utility — eliminated 4x duplicated `mapInterval()` and `extractDate()` methods across services.
- Added `.dockerignore` for optimized Docker builds.
- Added `.env.example` for environment variable documentation.

---

### Known Limitations

- **N+1 query patterns** in `ComplianceSummaryService`, `PatientTimelineService`, `ExportService`, and `PatientRiskService` — acceptable at current scale, targeted for Phase 2 optimization.
- **PatientController** accesses repositories directly — business logic should be delegated to services in a future refactor.
- **No caching layer** — Phase 2 will introduce Redis for high-frequency dashboard queries.
- **No pagination** on some list endpoints — cursor pagination to be added where missing.

---

### Dependencies

| Dependency | Version |
|------------|---------|
| Spring Boot | 3.4.4 |
| Spring Data JPA | (managed) |
| Micrometer Prometheus | (managed) |
| Logstash Logback Encoder | 7.4 |
| PostgreSQL JDBC Driver | (managed) |
| Lombok | (managed) |
| Testcontainers | (managed) |
| JUnit 5 | (managed) |
