# Architecture & Design

## 1. System Context

The **CCE Insights Service** (referred to as "Analytics Service" in the Solution Design v0.3) serves compliance analytics data — protocol adherence rates, deviation trends, facility-level summaries, and patient compliance timelines. It is a **read-only** service that queries the Compliance DB directly and exposes REST APIs consumed by the Analytics UI dashboard.

> All inbound requests arrive via the **CCE Gateway Service**, which validates OAuth tokens and enforces the `dashboard:read` scope. The Insights Service does not handle authentication or authorization.

```mermaid
graph TB
    subgraph External
        UI["Analytics UI<br/>(React Dashboard)"]
        GATEWAY["CCE Gateway Service<br/>(Auth & Routing)"]
    end

    subgraph CCE Insights Service
        API["REST API<br/>(Spring MVC)"]
        SUMMARY["Compliance Summary<br/>Service"]
        TIMELINE["Patient Timeline<br/>Service"]
        DEVIATION["Deviation Analytics<br/>Service"]
        EVENTVOLUME["Event Volume<br/>Service"]
        EXPORT["Export Service"]
    end

    subgraph Shared Infrastructure
        DB[("PostgreSQL 16<br/>(Compliance DB)")]
    end

    UI --> GATEWAY
    GATEWAY -->|"dashboard:read"| API
    API --> SUMMARY
    API --> TIMELINE
    API --> DEVIATION
    API --> EVENTVOLUME
    API --> EXPORT
    SUMMARY --> DB
    TIMELINE --> DB
    DEVIATION --> DB
    EVENTVOLUME --> DB
    EXPORT --> DB

    classDef service fill:#4A90D9,stroke:#2C5F8A,color:white
    classDef external fill:#7B8D8E,stroke:#566573,color:white
    classDef data fill:#27AE60,stroke:#1E8449,color:white

    class API,SUMMARY,TIMELINE,DEVIATION,EVENTVOLUME,EXPORT service
    class UI,GATEWAY external
    class DB data
```

**This service does NOT handle:** event ingestion, protocol matching, step completion, deviation detection, time-based transitions, authentication/authorization, or any write operations.

> **Event Volume Analytics:** In addition to compliance-focused analytics, the Insights Service provides event volume metrics — counts of clinical events grouped by FHIR `resourceType`, facility, practitioner, and source system. These metrics are derived from the `event_log` table (immutable log of all inbound CloudEvents maintained by the Compliance Service). Practitioner information is extracted from the `event_log.data` JSONB column using resource-type-specific paths.

---

## 2. Technology Stack

| Concern | Technology | Version |
|---------|------------|---------|
| Language | Java | 21 (LTS) |
| Framework | Spring Boot | 3.4.x |
| Build tool | Gradle | 8.x |
| Database | PostgreSQL | 16+ (shared with Compliance Service) |
| DB access | Spring Data JPA + Hibernate | (Spring Boot managed) |
| Observability | Micrometer + Prometheus | (Spring Boot managed) |
| Testing | JUnit 5, Testcontainers, MockMvc | |

### Key Gradle Dependencies

```groovy
// Spring Boot starters
implementation 'org.springframework.boot:spring-boot-starter-web'
implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
implementation 'org.springframework.boot:spring-boot-starter-actuator'
implementation 'org.springframework.boot:spring-boot-starter-validation'

// Database
runtimeOnly 'org.postgresql:postgresql'

// Observability
implementation 'io.micrometer:micrometer-registry-prometheus'

// Testing
testImplementation 'org.springframework.boot:spring-boot-starter-test'
testImplementation 'org.testcontainers:postgresql'
testImplementation 'org.testcontainers:junit-jupiter'
```

**Not included:** Spring Kafka (no Kafka integration), Flyway (no owned tables), HAPI FHIR (no FHIR parsing), json-logic-java (no expression evaluation).

---

## 3. Package Structure

