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
