# Flow Diagrams

## 1. Request Flow: Gateway → Insights → Database

```mermaid
sequenceDiagram
    participant Client
    participant Gateway as CCE Gateway<br/>(8080)
    participant Insights as Insights Service<br/>(8084)
    participant DB as PostgreSQL<br/>(cce_collector)

    Client->>Gateway: GET /v1/compliance-summary?facility_id=FAC-001
    Note right of Gateway: Validate OAuth token<br/>Check scope: dashboard:read

    Gateway->>Insights: Forward request + headers<br/>X-User-Id, X-Facility-Id, X-Roles

    Insights->>DB: SELECT (aggregation query)
    DB-->>Insights: Result set

    Insights-->>Gateway: 200 OK { "data": {...} }
    Gateway-->>Client: 200 OK (passthrough)
```

## 2. Compliance Summary Aggregation Flow

```mermaid
flowchart TD
    A[GET /v1/compliance-summary] --> B{facility_id<br/>provided?}
    B -- Yes --> C[Filter by facility_id]
    B -- No --> D[All facilities]
    C --> E[Query protocol_instance + step_instance]
    D --> E

    E --> F[Count step_instance by state<br/>per protocol_instance]
    F --> G{Calculate adherence_rate<br/>completed / total}
    G --> H{adherence_rate >= 80%?}
    H -- Yes --> I[on_track]
    H -- No --> J{adherence_rate >= 50%?}
    J -- Yes --> K[at_risk]
    J -- No --> L[non_compliant]

    I --> M[Group by protocol_canonical]
    K --> M
    L --> M
    M --> N[Build ComplianceSummaryResponse]
    N --> O[Return { data: ... }]
```

## 3. Patient Timeline Query Flow

```mermaid
sequenceDiagram
    participant Controller as PatientController
    participant Service as PatientComplianceService
    participant PIRepo as ProtocolInstanceRepository
    participant SIRepo as StepInstanceRepository
    participant DB as PostgreSQL

    Controller->>Service: getPatientTimeline(patientId)
    Service->>PIRepo: findBySubject(patientId)
    PIRepo->>DB: SELECT * FROM protocol_instance<br/>WHERE subject = ?
    DB-->>PIRepo: protocol instances

    loop For each protocol instance
        Service->>SIRepo: findByProtocolInstanceId(piId)
        SIRepo->>DB: SELECT * FROM step_instance<br/>WHERE protocol_instance_id = ?<br/>ORDER BY scheduled_date ASC
        DB-->>SIRepo: step instances
    end

    Service->>Service: Build timeline events<br/>(steps + deviations)
    Service-->>Controller: PatientTimelineResponse
```

## 4. Deviation Trend Analysis Flow

```mermaid
flowchart TD
    A[GET /v1/deviations/trends] --> B[Parse query params:<br/>type, facility_id, from, to]
    B --> C[Query deviation table<br/>with date range + filters]
    C --> D[GROUP BY deviation_type,<br/>DATE_TRUNC period]

    D --> E{period param?}
    E -- day --> F[DATE_TRUNC 'day']
    E -- week --> G[DATE_TRUNC 'week']
    E -- month --> H[DATE_TRUNC 'month']

    F --> I[Build trend data points]
    G --> I
    H --> I

    I --> J[Return DeviationTrendsResponse<br/>{ period, count, type }]
```

## 5. Facility Compliance Overview Flow

```mermaid
flowchart TD
    A[GET /v1/compliance-summary/by-facility] --> B[Query protocol_instance<br/>JOIN event_log for facility_id]
    B --> C[For each facility]
    C --> D[Count total protocols]
    C --> E[Count active protocols]
    C --> F[Count completed protocols]
    C --> G[Join step_instance<br/>Calculate adherence]

    D --> H[Build FacilityComplianceDTO]
    E --> H
    F --> H
    G --> H

    H --> I[Sort by adherence_rate ASC<br/>worst-performing first]
    I --> J[Apply pagination]
    J --> K[Return paginated response]
```

## 6. Export Flow