```
src/main/java/org/openphc/cce/insights/
├── InsightsServiceApplication.java            # @SpringBootApplication
├── config/
│   ├── JpaConfig.java                         # Read-only transaction defaults
│   └── ObservabilityConfig.java               # Custom metrics
├── domain/
│   ├── entity/
│   │   ├── ProtocolDefinition.java            # Read-only entity
│   │   ├── ProtocolInstance.java              # Read-only entity
│   │   ├── StepInstance.java                  # Read-only entity
│   │   ├── Deviation.java                     # Read-only entity
│   │   └── EventLog.java                      # Read-only entity
│   ├── enums/
│   │   ├── ProtocolInstanceStatus.java        # ACTIVE, COMPLETED, WITHDRAWN, EXPIRED
│   │   ├── StepState.java                     # PENDING, DUE, OVERDUE, MISSED, COMPLETED, SKIPPED
│   │   ├── CompletionStatus.java              # EARLY, ON_TIME, LATE
│   │   └── DeviationType.java                 # OVERDUE, MISSED
│   └── repository/
│       ├── ProtocolDefinitionRepository.java
│       ├── ProtocolInstanceRepository.java
│       ├── StepInstanceRepository.java
│       ├── DeviationRepository.java
│       └── EventLogRepository.java
├── service/
│   ├── ComplianceSummaryService.java          # Protocol & facility compliance aggregation
│   ├── ProtocolAnalyticsService.java          # Step analytics, completion funnel, outcome distribution, enrollment trends
│   ├── PatientTimelineService.java            # Patient event timeline + tracking
│   ├── PatientRiskService.java                # At-risk hotspots, repeat deviations
│   ├── DeviationAnalyticsService.java         # Deviation trends, by-action, resolution rate
│   ├── FacilityRankingService.java            # Facility leaderboard
│   ├── EventVolumeService.java                # Event volume by resourceType, facility, practitioner, source
│   ├── ProcessingQualityService.java          # Event processing quality (MATCHED/ZERO_MATCH/DUPLICATE)
│   └── ExportService.java                     # CSV/JSON export generation
├── web/
│   ├── controller/
│   │   ├── ComplianceSummaryController.java
│   │   ├── ProtocolAnalyticsController.java
│   │   ├── PatientController.java
│   │   ├── PatientRiskController.java
│   │   ├── DeviationController.java
│   │   ├── FacilityRankingController.java
│   │   ├── EventVolumeController.java
│   │   ├── ProcessingQualityController.java
│   │   └── ExportController.java
│   └── dto/
│       ├── ComplianceSummaryDto.java
│       ├── FacilitySummaryDto.java
│       ├── PatientComplianceDto.java
│       ├── PatientTimelineDto.java
│       ├── StepAnalyticsDto.java
│       ├── CompletionFunnelDto.java
│       ├── OutcomeDistributionDto.java
│       ├── EnrollmentTrendDto.java
│       ├── FacilityRankingDto.java
│       ├── DeviationDto.java
│       ├── DeviationTrendDto.java
│       ├── DeviationByActionDto.java
│       ├── DeviationResolutionDto.java
│       ├── IntelligenceSummaryDto.java
│       ├── EventVolumeSummaryDto.java
│       ├── ResourceTypeCountDto.java
│       ├── FacilityEventCountDto.java
│       ├── PractitionerEventCountDto.java
│       ├── SourceSystemCountDto.java
│       ├── EventVolumeTrendDto.java
│       ├── ProcessingQualityDto.java
│       ├── AtRiskHotspotDto.java
│       ├── RepeatDeviationPatientDto.java
│       └── DtoMapper.java

src/main/resources/
├── application.yml
└── application-docker.yml

src/test/java/org/openphc/cce/insights/           # Unit tests
src/integrationTest/java/org/openphc/cce/insights/ # Integration tests
```

**Total:** ~45 source files across 10 packages.

---

## 4. Data Access Patterns

### 4.1 Read-Only Design

All database access uses `@Transactional(readOnly = true)`. The Insights Service never performs `INSERT`, `UPDATE`, or `DELETE` operations. JPA entities can use `@Immutable` to enforce this at the Hibernate level.

