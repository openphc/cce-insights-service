# CCE Insights Service — AI Agent Instructions

## Architecture

Spring Boot 3.4.x / Java 21 microservice that serves compliance analytics — protocol adherence rates, deviation trends, facility-level summaries, and patient compliance timelines. It is a **read-only** service that queries the Compliance DB directly (Phase 1). All requests arrive via the CCE Gateway Service, which handles authentication and authorization.

> **Naming:** This service is referred to as "Analytics Service" in the CCE Solution Design v0.3 document. It has been renamed to **Insights Service** (`cce-insights-service`) for the implementation.

**Core role:** Aggregate and serve compliance data for dashboards, reports, and data exports. No event processing, no protocol matching, no state transitions.

## Key Conventions

- **Package:** `org.openphc.cce.insights` — ~45 source files across 10 packages
- **Read-only database access:** No writes to any table. Uses Spring Data JPA with read-only transactions (`@Transactional(readOnly = true)`)
- **Shared database:** Connects to the same PostgreSQL database (`cce_collector`) as all other CCE services (Phase 1). Queries `protocol_instance`, `step_instance`, `deviation`, `protocol_definition`, `event_log` tables.
- **No Kafka integration:** Does not consume from or produce to any Kafka topic. Purely REST API → Database.
- **DTOs only:** Never exposes JPA entities in REST responses. Separate DTOs with `DtoMapper`.
- **All timestamps:** `OffsetDateTime` in UTC
- **IDs:** `UUID` for all entity primary keys
- **Caching:** Optional Redis caching for frequently requested aggregations (future phase). Initial release queries DB directly.
- **Build tool:** Gradle 8.x
- **No Flyway migrations:** The Insights Service does not own any tables. Schema is managed by the Compliance Service.

## API Endpoints

All endpoints prefixed with `/v1/`. All require the `dashboard:read` OAuth scope (enforced by Gateway).

### Compliance Summaries

| Method | Path | Description |
|--------|------|-------------|
| GET | `/v1/insights/protocols/{protocolDefinitionId}/compliance-summary` | Aggregate compliance metrics for a protocol |
| GET | `/v1/insights/facilities/{facilityId}/compliance-summary` | Facility-level compliance metrics across all protocols |
| GET | `/v1/insights/protocols/{protocolDefinitionId}/patients` | List patients by compliance status for a protocol |

### Patient Compliance

| Method | Path | Description |
|--------|------|-------------|
| GET | `/v1/insights/patients/{patientId}/compliance-timeline` | Full compliance timeline across all enrolled protocols |
| GET | `/v1/insights/patients/{patientId}/protocol-tracking` | All protocol instances for a patient (delegated read from compliance data) |
| GET | `/v1/insights/patients/{patientId}/protocol-tracking/{protocolInstanceId}` | Detailed tracking with step instances |

### Intelligence & Deviations

| Method | Path | Description |
|--------|------|-------------|
| GET | `/v1/insights/intelligence/summary` | Intelligence events summary (counts by type, period) |
| GET | `/v1/insights/deviations` | List deviations with filter/sort/pagination |
| GET | `/v1/insights/deviations/trends` | Deviation trends over time (daily/weekly/monthly aggregation) |

### Event Volume & Activity Metrics

| Method | Path | Description |
|--------|------|-------------|
| GET | `/v1/insights/events/summary` | Aggregate event counts by resourceType, facility, source, processing status |
| GET | `/v1/insights/events/trends` | Event volume over time (daily/weekly/monthly) with resource type breakdown |
| GET | `/v1/insights/events/by-resource-type` | Event counts grouped by FHIR resourceType |
| GET | `/v1/insights/events/by-facility` | Event counts grouped by facility with resource type breakdown |
| GET | `/v1/insights/events/by-practitioner` | Event counts grouped by practitioner (extracted from FHIR data JSONB) |
| GET | `/v1/insights/events/by-source` | Event counts grouped by source system |

### Protocol Analytics

| Method | Path | Description |
|--------|------|-------------|
| GET | `/v1/insights/protocols/{protocolDefinitionId}/step-analytics` | Per-step completion rates, timeliness (EARLY/ON_TIME/LATE), avg/median time-to-complete |
| GET | `/v1/insights/protocols/{protocolDefinitionId}/completion-funnel` | Drop-off rates at each sequential step — where patients are lost |
| GET | `/v1/insights/protocols/{protocolDefinitionId}/outcome-distribution` | % of protocol instances by terminal status (ACTIVE/COMPLETED/WITHDRAWN/EXPIRED) |
| GET | `/v1/insights/protocols/{protocolDefinitionId}/enrollment-trends` | New enrollments over time (daily/weekly/monthly) |

### Facility Analytics

| Method | Path | Description |
|--------|------|-------------|
| GET | `/v1/insights/facilities/ranking` | Facility leaderboard by complianceRate, deviationCount, or eventVolume |

### Deviation Analytics

