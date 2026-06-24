# Release Notes

## Unreleased

### Removed

- **`GET /v1/insights/events/source-comparison`** — Removed source comparison endpoint, `SourceComparisonDto`, overlap/unique repository queries, and related integration tests.
- **`GET /v1/insights/events/by-practitioner`**, **`GET /v1/insights/events/by-source`**, **`GET /v1/insights/events/processing-quality`** — Removed practitioner/source/processing-quality event endpoints, services, DTOs, repository queries, and tests.

## v1.1.0 — Demo Intelligence Release

**Release Date:** June 2025  
**Branch:** `demo-intelligence`  

---

### Overview

Major feature release adding intelligence analytics, protocol action introspection, dashboard endpoints, and practitioner ranking. Also includes significant data corrections for demo environment and expanded filtering across all analytics endpoints.

---

### New Features

- **Protocol Title & Related Artifacts** — `GET /patients/{id}/protocol-tracking` now returns `protocolTitle` (human-readable name) and `relatedArtifact` array (documentation URLs, thumbnail images) extracted from the protocol definition JSONB
- **Step Analytics Facility Filter** — `GET /protocols/{id}/step-analytics` now correctly filters by `facilityId`, using protocol-instance-level facility association so steps without individual facility-tagged events are still included
- **Dashboard Controller** — 2 new endpoints (`/dashboard/overview`, `/dashboard/compliance-summary`) providing aggregated KPI data
- **Practitioner Ranking Controller** — New endpoint for ranking practitioners by compliance rate, deviation count, or event volume
- **Protocol Action Order** — `GET /protocols/{id}/action-order` returns ordered list of protocol actions with `type` and `title` fields, enabling the UI to show only `fire-event` type intelligence actions
- **Intelligence Summary** — Respects date range and facility filters for dynamic analytics
- **All Protocols Compliance Summary** — `GET /protocols/compliance-summary` returns compliance summary across all protocols in one call
- **Due Date in Patient Timeline** — `PatientTimelineDto` now includes `dueDate` field for step tracking
- **Compliance Categorization** — Facilities and practitioners are categorized by compliance rate bands

---

### Improvements

- **Date range filters** — All analytics endpoints now accept `startDate` and `endDate` query parameters
- **Facility filter** — Protocol compliance summary endpoints respect `facilityId` filter
- **Intelligence delivery entities** — Added `IntelligenceDelivery`, `ReceiverAdaptor`, `DestinationAdaptorMapping` entities (9 total, up from 6)
- **14 controllers** (up from 11) with **36 GET endpoints**
- **ActionOrderEntryDto** — Now includes `type` (from `action.type.coding[0].code`) and `title` (from `action.title`) fields extracted from the protocol definition JSONB

---

### Demo Data Corrections

- **Protocol relatedArtifact** — RMNCH protocol definition now includes `relatedArtifact` array with WHO guideline documentation link and thumbnail image
- **Practitioner roles** — Nurse/CHW assigned to non-consultation steps, Doctor only for consultation and referral-ack steps
- **Source attribution** — `event_log.source` and `step_instance.completed_by_source` set to `openMRS` for consultation events
- **Step renames** — "ANC Visit # Referral" → "ANC Visit # Referral Initiated", "ANC Visit # Referral Ack" → "ANC Visit # Referral Closure"
- **Fix scripts** — `04-fix-demo-data.sql` provides migration script for applying corrections to live databases

---

### Bug Fixes

- **Fixed:** Step analytics (Service Workflow Compliance) showed inflated percentages (e.g., 1000%) when facility filter was active — step counts were unfiltered while denominator was facility-filtered
- **Fixed:** `AT_RISK` compliance category removed — only `COMPLIANT`, `MODERATE`, `NON_COMPLIANT` remain
- **Fixed:** Intelligence summary counted only last 30 days — now counts all deviations
- **Fixed:** Facility filter ignored on protocol compliance summary endpoints
- **Fixed:** Compliance rate formula now uses step-based calculation

---

## v1.0.0 — Initial Release

**Release Date:** 2025  
**Sprint Target:** Release 1.0.0  

---

### Overview

First production release of the **CCE Insights Service** — a read-only analytics API providing compliance dashboards, deviation analytics, event volume metrics, ingestion monitoring, and patient risk analysis for the CCE platform.

---

### Highlights

- **33 REST endpoints** across 14 controllers
- **9 database tables** queried (read-only) from the shared `cce_collector` PostgreSQL database
- **Zero write operations** — fully read-only JPA entities with `@Immutable` annotations
- **Caffeine caching** — 3-tier in-memory cache (lookups/analytics/metrics) with configurable TTLs
- **Docker-ready** — multi-stage Dockerfile, docker-compose.yml
- **Comprehensive observability** — Prometheus metrics, structured JSON logging, custom health indicators
- **Integration tested** — Testcontainers-based tests covering all endpoint groups

---

### API Endpoints (33 total)

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

#### Event Volume & Activity Metrics (5 endpoints) — S7
| # | Endpoint |
|---|----------|
| 14 | `GET /v1/insights/events/kpis` |
| 15 | `GET /v1/insights/events/summary` |
| 16 | `GET /v1/insights/events/trends` |
| 17 | `GET /v1/insights/events/by-resource-type` |
| 18 | `GET /v1/insights/events/by-facility` |

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