### 4.2 Tables Queried

| Table | Owner | Queries Used For |
|---|---|---|
| `protocol_definition` | Compliance Service | Protocol metadata (name, version, canonical URL) |
| `protocol_instance` | Compliance Service | Patient enrollments, compliance rates, filtering by status/facility |
| `step_instance` | Compliance Service | Step states, timing, completion status, aggregation |
| `deviation` | Compliance Service | Deviation records, trends, counts by type |
| `event_log` | Compliance Service | Patient event history, timeline visualization, event volume analytics |

### 4.3 Key Query Patterns

**Protocol Compliance Summary:**
```sql
SELECT
  pd.url || '|' || pd.version AS protocol_canonical,
  COUNT(DISTINCT pi.id) AS total_enrollments,
  COUNT(DISTINCT CASE WHEN pi.status = 'COMPLETED' THEN pi.id END) AS completed,
  COUNT(DISTINCT CASE WHEN pi.status = 'ACTIVE' THEN pi.id END) AS active,
  AVG(
    CASE WHEN pi.status IN ('ACTIVE', 'COMPLETED') THEN
      (SELECT COUNT(*) FROM step_instance si WHERE si.protocol_instance_id = pi.id AND si.state = 'COMPLETED')::float /
      NULLIF((SELECT COUNT(*) FROM step_instance si WHERE si.protocol_instance_id = pi.id), 0)
    END
  ) AS avg_compliance_rate
FROM protocol_instance pi
JOIN protocol_definition pd ON pi.protocol_definition_id = pd.id
WHERE pd.id = :protocolDefinitionId
GROUP BY pd.url, pd.version;
```

**Facility Compliance Summary:**
```sql
SELECT
  el.facility_id,
  COUNT(DISTINCT pi.id) AS total_enrollments,
  COUNT(DISTINCT CASE WHEN si.state IN ('OVERDUE', 'MISSED') THEN pi.id END) AS with_deviations
FROM protocol_instance pi
JOIN step_instance si ON si.protocol_instance_id = pi.id
JOIN event_log el ON el.protocol_instance_id = pi.id
WHERE el.facility_id = :facilityId
  AND el.facility_id IS NOT NULL
GROUP BY el.facility_id;
```

**Event Volume by Resource Type:**
```sql
SELECT
  el.data->>'resourceType' AS resource_type,
  COUNT(*) AS event_count
FROM event_log el
WHERE el.processing_status != 'DUPLICATE'
  AND (:facilityId IS NULL OR el.facility_id = :facilityId)
  AND (:startDate IS NULL OR el.event_time >= :startDate)
  AND (:endDate IS NULL OR el.event_time <= :endDate)
GROUP BY el.data->>'resourceType'
ORDER BY event_count DESC;
```

**Event Volume by Facility:**
```sql
SELECT
  el.facility_id,
  el.data->>'resourceType' AS resource_type,
  COUNT(*) AS event_count
FROM event_log el
WHERE el.facility_id IS NOT NULL
  AND el.processing_status != 'DUPLICATE'
  AND (:startDate IS NULL OR el.event_time >= :startDate)
  AND (:endDate IS NULL OR el.event_time <= :endDate)
GROUP BY el.facility_id, el.data->>'resourceType'
ORDER BY el.facility_id, event_count DESC;
```

**Event Volume by Practitioner:**

Practitioner references are extracted from different JSONB paths depending on the FHIR resource type:

| Resource Type | JSONB Path | Example Value |
|---|---|---|
| Encounter | `data->'participant'->0->'individual'->'reference'` | `Practitioner/HLC-PRAC-2025-00005` |
| Observation | `data->'performer'->0->'reference'` | `Practitioner/HLC-PRAC-2025-00005` |
| Condition | `data->'asserter'->'reference'` | `Practitioner/f830114a-...` |
| MedicationRequest | `data->'requester'->'reference'` | `Practitioner/f830114a-...` |
| MedicationDispense | `data->'performer'->0->'actor'->'reference'` | `Practitioner/...` |
| MedicationAdministration | `data->'performer'->0->'actor'->'reference'` | `Practitioner/...` |
| ServiceRequest | `data->'requester'->'reference'` | `Practitioner/...` |
| Procedure | `data->'performer'->0->'actor'->'reference'` | `Practitioner/...` |
| Immunization | `data->'performer'->0->'actor'->'reference'` | `Practitioner/...` |