| Method | Path | Description |
|--------|------|-------------|
| GET | `/v1/insights/deviations/by-action` | Most deviated-from protocol steps grouped by actionId |
| GET | `/v1/insights/deviations/resolution-rate` | OVERDUE→COMPLETED (resolved) vs OVERDUE→MISSED (escalated) ratio |

### Event Processing & Integration Health

| Method | Path | Description |
|--------|------|-------------|
| GET | `/v1/insights/events/processing-quality` | MATCHED/ZERO_MATCH/DUPLICATE ratios per source system |

### Patient Risk Analytics

| Method | Path | Description |
|--------|------|-------------|
| GET | `/v1/insights/patients/at-risk-hotspots` | Concentration of at_risk/non_compliant patients by facility |
| GET | `/v1/insights/patients/repeat-deviations` | Patients with deviations >= minDeviations threshold |

### Exports

| Method | Path | Description |
|--------|------|-------------|
| GET | `/v1/insights/exports/compliance-report` | Export compliance data in CSV or JSON format |

### Response Envelope

```json
// Success (single resource)
{ "data": { ... } }

// Success (list with pagination)
{
  "data": [ ... ],
  "pagination": {
    "limit": 50,
    "next_cursor": "eyJpZCI6MTIzfQ==",
    "has_more": true
  }
}

// Error
{
  "error": {
    "code": "NOT_FOUND",
    "message": "Protocol definition not found"
  }
}
```

## Database Access (Read-Only)

### Tables Queried

| Table | Owner | Access | Purpose |
|---|---|---|---|
| `protocol_instance` | Compliance Service | Read-only | Patient enrollments, compliance rates |
| `step_instance` | Compliance Service | Read-only | Step states, timing, completion status |
| `deviation` | Compliance Service | Read-only | Deviation records, trends |
| `protocol_definition` | Compliance Service | Read-only | Protocol metadata (name, version) |
| `event_log` | Compliance Service | Read-only | Patient event timeline, facility ID source |

### Key Aggregation Queries

- **Protocol compliance rate:** Count of `step_instance` by `completion_status` grouped by protocol
- **Facility summary:** JOIN `protocol_instance` → `event_log` (for `facility_id`) → `step_instance`
- **Step analytics:** COUNT by `state` and `completion_status` per `action_id`, with `PERCENTILE_CONT(0.5)` for median
- **Completion funnel:** COUNT DISTINCT `patient_id` reached vs completed per `action_id`
- **Outcome distribution:** COUNT `protocol_instance` grouped by `status`
- **Enrollment trends:** COUNT `protocol_instance` grouped by `DATE_TRUNC(:interval, enrolled_at)`
- **Facility ranking:** Cross-table aggregation of compliance rate, deviation count, event volume per `facility_id`
- **Deviation trends:** COUNT deviations grouped by `deviation_type`, `detected_at` (date-truncated)
- **Deviations by action:** COUNT deviations grouped by `step_instance.action_id`
- **Resolution rate:** Track `OVERDUE` deviations → `step_instance.state` (COMPLETED = resolved, MISSED = escalated)
- **Processing quality:** COUNT `event_log` grouped by `source`, `processing_status`
- **At-risk hotspots:** Classify patients per facility as on_track/at_risk/non_compliant using correlated subqueries on `step_instance.state`
- **Repeat deviations:** COUNT deviations per `patient_id` with `HAVING COUNT(*) >= :minDeviations`
- **Patient timeline:** JOIN `event_log` + `step_instance` ordered by `event_time`

## Build & Run

```bash
./gradlew build -x test                # Fast build
./gradlew build                         # Build + all tests
cd /path/to/cce-collector-service && docker compose up -d  # Start shared PostgreSQL + Kafka
./gradlew bootRun                       # Run app (port 8084)
curl localhost:8084/actuator/health     # Health check
```

## Testing

- Unit tests: mocked repositories — `src/test/java`
- Integration tests: Testcontainers PostgreSQL — `src/integrationTest/java`
- API tests: MockMvc with `@WebMvcTest`
- Run unit tests: `./gradlew test`
- Run integration tests: `./gradlew integrationTest`

## Key Files to Read First

- `docs/architecture-overview.md` — system context, data access patterns, caching strategy
- `docs/api-reference.md` — all REST endpoints with request/response examples
- `docs/data-dictionary.md` — query patterns, aggregation formulas, filter parameters
- `docs/developer-setup.md` — local setup, shared database requirement

## What's NOT in Scope (Release 1.0.0)

- **Dedicated analytics database** (Phase 2) — initial release queries Compliance DB directly
- **Redis caching** — no caching layer in 1.0.0; all queries hit PostgreSQL
- **Real-time streaming analytics** — no Kafka consumption; batch queries only
- **Data export scheduling** — exports are on-demand via API only
- **Intelligence event aggregation** — intelligence triggers are not yet published by Compliance Service in 1.0.0