```mermaid
sequenceDiagram
    participant Client
    participant Controller as ExportController
    participant Service as ExportService
    participant DB as PostgreSQL

    Client->>Controller: GET /v1/export?format=csv&type=deviations
    Controller->>Service: exportDeviations(filters, format)

    Service->>DB: Streaming query<br/>SELECT ... FROM deviation<br/>JOIN protocol_instance ...
    Note right of Service: Uses cursor-based<br/>streaming to avoid<br/>loading all rows

    loop Stream rows
        DB-->>Service: Row batch
        Service->>Service: Transform to CSV row
    end

    Service-->>Controller: StreamingResponseBody
    Controller-->>Client: 200 OK<br/>Content-Type: text/csv<br/>Content-Disposition: attachment
```

## 7. Event Volume Analytics Flow

### 7.1 Event Volume by Resource Type / Facility / Source

```mermaid
flowchart TD
    A[GET /v1/events/by-resource-type<br/>or /by-facility or /by-source] --> B[Parse query params:<br/>facilityId, source, startDate, endDate]
    B --> C[Query event_log table<br/>WHERE processing_status != DUPLICATE]

    C --> D{Group-by dimension?}
    D -- by-resource-type --> E["GROUP BY data->>'resourceType'"]
    D -- by-facility --> F[GROUP BY facility_id]
    D -- by-source --> G[GROUP BY source]

    E --> H[Calculate COUNT + percentage]
    F --> I[Calculate COUNT per facility<br/>+ resource type sub-groups]
    G --> J[Calculate COUNT per source<br/>+ resource type sub-groups]

    H --> K[Return EventVolumeSummary]
    I --> K
    J --> K
```

### 7.2 Event Volume by Practitioner (JSONB Extraction)

```mermaid
sequenceDiagram
    participant Controller as EventVolumeController
    participant Service as EventVolumeService
    participant DB as PostgreSQL

    Controller->>Service: getEventsByPractitioner(facilityId, resourceType, dateRange)

    Service->>DB: SELECT COALESCE(<br/>  data->'participant'->0->'individual'->>'reference',<br/>  data->'performer'->0->>'reference',<br/>  data->'asserter'->>'reference',<br/>  data->'requester'->>'reference',<br/>  data->'performer'->0->'actor'->>'reference'<br/>) AS practitioner_ref,<br/>COUNT(*) ...<br/>GROUP BY practitioner_ref

    Note right of DB: JSONB path varies<br/>by FHIR resource type.<br/>COALESCE tries all paths.

    DB-->>Service: practitioner_ref, resource_type, count rows

    Service->>Service: Filter NULL practitioner_ref<br/>Extract display name from JSONB<br/>Build DTOs

    Service-->>Controller: PractitionerEventCountDto[]
    Controller-->>Controller: Wrap in pagination envelope
```

### 7.3 Event Volume Trends

```mermaid
flowchart TD
    A[GET /v1/events/trends] --> B[Parse query params:<br/>interval, resourceType,<br/>facilityId, source, dateRange]
    B --> C[Query event_log table]
    C --> D{interval param?}
    D -- daily --> E["DATE_TRUNC('day', event_time)"]
    D -- weekly --> F["DATE_TRUNC('week', event_time)"]
    D -- monthly --> G["DATE_TRUNC('month', event_time)"]

    E --> H["GROUP BY period, data->>'resourceType'"]
    F --> H
    G --> H

    H --> I[Build trend data points<br/>with resource type breakdown]
    I --> J[Return EventVolumeTrendsResponse]
```

### 7.4 Event Volume Summary (Composite)

```mermaid
flowchart TD
    A[GET /v1/events/summary] --> B[Parse filters]

    B --> C[Query 1: Total + status breakdown<br/>GROUP BY processing_status]
    B --> D[Query 2: By resource type<br/>GROUP BY data resourceType]
    B --> E[Query 3: By facility<br/>GROUP BY facility_id]
    B --> F[Query 4: By source<br/>GROUP BY source]

    C --> G[Compose EventVolumeSummaryDto]
    D --> G
    E --> G
    F --> G

    G --> H[Return composite response]
```