```sql
SELECT
  COALESCE(
    el.data->'participant'->0->'individual'->>'reference',
    el.data->'performer'->0->>'reference',
    el.data->'asserter'->>'reference',
    el.data->'requester'->>'reference',
    el.data->'performer'->0->'actor'->>'reference'
  ) AS practitioner_ref,
  el.data->>'resourceType' AS resource_type,
  COUNT(*) AS event_count
FROM event_log el
WHERE el.processing_status != 'DUPLICATE'
  AND (:facilityId IS NULL OR el.facility_id = :facilityId)
  AND (:startDate IS NULL OR el.event_time >= :startDate)
  AND (:endDate IS NULL OR el.event_time <= :endDate)
GROUP BY practitioner_ref, resource_type
HAVING practitioner_ref IS NOT NULL
ORDER BY event_count DESC;
```

**Event Volume by Source System:**
```sql
SELECT
  el.source,
  el.data->>'resourceType' AS resource_type,
  COUNT(*) AS event_count
FROM event_log el
WHERE el.processing_status != 'DUPLICATE'
  AND (:startDate IS NULL OR el.event_time >= :startDate)
  AND (:endDate IS NULL OR el.event_time <= :endDate)
GROUP BY el.source, el.data->>'resourceType'
ORDER BY el.source, event_count DESC;
```

**Deviation Trends:**
```sql
SELECT
  DATE_TRUNC(:interval, d.detected_at) AS period,
  d.deviation_type,
  COUNT(*) AS count
FROM deviation d
WHERE d.detected_at BETWEEN :startDate AND :endDate
GROUP BY period, d.deviation_type
ORDER BY period;
```

---

## 5. Phased Architecture

| Phase | Data Source | Caching | Trade-off |
|---|---|---|---|
| **Phase 1 (current)** | Compliance DB `cce_collector` (direct queries) | None | Simpler deployment; acceptable at low-to-moderate scale |
| **Phase 2 (future)** | Dedicated analytics DB (materialized views or CDC) | Redis | Query performance at scale; eventual consistency |

Phase 1 is appropriate for initial deployments. Phase 2 transition will be transparent to API consumers — same endpoints, same response schemas.

---

## 6. Error Handling

| Scenario | Response | HTTP Status |
|---|---|---|
| Resource not found | `{ "error": { "code": "NOT_FOUND", "message": "..." } }` | 404 |
| Invalid query parameters | `{ "error": { "code": "VALIDATION_ERROR", "message": "..." } }` | 400 |
| Database unreachable | `{ "error": { "code": "SERVICE_UNAVAILABLE", "message": "..." } }` | 503 |
| Unexpected error | `{ "error": { "code": "INTERNAL_ERROR", "message": "..." } }` | 500 |

---

## 7. Observability

### Metrics

| Metric | Type | Tags | Description |
|---|---|---|---|
| `cce.insights.request.duration` | Timer | `endpoint`, `status` | REST endpoint response time |
| `cce.insights.query.duration` | Timer | `query_type` | Database query execution time |
| `cce.insights.request.count` | Counter | `endpoint`, `status` | Request count per endpoint |

### Health Indicators

| Indicator | Details |
|---|---|
| `db` (auto) | PostgreSQL connectivity |
| `diskSpace` (auto) | Disk space availability |

---

## 8. Scaling & Deployment