#### Lookup Endpoints (5 endpoints) — S16
| # | Endpoint |
|---|----------|
| 34 | `GET /v1/insights/lookups/protocols` |
| 35 | `GET /v1/insights/lookups/facilities` |
| 36 | `GET /v1/insights/lookups/practitioners` |
| 37 | `GET /v1/insights/lookups/sources` |
| 38 | `GET /v1/insights/lookups/patients` |

---

### Architecture

| Component | Details |
|-----------|---------|
| **Framework** | Spring Boot 3.x, Java 21 |
| **Build** | Gradle 8.12 with JaCoCo |
| **Database** | ClickHouse (analytics backend, read-only via jOOQ 3.19) |
| **Entities** | 9: ProtocolDefinition, ProtocolInstance, StepInstance, Deviation, ComplianceEventLog, InboundEventLog, IntelligenceDelivery, ReceiverAdaptor, DestinationAdaptorMapping |
| **Repositories** | 9 (jOOQ DSL implementations extending AbstractClickHouseRepository) |
| **Services** | 12 + DateUtil utility |
| **Controllers** | 14 |
| **DTOs** | ~35 |
| **Configs** | 5 (CacheConfig, JooqConfig, MetricsConfig, ObservabilityConfig, DatabaseHealthIndicator) |
| **Integration Tests** | 10 IT classes with MockMvc / @WebMvcTest |

---

### Tables Queried

| Table | Owner | Purpose |
|-------|-------|---------|
| `protocol_definitions` | Compliance Service | Protocol metadata |
| `protocol_instances` | Compliance Service | Patient enrollments |
| `step_instances` | Compliance Service | Step states & timing |
| `deviations` | Compliance Service | Deviation records |
| `compliance_event_logs` | Compliance Service | Event history & volume |
| `inbound_event_logs` | Collector Service | Ingestion pipeline analytics & facility names |
| `intelligence_deliveries` | Intelligence Service | Intelligence delivery tracking |
| `receiver_adaptors` | Intelligence Service | Adaptor registry |
| `destination_adaptor_mappings` | Intelligence Service | Destination routing |
| `mv_patient_facility_latest` | Collector Service | Materialized view — patient→facility mapping |

---

### Bug Fixes in This Release

- **Fixed:** `FacilityRankingService.deviationCountMap` was never populated — compliance rate always returned 100%. Now queries deviation counts per facility.
- **Fixed:** `FacilityRankingService` compliance rate formula — changed from `1 - deviations/events` to step-based `(completedSteps + skippedSteps) / totalSteps`. Uses `findStepComplianceByFacility()` query.
- **Fixed:** `FacilityRankingService` rankBy switch-case — now matches UI values (`complianceRate`, `deviationCount`, `eventVolume`). Added `order` parameter (asc/desc) support.
- **Fixed:** `PatientRiskService.getAtRiskHotspots()` — was computing compliance categories globally instead of per-facility. Added `findFacilityPatientMapping()` query to scope patients per facility.
- **Fixed:** `ComplianceSummaryService.getFacilityComplianceSummary()` — was ignoring `facilityId` filter. Added `findPatientsByFacility()` query to restrict results to the requested facility.
- **Fixed:** `EventVolumeService.getTrends()` — source parameter was not being passed from controller to service. Wired the parameter through.
- **Fixed:** `EventVolumeService.getSummary()` — `processingStatusBreakdown` was always `null`. Now populates with `{matched: {count, percentage}, zeroMatch: {count, percentage}, duplicate: {count, percentage}}` using `countByProcessingStatus()` query.
- **Fixed:** Missing `logstash-logback-encoder` dependency — JSON logging (docker profile) would fail at runtime.
- **Fixed:** PostgreSQL nullable parameter CAST issue — native queries with nullable parameters now use `CAST(:param AS type)` across all 4 repository files.
- **Fixed:** `EventVolumeService.getSummary()` indexing bug — `countByFacility()` returns 3 columns but code indexed wrong column as count.
- **Fixed:** `java.time.Instant` casting — PostgreSQL returns `Instant` for `timestamptz` columns in native queries, not `Timestamp`. Added `DateUtil.toOffsetDateTime()` utility.
- **Fixed:** CSV export `HttpMessageNotWritableException` — `StreamingResponseBody` in `ResponseEntity` fails content negotiation. Rewritten to use `HttpServletResponse` directly.
- **Fixed:** `GlobalExceptionHandler` was silently swallowing exceptions — added `@Slf4j` and `log.error()` calls.

### Code Quality Improvements

- Extracted `DateUtil` utility — eliminated 4x duplicated `mapInterval()` and `extractDate()` methods across services.
- Added `DateUtil.toOffsetDateTime()` helper — handles `Instant`, `OffsetDateTime`, and `Timestamp` type conversion from native query results.
- Added `.dockerignore` for optimized Docker builds.
- Added `.env.example` for environment variable documentation.
- Implemented Caffeine caching with `@Cacheable` annotations on 25 service methods across 9 classes.

---

### Known Limitations

- **PatientController** accesses repositories directly — business logic should be delegated to services in a future refactor.
- **No pagination** on some list endpoints — cursor pagination to be added where missing.
- **Per-instance caching** — Caffeine caches are not shared across instances. Phase 2 will introduce Redis for distributed caching.

---

### Dependencies

| Dependency | Version |
|------------|---------|
| Spring Boot | 3.x |
| jOOQ | 3.19 |
| ClickHouse JDBC | 0.8.3 |
| Caffeine | (managed) |
| Micrometer Prometheus | (managed) |
| Logstash Logback Encoder | 7.4 |
| Lombok | (managed) |
| JUnit 5 | (managed) |