## 8. Protocol Analytics Flow

### 8.1 Step Analytics

```mermaid
flowchart TD
    A[GET /v1/protocols/:id/step-analytics] --> B[Parse params:<br/>facilityId, startDate, endDate]
    B --> C[Query step_instance<br/>JOIN protocol_instance<br/>WHERE protocol_definition_id = :id]
    C --> D[GROUP BY action_id]
    D --> E[For each action_id]

    E --> F[COUNT by state<br/>COMPLETED, OVERDUE,<br/>MISSED, SKIPPED, PENDING]
    E --> G[COUNT by completion_status<br/>EARLY, ON_TIME, LATE]
    E --> H[AVG + PERCENTILE_CONT 0.5<br/>of completed_at - due_date]

    F --> I[Build StepAnalyticsDto]
    G --> I
    H --> I

    I --> J[Return step analytics response]
```

### 8.2 Completion Funnel

```mermaid
flowchart TD
    A[GET /v1/protocols/:id/completion-funnel] --> B[Parse params]
    B --> C[Query step_instance<br/>JOIN protocol_instance]
    C --> D[GROUP BY action_id]
    D --> E[For each action_id]

    E --> F[reached = COUNT DISTINCT<br/>patient_id with any state]
    E --> G[completed = COUNT DISTINCT<br/>patient_id with COMPLETED]

    F --> H["completion_rate =<br/>completed / reached"]
    G --> H
    H --> I["drop_off_rate =<br/>1 - completion_rate"]

    I --> J[Order by step_order<br/>from PlanDefinition]
    J --> K[Return funnel response]
```

### 8.3 Outcome Distribution

```mermaid
flowchart TD
    A[GET /v1/protocols/:id/outcome-distribution] --> B[Parse params]
    B --> C[Query protocol_instance<br/>WHERE protocol_definition_id = :id]
    C --> D[GROUP BY status]

    D --> E[ACTIVE count]
    D --> F[COMPLETED count]
    D --> G[WITHDRAWN count]
    D --> H[EXPIRED count]

    E --> I["percentage =<br/>(count / total) × 100"]
    F --> I
    G --> I
    H --> I

    I --> J[Return distribution response]
```

### 8.4 Enrollment Trends

```mermaid
flowchart TD
    A[GET /v1/protocols/:id/enrollment-trends] --> B[Parse params:<br/>interval, startDate, endDate]
    B --> C[Query protocol_instance<br/>WHERE protocol_definition_id = :id<br/>AND enrolled_at in range]

    C --> D{interval param?}
    D -- daily --> E["DATE_TRUNC('day', enrolled_at)"]
    D -- weekly --> F["DATE_TRUNC('week', enrolled_at)"]
    D -- monthly --> G["DATE_TRUNC('month', enrolled_at)"]

    E --> H[GROUP BY period<br/>COUNT enrollments]
    F --> H
    G --> H

    H --> I[Return enrollment trends response]
```

## 9. Facility Ranking Flow

```mermaid
sequenceDiagram
    participant Controller as FacilityRankingController
    participant Service as FacilityRankingService
    participant DB as PostgreSQL

    Controller->>Service: getRanking(rankBy, order, protocolId, dateRange)

    Service->>DB: SELECT facility_id,<br/>compliance_rate, deviations, events<br/>FROM protocol_instance<br/>JOIN event_log<br/>JOIN step_instance<br/>GROUP BY facility_id

    DB-->>Service: facility aggregation rows

    Service->>Service: Sort by rankBy metric<br/>(complianceRate / deviationCount / eventVolume)
    Service->>Service: Assign rank numbers
    Service->>Service: Apply pagination

    Service-->>Controller: FacilityRankingDto[]
```

## 10. Deviation Analytics Flow

### 10.1 Deviations by Action