- **Stateless:** No local state, no Kafka consumer groups — can be scaled horizontally without coordination.
- **Deployment:** 2+ instances behind a load balancer for high availability.
- **Database connection pool:** Size per instance should account for total instances × pool size ≤ PostgreSQL `max_connections` allocation for analytics.
- **Read replicas (future):** Phase 2 can point the Insights Service at a PostgreSQL read replica to eliminate any impact on the Compliance Service's write performance.

---

## 9. Additional Query Patterns

These are the SQL patterns for the protocol analytics, deviation analytics, facility ranking, event processing quality, and patient risk endpoints.

**Step Analytics (Timeliness Distribution):**
```sql
SELECT
  si.action_id,
  COUNT(*) AS total_instances,
  COUNT(CASE WHEN si.state = 'COMPLETED' THEN 1 END) AS completed_count,
  COUNT(CASE WHEN si.completion_status = 'EARLY' THEN 1 END) AS early_count,
  COUNT(CASE WHEN si.completion_status = 'ON_TIME' THEN 1 END) AS on_time_count,
  COUNT(CASE WHEN si.completion_status = 'LATE' THEN 1 END) AS late_count,
  COUNT(CASE WHEN si.state = 'OVERDUE' THEN 1 END) AS overdue_count,
  COUNT(CASE WHEN si.state = 'MISSED' THEN 1 END) AS missed_count,
  COUNT(CASE WHEN si.state = 'SKIPPED' THEN 1 END) AS skipped_count,
  AVG(EXTRACT(EPOCH FROM (si.completed_at - si.due_date)) / 86400.0)
    FILTER (WHERE si.state = 'COMPLETED' AND si.due_date IS NOT NULL) AS avg_days_to_complete,
  PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM (si.completed_at - si.due_date)) / 86400.0)
    FILTER (WHERE si.state = 'COMPLETED' AND si.due_date IS NOT NULL) AS median_days_to_complete
FROM step_instance si
JOIN protocol_instance pi ON si.protocol_instance_id = pi.id
WHERE pi.protocol_definition_id = :protocolDefinitionId
GROUP BY si.action_id;
```

**Completion Funnel:**
```sql
SELECT
  si.action_id,
  COUNT(DISTINCT pi.patient_id) AS reached_count,
  COUNT(DISTINCT CASE WHEN si.state = 'COMPLETED' THEN pi.patient_id END) AS completed_count
FROM step_instance si
JOIN protocol_instance pi ON si.protocol_instance_id = pi.id
WHERE pi.protocol_definition_id = :protocolDefinitionId
GROUP BY si.action_id;
```

**Protocol Outcome Distribution:**
```sql
SELECT
  pi.status,
  COUNT(*) AS count
FROM protocol_instance pi
WHERE pi.protocol_definition_id = :protocolDefinitionId
GROUP BY pi.status;
```

**Enrollment Trends:**
```sql
SELECT
  DATE_TRUNC(:interval, pi.enrolled_at) AS period,
  COUNT(*) AS enrollments
FROM protocol_instance pi
WHERE pi.protocol_definition_id = :protocolDefinitionId
  AND pi.enrolled_at BETWEEN :startDate AND :endDate
GROUP BY period
ORDER BY period;
```

**Facility Ranking (by compliance rate):**
```sql
SELECT
  el.facility_id,
  COUNT(DISTINCT pi.id) AS total_enrollments,
  AVG(
    (SELECT COUNT(*) FROM step_instance si2
     WHERE si2.protocol_instance_id = pi.id AND si2.state IN ('COMPLETED', 'SKIPPED'))::float /
    NULLIF((SELECT COUNT(*) FROM step_instance si3 WHERE si3.protocol_instance_id = pi.id), 0)
  ) AS compliance_rate,
  (SELECT COUNT(*) FROM deviation d
   JOIN protocol_instance pi2 ON d.protocol_instance_id = pi2.id
   JOIN event_log el2 ON el2.protocol_instance_id = pi2.id
   WHERE el2.facility_id = el.facility_id
     AND d.detected_at > NOW() - INTERVAL '30 days') AS active_deviations,
  COUNT(DISTINCT el.id) AS total_events
FROM protocol_instance pi
JOIN event_log el ON el.protocol_instance_id = pi.id
WHERE el.facility_id IS NOT NULL
GROUP BY el.facility_id
ORDER BY compliance_rate DESC;
```