```mermaid
flowchart TD
    A[GET /v1/deviations/by-action] --> B[Parse params:<br/>protocolDefinitionId, deviationType,<br/>facilityId, dateRange]
    B --> C[Query deviation<br/>JOIN step_instance<br/>JOIN protocol_instance]
    C --> D[GROUP BY action_id,<br/>protocol_definition_id]

    D --> E[COUNT total deviations]
    D --> F[COUNT OVERDUE vs MISSED]
    D --> G[COUNT DISTINCT patient_id<br/>= affected_patients]

    E --> H[Build DeviationByActionDto]
    F --> H
    G --> H

    H --> I[ORDER BY total_deviations DESC]
    I --> J[Return response]
```

### 10.2 Deviation Resolution Rate

```mermaid
flowchart TD
    A[GET /v1/deviations/resolution-rate] --> B[Parse params]
    B --> C[Query deviation<br/>WHERE deviation_type = OVERDUE<br/>JOIN step_instance]
    C --> D{Step final state?}

    D -- "state = COMPLETED" --> E[Resolved<br/>Patient recovered]
    D -- "state = MISSED" --> F[Escalated<br/>Unrecoverable]
    D -- "state = OVERDUE<br/>(still active)" --> G[Still pending]

    E --> H["resolution_rate =<br/>resolved / total_overdue"]
    F --> H
    E --> I["avg_days_to_resolve =<br/>AVG(completed_at − detected_at)"]

    H --> J[Group by protocol for breakdown]
    I --> J
    J --> K[Return resolution rate response]
```

## 11. Event Processing Quality Flow

```mermaid
flowchart TD
    A[GET /v1/events/processing-quality] --> B[Parse params:<br/>source, facilityId, dateRange]
    B --> C[Query event_log<br/>WHERE event_time in range]
    C --> D[GROUP BY source,<br/>processing_status]

    D --> E[MATCHED count]
    D --> F[ZERO_MATCH count]
    D --> G[DUPLICATE count]

    E --> H["Calculate percentages<br/>per source"]
    F --> H
    G --> H

    H --> I{High ZERO_MATCH rate?}
    I -- "> 20%" --> J["⚠ Signals misconfigured<br/>emitters or gaps in protocols"]
    I -- "≤ 20%" --> K[Normal]

    J --> L[Return processing quality response]
    K --> L
```

## 12. Patient Risk Analytics Flow

### 12.1 At-Risk Hotspots

```mermaid
sequenceDiagram
    participant Controller as PatientRiskController
    participant Service as PatientRiskService
    participant DB as PostgreSQL

    Controller->>Service: getAtRiskHotspots(protocolId, dateRange)

    Service->>DB: SELECT facility_id,<br/>COUNT patients by compliance category<br/>FROM protocol_instance<br/>JOIN event_log<br/>WHERE status = 'ACTIVE'<br/>GROUP BY facility_id

    Note right of DB: Per patient:<br/>on_track = no OVERDUE/MISSED<br/>at_risk = has OVERDUE, no MISSED<br/>non_compliant = has MISSED

    DB-->>Service: facility, on_track, at_risk, non_compliant counts

    Service->>Service: Calculate percentages
    Service->>Service: Order by non_compliant DESC
    Service->>Service: Apply pagination

    Service-->>Controller: AtRiskHotspotDto[]
```

### 12.2 Repeat Deviations

```mermaid
flowchart TD
    A[GET /v1/patients/repeat-deviations] --> B[Parse params:<br/>minDeviations, facilityId,<br/>protocolDefinitionId, dateRange]
    B --> C[Query deviation<br/>JOIN protocol_instance]
    C --> D[GROUP BY patient_id]
    D --> E["HAVING COUNT(*) >= :minDeviations"]

    E --> F[For each patient]
    F --> G[COUNT overdue + missed]
    F --> H[COUNT DISTINCT protocols]
    F --> I[COUNT DISTINCT steps]
    F --> J[Fetch recent deviation details]

    G --> K[Build RepeatDeviationPatientDto]
    H --> K
    I --> K
    J --> K

    K --> L[ORDER BY total_deviations DESC]
    L --> M[Apply pagination]
    M --> N[Return response]
```