**Deviations by Action:**
```sql
SELECT
  si.action_id,
  pi.protocol_definition_id,
  pi.protocol_canonical,
  COUNT(*) AS total_deviations,
  COUNT(CASE WHEN d.deviation_type = 'OVERDUE' THEN 1 END) AS overdue_count,
  COUNT(CASE WHEN d.deviation_type = 'MISSED' THEN 1 END) AS missed_count,
  COUNT(DISTINCT pi.patient_id) AS affected_patients
FROM deviation d
JOIN step_instance si ON d.step_instance_id = si.id
JOIN protocol_instance pi ON d.protocol_instance_id = pi.id
GROUP BY si.action_id, pi.protocol_definition_id, pi.protocol_canonical
ORDER BY total_deviations DESC;
```

**Deviation Resolution Rate:**
```sql
SELECT
  COUNT(*) FILTER (WHERE si.state = 'COMPLETED') AS resolved_count,
  COUNT(*) FILTER (WHERE si.state = 'MISSED') AS escalated_count,
  COUNT(*) AS total_overdue,
  AVG(EXTRACT(EPOCH FROM (si.completed_at - d.detected_at)) / 86400.0)
    FILTER (WHERE si.state = 'COMPLETED') AS avg_days_to_resolve
FROM deviation d
JOIN step_instance si ON d.step_instance_id = si.id
WHERE d.deviation_type = 'OVERDUE';
```

**Event Processing Quality:**
```sql
SELECT
  el.source,
  el.processing_status,
  COUNT(*) AS count
FROM event_log el
WHERE el.event_time BETWEEN :startDate AND :endDate
GROUP BY el.source, el.processing_status
ORDER BY el.source;
```

**At-Risk Patient Hotspots:**
```sql
SELECT
  el.facility_id,
  COUNT(DISTINCT pi.patient_id) AS total_patients,
  COUNT(DISTINCT pi.patient_id) FILTER (WHERE NOT EXISTS (
    SELECT 1 FROM step_instance si WHERE si.protocol_instance_id = pi.id AND si.state IN ('OVERDUE', 'MISSED')
  )) AS on_track_count,
  COUNT(DISTINCT pi.patient_id) FILTER (WHERE EXISTS (
    SELECT 1 FROM step_instance si WHERE si.protocol_instance_id = pi.id AND si.state = 'OVERDUE'
  ) AND NOT EXISTS (
    SELECT 1 FROM step_instance si WHERE si.protocol_instance_id = pi.id AND si.state = 'MISSED'
  )) AS at_risk_count,
  COUNT(DISTINCT pi.patient_id) FILTER (WHERE EXISTS (
    SELECT 1 FROM step_instance si WHERE si.protocol_instance_id = pi.id AND si.state = 'MISSED'
  )) AS non_compliant_count
FROM protocol_instance pi
JOIN event_log el ON el.protocol_instance_id = pi.id
WHERE pi.status = 'ACTIVE'
  AND el.facility_id IS NOT NULL
GROUP BY el.facility_id
ORDER BY non_compliant_count DESC;
```

**Repeat Deviation Patients:**
```sql
SELECT
  pi.patient_id,
  COUNT(*) AS total_deviations,
  COUNT(CASE WHEN d.deviation_type = 'OVERDUE' THEN 1 END) AS overdue_count,
  COUNT(CASE WHEN d.deviation_type = 'MISSED' THEN 1 END) AS missed_count,
  COUNT(DISTINCT pi.id) AS affected_protocols,
  COUNT(DISTINCT d.step_instance_id) AS affected_steps
FROM deviation d
JOIN protocol_instance pi ON d.protocol_instance_id = pi.id
GROUP BY pi.patient_id
HAVING COUNT(*) >= :minDeviations
ORDER BY total_deviations DESC;
```
