# CCE Insights Service — API Reference

All endpoints are accessed through the **CCE Gateway Service** (not directly by external callers). The Gateway validates OAuth tokens and enforces the `dashboard:read` scope. The Insights Service receives pre-authenticated requests with `user_id`, `facility_id`, `roles` forwarded as HTTP headers.

> **Naming:** This service is referred to as "Analytics Service" in the CCE Solution Design v0.3. Implementation uses **Insights Service** (`cce-insights-service`).

> **`complianceRate` scale:** every `complianceRate` (and `overallComplianceRate`) field
> across this API is a **percentage, 0–100, rounded to one decimal** (e.g. `72.0` means
> 72%) — never a 0–1 fraction. This applies uniformly across compliance summaries,
> patient lists, facility ranking, practitioner ranking, and the dashboard.

---

## 0. Global filters

Every clinical-metric endpoint accepts the shared filters the UI sends from the header:

| Param | Applies to | Notes |
|---|---|---|
| `startDate`, `endDate` | all metric endpoints | clinical `event_time` window (ISO-8601). |
| `facilityId` | most metric endpoints | scope to a single facility. |
| `district` | **all clinical pages** (see below) | scope to every facility in that district. Blank/absent = all facilities. |

**Global District filter.** `district` (district name, e.g. `Gasabo`) resolves to the set of
facilities in that district (from the `facility` reference table) and scopes the result to those
facilities — behaving as "the union of the district's facilities". It is honored by:

- Facility ranking, activity-summary, activity-detail, adoption (`/facilities/*`)
- Dashboard referrals (`/dashboard/referrals`) and patient referrals (`/patients/referrals/received-by-hie`)
- Compliance summaries (`/protocols[/{id}]/compliance-summary`, `/protocols/{id}/patients`) and step-analytics
- Deviations (`/deviations`, `/deviations/kpis`, `/deviations/trends`, `/deviations/by-action`)
- Event volume (`/events/summary`, `/events/trends`, `/events/by-resource-type`, `/events/by-facility`)

Not district-scoped by design: the **Ingestion** pipeline endpoints (`/ingestion/*`) — pipeline
health, not clinical-event metrics — and the cumulative `/events/kpis`.

The district option list is served by **`GET /v1/insights/lookups/districts`** → `{ "data": ["Gasabo", "Kicukiro", ...] }` (distinct, non-blank, sorted). `GET /v1/insights/lookups/facilities` now also returns each facility's `district`.

---

## 1. Compliance Summaries

### 1.1 GET `/v1/insights/protocols/{protocolDefinitionId}/compliance-summary`

Aggregate compliance metrics for a specific protocol across all enrolled patients.

**Required Scope:** `dashboard:read`

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `protocolDefinitionId` | UUID | Protocol definition ID |

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `facilityId` | String | — | Filter by facility FOSA ID |
| `startDate` | ISO 8601 | — | Start of date range — the cohort scope depends on `dateFilterMode` |
| `endDate` | ISO 8601 | — | End of date range — also used as the snapshot date for step metrics |
| `dateFilterMode` | String | `enrollment` | `enrollment` — cohort = patients **enrolled in** [startDate, endDate]; `eventTime` (RI-36, "Clinical Event Date") — cohort = patients of this protocol with a protocol-**MATCHED** inbound event (`compliance_event_logs.processing_status='MATCHED'`) at clinical **`event_time`** in range. Every card (patients, transactions, deviations) derives from the selected cohort. This is the clinical event clock, **not** the system `updated_at` "activity" detail log. Note: `compliance_event_logs` carries no protocol link, so per-protocol `eventTime` = "enrolled in this protocol AND has a matched event in range". |

**Response: `200 OK`**

```json
{
  "data": {
    "protocolDefinitionId": "550e8400-e29b-41d4-a716-446655440000",
    "protocolCanonical": "http://openphc.org/fhir/PlanDefinition/anc-high-risk|2.1",
    "totalEnrollments": 248,
    "statusBreakdown": {
      "active": 180,
      "completed": 52,
      "withdrawn": 8,
      "expired": 8
    },
    "complianceRate": 72.0,
    "compliantPatients": 178,
    "stepMetrics": {
      "totalSteps": 1240,
      "completed": 680,
      "onTime": 520,
      "late": 120,
      "early": 40,
      "overdue": 180,
      "missed": 90,
      "due": 20,
      "pending": 290
    },
    "deviationCount": 270,
    "deviationBreakdown": {
      "overdue": 180,
      "missed": 90
    }
  }
}
```

---

### 1.2 GET `/v1/insights/facilities/{facilityId}/compliance-summary`

Facility-level compliance metrics across all protocols.

**Required Scope:** `dashboard:read`

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `facilityId` | String | Facility FOSA ID (e.g., `0002`) |

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `protocolDefinitionId` | UUID | — | Accepted but **not currently applied** — the response is never filtered by protocol (all protocols always included in `protocolBreakdown`) |
| `startDate` | ISO 8601 | — | When set, `totalPatients` counts distinct patients enrolled at this facility in the period |
| `endDate` | ISO 8601 | — | End of date range |

**Response: `200 OK`**

```json
{
  "data": {
    "facilityId": "0002",
    "totalPatients": 156,
    "totalEnrollments": 312,
    "overallComplianceRate": 68.0,
    "protocolBreakdown": [
      {
        "protocolDefinitionId": "550e8400-e29b-41d4-a716-446655440000",
        "protocolCanonical": "http://openphc.org/fhir/PlanDefinition/anc-high-risk|2.1",
        "enrollments": 89,
        "complianceRate": 74.0,
        "activeDeviations": 12
      },
      {
        "protocolDefinitionId": "660e8400-e29b-41d4-a716-446655440000",
        "protocolCanonical": "http://openphc.org/fhir/PlanDefinition/child-immunization|1.0",
        "enrollments": 223,
        "complianceRate": 65.0,
        "activeDeviations": 34
      }
    ]
  }
}
```

---

### 1.3 GET `/v1/insights/protocols/{protocolDefinitionId}/patients`

List patients enrolled in a protocol, filterable by compliance status. Results are
deduplicated to **one row per patient** (most recent enrollment in the filtered period).

**Required Scope:** `dashboard:read`

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `status` | String | — | Filter: `on_track` (compliant), `non_compliant` |
| `facilityId` | String | — | Restrict to patients whose latest facility (via `mv_patient_facility_latest`) matches |
| `patientId` | String | — | Substring search on patient ID |
| `startDate` | ISO 8601 | — | When set, narrows cohort based on `dateFilterMode` |
| `endDate` | ISO 8601 | — | End of date range |
| `dateFilterMode` | String | `enrollment` | `enrollment` — cohort = patients enrolled in [startDate, endDate]; `eventTime` (RI-36, "Clinical Event Date") — cohort = patients with a protocol-**MATCHED** inbound event (`compliance_event_logs.processing_status='MATCHED'`) at clinical **`event_time`** in range, regardless of enrollment date. Clinical event clock — **not** the system `updated_at` "activity" detail log. (Was previously step `updated_at`; now clinical event time, consistent with the Dashboard card / Deviations page.) |
| `limit` | Integer | `15` | Page size (max 200) |
| `cursor` | String | — | Pagination cursor |

**Response: `200 OK`**

```json
{
  "data": [
    {
      "patientId": "260225-0002-5501",
      "protocolInstanceId": "660e8400-e29b-41d4-a716-446655440001",
      "protocolCanonical": "http://openphc.org/fhir/PlanDefinition/anc-high-risk|2.1",
      "enrolledAt": "2026-01-15T10:00:00Z",
      "status": "active",
      "complianceRate": 50.0,
      "complianceCategory": "at_risk",
      "stepsCompleted": 3,
      "totalSteps": 6,
      "activeDeviations": 1,
      "facilityId": "0002"
    }
  ],
  "pagination": {
    "limit": 50,
    "next_cursor": "eyJpZCI6MTIzfQ==",
    "has_more": true
  }
}
```

**Compliance Categories** (deviation-based — matches dashboard):
| Category | Definition |
|---|---|
| `on_track` | No deviation records for the patient in the selected period |
| `non_compliant` | At least one deviation record (overdue, missed, or order violation) |

> The `complianceRate` field is step completion percentage and is independent of category. The `at_risk` category is no longer returned by this endpoint.

---

## 2. Patient Compliance

### 2.1 GET `/v1/insights/patients/{patientId}/compliance-timeline`

Full compliance timeline for a patient across all enrolled protocols. Combines event history and step status into a chronological view.

**Required Scope:** `dashboard:read`

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `startDate` | ISO 8601 | — | Filters the `timeline` events array by `timestamp ≥ startDate`. The `journey` (per-protocol-action step list) remains the full set so structure is not hidden when narrowing the range. |
| `endDate` | ISO 8601 | — | Filters the `timeline` events array by `timestamp ≤ endDate`. |

**Response: `200 OK`**

```json
{
  "data": {
    "patientId": "260225-0002-5501",
    "protocols": [
      {
        "protocolInstanceId": "660e8400-e29b-41d4-a716-446655440001",
        "protocolCanonical": "http://openphc.org/fhir/PlanDefinition/anc-high-risk|2.1",
        "status": "active",
        "complianceRate": 50.0,
        "timeline": [
          {
            "timestamp": "2026-01-15T10:00:00Z",
            "type": "enrollment",
            "description": "Enrolled in ANC High-Risk v2.1"
          },
          {
            "timestamp": "2026-01-20T09:30:00Z",
            "type": "step_completed",
            "actionId": "anc-visit-1",
            "completionStatus": "on_time",
            "source": "ebuzima/kigali-south"
          },
          {
            "timestamp": "2026-02-20T00:00:00Z",
            "type": "step_overdue",
            "actionId": "anc-visit-2",
            "daysOverdue": 5
          }
        ]
      }
    ]
  }
}
```

### 2.2 GET `/v1/insights/patients/{patientId}/protocol-tracking`

List all protocol instances for a patient.

**Required Scope:** `dashboard:read`

**Response: `200 OK`**

```json
{
  "data": [
    {
      "protocolInstanceId": "660e8400-e29b-41d4-a716-446655440001",
      "protocolCanonical": "http://openphc.org/fhir/PlanDefinition/anc-high-risk|2.1",
      "protocolTitle": "ANC High-Risk Protocol",
      "relatedArtifact": [
        {
          "type": "documentation",
          "label": "Reference Guideline",
          "display": "External clinical guideline",
          "url": "https://example.org/guideline.pdf"
        },
        {
          "type": "thumbnail",
          "label": "Thumbnail Image",
          "display": "Protocol Thumbnail Image",
          "url": "https://example.org/thumbnail.png"
        }
      ],
      "enrolledAt": "2026-01-15T10:00:00Z",
      "status": "active",
      "complianceRate": 50.0,
      "stepsCompleted": 3,
      "totalSteps": 6
    }
  ]
}
```

### 2.3 GET `/v1/insights/patients/{patientId}/protocol-tracking/{protocolInstanceId}`

Detailed tracking for a specific protocol instance with all step instances.

**Required Scope:** `dashboard:read`

**Response: `200 OK`**

```json
{
  "data": {
    "protocolInstanceId": "660e8400-e29b-41d4-a716-446655440001",
    "patientId": "260225-0002-5501",
    "protocolCanonical": "http://openphc.org/fhir/PlanDefinition/anc-high-risk|2.1",
    "status": "active",
    "enrolledAt": "2026-01-15T10:00:00Z",
    "complianceRate": 50.0,
    "steps": [
      {
        "stepInstanceId": "770e8400-e29b-41d4-a716-446655440001",
        "actionId": "anc-visit-1",
        "state": "COMPLETED",
        "dueDate": "2026-01-20T00:00:00Z",
        "completedAt": "2026-01-20T09:30:00Z",
        "completionStatus": "ON_TIME",
        "completedBySource": "ebuzima/kigali-south"
      },
      {
        "stepInstanceId": "770e8400-e29b-41d4-a716-446655440002",
        "actionId": "anc-visit-2",
        "state": "OVERDUE",
        "dueDate": "2026-02-15T00:00:00Z",
        "overdueDate": "2026-02-20T00:00:00Z",
        "daysOverdue": 5
      },
      {
        "stepInstanceId": "770e8400-e29b-41d4-a716-446655440003",
        "actionId": "anc-visit-3",
        "state": "PENDING",
        "dueDate": "2026-03-10T00:00:00Z"
      }
    ],
    "deviations": [
      {
        "deviationId": "880e8400-e29b-41d4-a716-446655440001",
        "stepInstanceId": "770e8400-e29b-41d4-a716-446655440002",
        "deviationType": "OVERDUE",
        "detectedAt": "2026-02-20T00:00:05Z"
      }
    ]
  }
}
```

---

### 2.4 GET `/v1/insights/patients/{patientId}/events`

Raw clinical events (from `event_log`) for a specific patient, ordered by event time descending. Shows the FHIR events that triggered step completions and protocol matching.

**Required Scope:** `dashboard:read`

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `patientId` | String | Patient identifier (e.g., `260225-0002-5501`) |

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `resourceType` | String | — | Filter by FHIR resource type (e.g., `Encounter`) |
| `source` | String | — | Filter by source system |
| `startDate` | ISO 8601 | — | Events after this time |
| `endDate` | ISO 8601 | — | Events before this time |
| `limit` | Integer | `50` | Page size (max 200) |

**Response: `200 OK`**

```json
{
  "data": [
    {
      "eventId": "990e8400-e29b-41d4-a716-446655440001",
      "cloudeventsId": "ce-001",
      "type": "org.openphc.fhir.Encounter.create",
      "eventTime": "2026-01-20T09:30:00Z",
      "source": "ebuzima/kigali-south",
      "resourceType": "Encounter",
      "processingStatus": "MATCHED",
      "facilityId": "0002",
      "protocolInstanceId": "660e8400-e29b-41d4-a716-446655440001",
      "actionId": "anc-visit-1",
      "matchedStepInstanceId": "770e8400-e29b-41d4-a716-446655440001"
    },
    {
      "eventId": "990e8400-e29b-41d4-a716-446655440002",
      "cloudeventsId": "ce-002",
      "type": "org.openphc.fhir.Observation.create",
      "eventTime": "2026-01-20T09:35:00Z",
      "source": "ebuzima/kigali-south",
      "resourceType": "Observation",
      "processingStatus": "MATCHED",
      "facilityId": "0002",
      "protocolInstanceId": "660e8400-e29b-41d4-a716-446655440001",
      "actionId": "anc-visit-1",
      "matchedStepInstanceId": "770e8400-e29b-41d4-a716-446655440001"
    }
  ]
}
```

---

### 2.5 GET `/v1/insights/patients/{patientId}/deviations`

All deviations for a patient across all protocol enrollments, ordered by detection time descending. Provides a cross-protocol deviation history for targeted outreach.

**Required Scope:** `dashboard:read`

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `patientId` | String | Patient identifier |

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `deviationType` | String | — | Filter: `overdue`, `missed` |
| `startDate` | ISO 8601 | — | Deviations detected after |
| `endDate` | ISO 8601 | — | Deviations detected before |

**Response: `200 OK`**

```json
{
  "data": [
    {
      "deviationId": "880e8400-e29b-41d4-a716-446655440001",
      "protocolInstanceId": "660e8400-e29b-41d4-a716-446655440002",
      "protocolCanonical": "http://openphc.org/fhir/PlanDefinition/anc-high-risk|2.1",
      "stepInstanceId": "770e8400-e29b-41d4-a716-446655440005",
      "deviationType": "OVERDUE",
      "detectedAt": "2026-02-25T00:00:05Z"
    }
  ]
}
```

---

### 2.6 GET `/v1/insights/patients/{patientId}/intelligence-deliveries`

All `intelligence_delivery` records for a patient — the notification/escalation delivery history for that patient's protocol actions. Matches `patientId` bare or prefixed as `Patient/{patientId}`.

**Required Scope:** `dashboard:read`

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `patientId` | String | Patient identifier |

**Response: `200 OK`**

```json
{
  "data": [
    {
      "id": "aa0e8400-e29b-41d4-a716-446655440001",
      "actionType": "NOTIFY",
      "status": "DELIVERED",
      "severity": "warning",
      "destination": "rhie-mediator",
      "protocolCanonical": "http://openphc.org/fhir/PlanDefinition/anc-high-risk|2.1",
      "actionId": "anc-visit-2-referral-escalation",
      "attemptCount": 1,
      "createdAt": "2026-02-20T00:00:10Z",
      "deliveredAt": "2026-02-20T00:00:12Z"
    }
  ]
}
```

> Response entries are plain maps (no dedicated DTO) with the fields shown above.

---

### 2.7 GET `/v1/insights/patients/referrals/received-by-hie`

Patients behind the Patients-page **"Referrals Received by HIE"** indicator (RI-44) — distinct patients with a referral received by HIE in the selected period, keyed by **clinical `event_time`**. A "referral" is an ACCEPTED inbound event that is a prod `TRANSFER_ENCOUNTER` Encounter or a dev/demo event that completed a Referral step (same definition as `mv_daily_referral_kpis`). One row per patient; the referral date uses the matched Referral step's `completed_at` (clinical occurrence), falling back to `event_time` for transfer encounters with no step.

**Required Scope:** `dashboard:read`

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `startDate` | ISO 8601 | — | Referral clinical date on/after (unbounded if omitted) |
| `endDate` | ISO 8601 | — | Referral clinical date on/before (unbounded if omitted) |

**Response: `200 OK`**

```json
{
  "data": [
    {
      "patientId": "9991234567890",
      "facilityId": "1302",
      "facilityName": "NCD Upazila",
      "lastReferral": "2026-06-25T08:17:51Z",
      "referralCount": 6,
      "matchedCount": 6
    }
  ]
}
```

| Field | Type | Description |
|-------|------|-------------|
| `patientId` | String | Patient (subject) |
| `facilityId` / `facilityName` | String | Reporting (origin) facility of the patient's referral |
| `lastReferral` | ISO 8601 | Most recent referral's clinical date (UTC) |
| `referralCount` | Long | Referral events for this patient in range |
| `matchedCount` | Long | Of those, referrals matched to (completing) a Referral step |

> The **"Created Referrals"**, **"Failed Referrals"**, and **"Referral Rate"** indicators on the Patients page are UI placeholders (no endpoint) pending a product definition.

---

## 3. Deviations & Intelligence

### 3.1 GET `/v1/insights/deviations`

List deviations with filtering, sorting, and pagination.

**Required Scope:** `dashboard:read`

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `deviationType` | String | — | Filter: `overdue`, `missed` |
| `facilityId` | String | — | Filter by facility |
| `protocolDefinitionId` | UUID | — | Filter by protocol |
| `startDate` | ISO 8601 | — | Deviation detected after |
| `endDate` | ISO 8601 | — | Deviation detected before |
| `limit` | Integer | `50` | Max rows returned |

> No `sort` or `cursor` param exists — results are not paginated (no `pagination` block in the response); `limit` simply caps the row count.

**Response: `200 OK`**

```json
{
  "data": [
    {
      "deviationId": "880e8400-e29b-41d4-a716-446655440001",
      "patientId": "260225-0002-5501",
      "protocolInstanceId": "660e8400-e29b-41d4-a716-446655440001",
      "protocolCanonical": "http://openphc.org/fhir/PlanDefinition/anc-high-risk|2.1",
      "stepInstanceId": "770e8400-e29b-41d4-a716-446655440002",
      "actionId": "anc-visit-2",
      "deviationType": "OVERDUE",
      "detectedAt": "2026-02-20T00:00:05Z",
      "occurredAt": "2026-02-20T00:00:00Z",
      "facilityId": "0002"
    }
  ]
}
```

> `occurredAt` is the clinical occurrence date (when the deviation actually happened,
> derived from the linked step's engine-computed dates); `detectedAt` is when the system
> flagged it (processing time). They usually match but can differ for backfilled data.

### 3.2 GET `/v1/insights/deviations/trends`

Deviation trends aggregated by time period.

**Required Scope:** `dashboard:read`

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `interval` | String | `weekly` | Aggregation: `daily`, `weekly`, `monthly` |
| `facilityId` | String | — | Filter by facility |
| `protocolDefinitionId` | UUID | — | Filter by protocol |
| `startDate` | ISO 8601 | 30 days ago | Trend start date |
| `endDate` | ISO 8601 | now | Trend end date |

**Response: `200 OK`**

```json
{
  "data": {
    "interval": "weekly",
    "trends": [
      {
        "period": "2026-02-17",
        "overdue": 12,
        "missed": 3,
        "orderViolation": 0,
        "total": 15
      },
      {
        "period": "2026-02-24",
        "overdue": 8,
        "missed": 5,
        "orderViolation": 0,
        "total": 13
      },
      {
        "period": "2026-03-03",
        "overdue": 15,
        "missed": 2,
        "orderViolation": 0,
        "total": 17
      }
    ]
  }
}
```

### 3.3 GET `/v1/insights/intelligence/summary`

Intelligence delivery summary — counts by status, action type, destination, severity.

**Required Scope:** `dashboard:read`

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `protocolDefinitionId` | UUID | — | Restrict to deliveries associated with the selected protocol's canonical URL |
| `startDate` | ISO 8601 | — | Start of date range |
| `endDate` | ISO 8601 | — | End of date range |

**Response: `200 OK`**

```json
{
  "data": {
    "total": 270,
    "delivered": 180,
    "failed": 30,
    "pending": 60,
    "successRate": 66.7,
    "avgLatencySeconds": 2.4,
    "byStatus": [...],
    "byActionType": [...],
    "bySeverity": [...],
    "byDestination": [...],
    "activeAdaptors": [...]
  }
}
```

> **Note:** the deviation-focused intelligence aggregator now lives at
> `/v1/insights/deviations/intelligence-summary`. The two endpoints serve different
> purposes — this one tracks delivery success of intelligence actions to receiver
> adaptors; the deviation endpoint summarises deviation counts in time windows.

---

### 3.4 GET `/v1/insights/deviations/kpis`

Total deviation counts, optionally scoped by protocol, facility, and detection date range. Counts distinct rows in the `deviations` table directly — this **supersedes summing `mv_daily_deviation_kpis` snapshot rows**, which inflated totals by re-counting the same active deviation on every day it appeared in the snapshot.

**Required Scope:** `dashboard:read`

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `protocolDefinitionId` | UUID | — | Filter by protocol |
| `facilityId` | String | — | Filter by facility (via `mv_patient_facility_latest`) |
| `startDate` | ISO 8601 (`OffsetDateTime`) | — | Deviation detected after |
| `endDate` | ISO 8601 (`OffsetDateTime`) | — | Deviation detected before |

**Response: `200 OK`**

```json
{
  "data": {
    "totalDeviations": 270,
    "overdueCount": 180,
    "missedCount": 82,
    "orderViolationCount": 8
  }
}
```

---

## 4. Event Volume & Activity Metrics

Event volume endpoints provide aggregate counts of clinical events received by CCE, discoverable by FHIR `resourceType`, facility (location), practitioner, and source system. These metrics are derived from the Compliance Service's `event_log` table. Duplicate events (`processing_status = 'DUPLICATE'`) are excluded from all counts.

### 4.1 GET `/v1/insights/events/summary`

High-level event volume summary with breakdowns by resource type, facility, and processing status.

**Required Scope:** `dashboard:read`

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `facilityId` | String | — | Filter by facility FOSA ID |
| `source` | String | — | Filter by source system (e.g., `rhie-mediator`) |
| `startDate` | ISO 8601 | 30 days ago | Start of date range |
| `endDate` | ISO 8601 | now | End of date range |

**Response: `200 OK`**

```json
{
  "data": {
    "totalEvents": 12480,
    "processingStatusBreakdown": {
      "matched": { "count": 9820, "percentage": 78.7 },
      "zeroMatch": { "count": 2540, "percentage": 20.4 },
      "duplicate": { "count": 120, "percentage": 0.9 }
    },
    "byResourceType": [
      { "resourceType": "Encounter", "count": 4200 },
      { "resourceType": "Observation", "count": 3850 },
      { "resourceType": "Condition", "count": 1600 },
      { "resourceType": "MedicationRequest", "count": 1200 },
      { "resourceType": "ServiceRequest", "count": 820 },
      { "resourceType": "Immunization", "count": 450 },
      { "resourceType": "Procedure", "count": 360 }
    ],
    "byFacility": [
      { "facilityId": "0002", "count": 3200 },
      { "facilityId": "0015", "count": 2800 },
      { "facilityId": "0008", "count": 2100 }
    ],
    "bySource": [
      { "source": "rhie-mediator", "count": 8400 },
      { "source": "ebuzima/kigali-south", "count": 4080 }
    ],
    "pipelineLossCount": 30
  }
}
```

---

### 4.2 GET `/v1/insights/events/trends`

Event volume trends over time, grouped by aggregation interval.

**Required Scope:** `dashboard:read`

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `interval` | String | `weekly` | Aggregation: `daily`, `weekly`, `monthly` |
| `resourceType` | String | — | Accepted but **not currently applied** — the parameter is read but never passed to the service, so it has no filtering effect |
| `facilityId` | String | — | Filter by facility FOSA ID |
| `source` | String | — | Filter by source system |
| `startDate` | ISO 8601 | 30 days ago | Start of date range |
| `endDate` | ISO 8601 | now | End of date range |

**Response: `200 OK`**

```json
{
  "data": {
    "interval": "weekly",
    "trends": [
      {
        "period": "2026-03-10",
        "total": 1840,
        "byResourceType": {
          "Encounter": 620,
          "Observation": 540,
          "Condition": 280,
          "MedicationRequest": 200,
          "ServiceRequest": 120,
          "Immunization": 80
        }
      },
      {
        "period": "2026-03-17",
        "total": 2050,
        "byResourceType": {
          "Encounter": 710,
          "Observation": 580,
          "Condition": 310,
          "MedicationRequest": 230,
          "ServiceRequest": 130,
          "Immunization": 90
        }
      }
    ]
  }
}
```

---

### 4.3 GET `/v1/insights/events/by-resource-type`

Event counts grouped by FHIR `resourceType` with optional facility and date range filtering.

**Required Scope:** `dashboard:read`

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `facilityId` | String | — | Filter by facility FOSA ID |
| `source` | String | — | Filter by source system |
| `startDate` | ISO 8601 | — | Start of date range |
| `endDate` | ISO 8601 | — | End of date range |

**Response: `200 OK`**

```json
{
  "data": [
    {
      "resourceType": "Encounter",
      "count": 4200,
      "percentage": 33.7
    },
    {
      "resourceType": "Observation",
      "count": 3850,
      "percentage": 30.8
    },
    {
      "resourceType": "Condition",
      "count": 1600,
      "percentage": 12.8
    },
    {
      "resourceType": "MedicationRequest",
      "count": 1200,
      "percentage": 9.6
    },
    {
      "resourceType": "ServiceRequest",
      "count": 820,
      "percentage": 6.6
    },
    {
      "resourceType": "Immunization",
      "count": 450,
      "percentage": 3.6
    },
    {
      "resourceType": "Procedure",
      "count": 360,
      "percentage": 2.9
    }
  ]
}
```

---

### 4.4 GET `/v1/insights/events/by-facility`

Event counts grouped by facility, with resource type breakdown per facility.

**Required Scope:** `dashboard:read`

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `resourceType` | String | — | Filter by FHIR resource type |
| `startDate` | ISO 8601 | — | Start of date range |
| `endDate` | ISO 8601 | — | End of date range |

> No `limit` or `cursor` param exists — the endpoint returns every in-scope facility, no pagination.

**Response: `200 OK`**

```json
{
  "data": [
    {
      "facilityId": "0002",
      "totalEvents": 3200,
      "byResourceType": [
        { "resourceType": "Encounter", "count": 1100 },
        { "resourceType": "Observation", "count": 950 },
        { "resourceType": "Condition", "count": 480 },
        { "resourceType": "MedicationRequest", "count": 380 },
        { "resourceType": "ServiceRequest", "count": 290 }
      ]
    },
    {
      "facilityId": "0015",
      "totalEvents": 2800,
      "byResourceType": [
        { "resourceType": "Encounter", "count": 980 },
        { "resourceType": "Observation", "count": 870 },
        { "resourceType": "Condition", "count": 410 },
        { "resourceType": "MedicationRequest", "count": 320 },
        { "resourceType": "ServiceRequest", "count": 220 }
      ]
    }
  ]
}
```

---

### 4.5 GET `/v1/insights/events/kpis`

All-time, unfiltered event processing totals from `mv_daily_event_kpis` — the cumulative counterpart to the date-filtered `/events/summary`.

**Required Scope:** `dashboard:read`

**Query Parameters:** none

**Response: `200 OK`**

```json
{
  "data": {
    "totalEvents": 128400,
    "matchedCount": 102300,
    "zeroMatchCount": 24600,
    "duplicateCount": 1500,
    "matchedRatePct": 79.7,
    "zeroMatchRatePct": 19.2,
    "pipelineLossCount": 310
  }
}
```

---

## 5. Exports

### 5.1 GET `/v1/insights/exports/compliance-report`

Export compliance data in CSV or JSON format.

**Required Scope:** `dashboard:read`

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `format` | String | `json` | Export format: `json`, `csv` |
| `protocolDefinitionId` | UUID | — | Filter by protocol |
| `facilityId` | String | — | Filter by facility |
| `startDate` | ISO 8601 | 30 days ago | Data range start |
| `endDate` | ISO 8601 | now | Data range end |

**Response: `200 OK`**

Content-Type varies by format:
- `application/json` for JSON exports
- `text/csv` for CSV exports

---

## 6. Error Responses

All error responses follow the standard CCE envelope:

```json
{
  "error": {
    "code": "NOT_FOUND",
    "message": "Protocol definition not found: 550e8400-e29b-41d4-a716-446655440000"
  }
}
```

| HTTP Status | Code | When |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Invalid query parameters (bad date format, invalid UUID, etc.) |
| 404 | `NOT_FOUND` | Requested resource does not exist |
| 503 | `SERVICE_UNAVAILABLE` | Database unreachable |
| 500 | `INTERNAL_ERROR` | Unexpected server error |

---

## 7. Actuator Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/actuator/health` | Aggregate health status |
| GET | `/actuator/health/liveness` | Kubernetes liveness probe |
| GET | `/actuator/health/readiness` | Kubernetes readiness probe |
| GET | `/actuator/prometheus` | Prometheus metrics scrape |
| GET | `/actuator/info` | Build/app info |
| GET | `/actuator/metrics` | Micrometer metric names/values |

---

## 8. Protocol Analytics

### 8.1 GET `/v1/insights/protocols/{protocolDefinitionId}/step-analytics`

Per-step completion rates, average time-to-complete, and timeliness distribution (`EARLY` / `ON_TIME` / `LATE`) for each protocol action. Identifies which steps in a care pathway are consistently delayed.

**Required Scope:** `dashboard:read`

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `protocolDefinitionId` | UUID | Protocol definition ID |

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `facilityId` | String | — | Narrow to patients tied to this facility via `mv_patient_facility_latest` |
| `startDate` | ISO 8601 | — | Narrows the cohort to enrollments enrolled in [start, end] (step rows of those enrollments are reported); step states still reflect current state, not state-in-period |
| `endDate` | ISO 8601 | — | End of enrollment window |

**Response: `200 OK`**

```json
{
  "data": {
    "protocolDefinitionId": "550e8400-e29b-41d4-a716-446655440000",
    "protocolCanonical": "http://openphc.org/fhir/PlanDefinition/anc-high-risk|2.1",
    "steps": [
      {
        "actionId": "anc-visit-1",
        "totalInstances": 248,
        "completedCount": 210,
        "completionRate": 0.85,
        "timelinessDistribution": {
          "early": 40,
          "onTime": 145,
          "late": 25
        },
        "overdueCount": 20,
        "missedCount": 8,
        "skippedCount": 10,
        "pendingCount": 0,
        "avgDaysToComplete": 1.2,
        "medianDaysToComplete": 0.5
      },
      {
        "actionId": "anc-visit-2",
        "totalInstances": 248,
        "completedCount": 160,
        "completionRate": 0.65,
        "timelinessDistribution": {
          "early": 15,
          "onTime": 100,
          "late": 45
        },
        "overdueCount": 45,
        "missedCount": 28,
        "skippedCount": 5,
        "pendingCount": 10,
        "avgDaysToComplete": 3.8,
        "medianDaysToComplete": 2.0
      }
    ]
  }
}
```

**Computed fields:**
- `completionRate` = `completedCount / totalInstances`
- `avgDaysToComplete` = AVG of `(completed_at - due_date)` in days, only for completed steps with a `due_date`
- `medianDaysToComplete` = Median of the same set (using ClickHouse `medianIf(...)`)

---

### 8.2 GET `/v1/insights/protocols/{protocolDefinitionId}/completion-funnel`

Drop-off rates at each sequential step — percentage of enrolled patients who complete each step. Shows where in the care pathway patients are lost.

**Required Scope:** `dashboard:read`

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `protocolDefinitionId` | UUID | Protocol definition ID |

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `facilityId` | String | — | Restricts the cohort to patients at this facility (via `mv_patient_facility_latest`) |
| `startDate` | ISO 8601 | — | Narrow to enrollments enrolled in [start, end] |
| `endDate` | ISO 8601 | — | End of enrollment window |

**Response: `200 OK`**

```json
{
  "data": {
    "protocolDefinitionId": "550e8400-e29b-41d4-a716-446655440000",
    "protocolCanonical": "http://openphc.org/fhir/PlanDefinition/anc-high-risk|2.1",
    "totalEnrollments": 248,
    "funnel": [
      {
        "actionId": "enrollment",
        "stepOrder": 1,
        "reachedCount": 248,
        "completedCount": 240,
        "completionRate": 0.97,
        "dropOffRate": 0.03
      },
      {
        "actionId": "anc-visit-1",
        "stepOrder": 2,
        "reachedCount": 240,
        "completedCount": 210,
        "completionRate": 0.88,
        "dropOffRate": 0.12
      },
      {
        "actionId": "anc-visit-2",
        "stepOrder": 3,
        "reachedCount": 210,
        "completedCount": 160,
        "completionRate": 0.76,
        "dropOffRate": 0.24
      },
      {
        "actionId": "anc-visit-3",
        "stepOrder": 4,
        "reachedCount": 160,
        "completedCount": 105,
        "completionRate": 0.66,
        "dropOffRate": 0.34
      }
    ]
  }
}
```

**Computed fields:**
- `reachedCount` = patients with a step instance for this action (any state)
- `completedCount` = patients with `state = 'COMPLETED'` for this action
- `completionRate` = `completedCount / reachedCount`
- `dropOffRate` = `1 - (completedCount / reachedCount)` (percentage lost at this step)
- `stepOrder` = derived from `PlanDefinition.action[]` ordering and `relatedAction` dependencies

---

### 8.3 GET `/v1/insights/protocols/{protocolDefinitionId}/outcome-distribution`

Percentage of protocol instances ending in each terminal status. Measures overall program effectiveness.

**Required Scope:** `dashboard:read`

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `protocolDefinitionId` | UUID | Protocol definition ID |

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `facilityId` | String | — | Restricts the cohort to patients at this facility (via `mv_patient_facility_latest`) |
| `startDate` | ISO 8601 | — | Narrow to enrollments enrolled in [start, end] |
| `endDate` | ISO 8601 | — | End of enrollment window |

**Response: `200 OK`**

```json
{
  "data": {
    "protocolDefinitionId": "550e8400-e29b-41d4-a716-446655440000",
    "protocolCanonical": "http://openphc.org/fhir/PlanDefinition/anc-high-risk|2.1",
    "totalInstances": 248,
    "distribution": {
      "active": { "count": 180, "percentage": 72.6 },
      "completed": { "count": 52, "percentage": 21.0 },
      "expired": { "count": 8, "percentage": 3.2 },
      "withdrawn": { "count": 8, "percentage": 3.2 }
    }
  }
}
```

---

### 8.4 GET `/v1/insights/protocols/{protocolDefinitionId}/enrollment-trends`

New protocol enrollments over time, with optional facility breakdown. Tracks program adoption and seasonal demand.

**Required Scope:** `dashboard:read`

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `protocolDefinitionId` | UUID | Protocol definition ID |

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `interval` | String | `weekly` | Aggregation: `daily`, `weekly`, `monthly` |
| `facilityId` | String | — | When set, restricts to enrollments whose patient is at this facility (via `mv_patient_facility_latest`) |
| `startDate` | ISO 8601 | 90 days ago | Trend start date |
| `endDate` | ISO 8601 | now | Trend end date |

**Response: `200 OK`**

```json
{
  "data": {
    "protocolDefinitionId": "550e8400-e29b-41d4-a716-446655440000",
    "interval": "weekly",
    "trends": [
      { "period": "2026-01-06", "enrollments": 18 },
      { "period": "2026-01-13", "enrollments": 22 },
      { "period": "2026-01-20", "enrollments": 25 },
      { "period": "2026-01-27", "enrollments": 15 },
      { "period": "2026-02-03", "enrollments": 30 }
    ]
  }
}
```

---

## 9. Facility Analytics

### 9.1 GET `/v1/insights/facilities/ranking`

Facility leaderboard ranked by compliance rate, deviation count, or event volume. Enables management oversight and targeted interventions.

**Required Scope:** `dashboard:read`

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `protocolDefinitionId` | UUID | — | Filter by protocol (ranks within that protocol) |
| `facilityId` | String | — | Narrow the ranking to a single facility (still returned with full rank metadata) |
| `rankBy` | String | `complianceRate` | Ranking metric: `complianceRate`, `deviationCount`, `eventVolume` |
| `order` | String | `desc` | `asc` (worst first) or `desc` (best first) |
| `startDate` | ISO 8601 | — | Date range start |
| `endDate` | ISO 8601 | — | Date range end |
| `limit` | Integer | `50` | Max rows returned |
| `cursor` | String | — | Accepted but **not currently applied** — never forwarded to the service; only `limit` has any effect. No `pagination` block in the response. |

> **Field semantics:** `totalEvents` is sourced from `inbound_event_logs` (status =
> `ACCEPTED`) in the period, intersected with the facility reference list — matches the
> Active Facilities tile and the Events → By Facility table. Earlier versions read
> `event_count` from the compliance MV which under-counted facilities with accepted but
> unmatched events.
>
> **RI-36 — compliance cohort (per row):** `compliantPatients` / `nonCompliantPatients` /
> `complianceRate` are computed live from the patient cohort that is **active in the period**
> — patients whose events are *considered by a protocol* (ACCEPTED inbound events with
> `event_time` in range whose `cloudevents_id` matched a protocol,
> `compliance_event_logs.processing_status='MATCHED'`), **not** `enrolled_at`. This is the
> same cohort as the Dashboard "Service Compliance" card, so the breakdown reconciles with it
> by definition. `nonCompliantPatients` uses the deviation's **clinical occurrence date** (not
> `detected_at`). Patients are attributed to their **current** facility via
> `mv_patient_facility_latest`; a tracked patient with no resolved facility there is counted in
> the country card but cannot appear in any facility row, so the breakdown's tracked total can
> be lower than the card's country total (a facility-attribution gap, not double-counting).
> `totalEnrollments` remains the all-time enrolled count and is unaffected.

**Response: `200 OK`**

```json
{
  "data": [
    {
      "rank": 1,
      "facilityId": "0015",
      "facilityName": "Muhima HC",
      "district": "Gasabo",
      "totalEnrollments": 89,
      "compliantPatients": 73,
      "nonCompliantPatients": 16,
      "complianceRate": 82.0,
      "activeDeviations": 5,
      "totalEvents": 2800,
      "patientsFromHIE": 41
    },
    {
      "rank": 2,
      "facilityId": "0002",
      "facilityName": "Kigali South HC",
      "district": "Nyarugenge",
      "totalEnrollments": 156,
      "compliantPatients": 115,
      "nonCompliantPatients": 41,
      "complianceRate": 74.0,
      "activeDeviations": 12,
      "totalEvents": 3200,
      "patientsFromHIE": 58
    },
    {
      "rank": 3,
      "facilityId": "0008",
      "facilityName": "Nyamirambo HC",
      "district": "Kicukiro",
      "totalEnrollments": 62,
      "compliantPatients": 36,
      "nonCompliantPatients": 26,
      "complianceRate": 58.0,
      "activeDeviations": 22,
      "totalEvents": 2100,
      "patientsFromHIE": 19
    }
  ]
}
```

---

### 9.2 GET `/v1/insights/facilities/activity-summary`

Active/inactive facility summary tile. **Active = facility with ≥1 `ACCEPTED` inbound event**
by clinical `event_time` in the period — same definition as eBuzima Adoption's actual-visits
count, so a facility with recorded activity is never "Inactive" just because that activity
hasn't been matched to a protocol step. (RI-62: previously also required the event to be
protocol-matched, `compliance_event_logs.processing_status='MATCHED'`; see data dictionary
§3.13a for why that was reverted.)

**Required Scope:** `dashboard:read`

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `facilityId` | String | — | Single-facility tile: reports 1 in-scope facility, active/inactive per whether it had an accepted event in the period |
| `startDate` | ISO 8601 | — | Start of date range — counts facilities with ≥1 accepted event (event_time) in the period |
| `endDate` | ISO 8601 | — | End of date range |

> Without any filters, falls back to today's active-facility count.
> "Inactive" = no accepted events at all in the period.

**Response: `200 OK`**

```json
{
  "data": {
    "totalInScope": 25,
    "activeFacilities": 19,
    "inactiveFacilities": 6,
    "activeFacilityRate": 76.0
  }
}
```

---

### 9.3 GET `/v1/insights/facilities/activity-detail`

Drill-down behind the Active/Inactive facility cards (RI-29): one row per in-scope facility, flagged active/inactive for the selected range using the same definition as `/activity-summary`. The UI partitions rows by `active`; counts reconcile with the summary tile.

**Required Scope:** `dashboard:read`

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `startDate` | ISO 8601 | today | Start of date range |
| `endDate` | ISO 8601 | today | End of date range |

**Response: `200 OK`**

```json
{
  "data": [
    {
      "facilityId": "0002",
      "facilityName": "Kigali South HC",
      "district": "Kigali",
      "lastActivity": "2026-03-14",
      "active": true
    },
    {
      "facilityId": "0031",
      "facilityName": "Kabuga HC",
      "district": "Gasabo",
      "lastActivity": "2026-01-02",
      "active": false
    }
  ]
}
```

> `lastActivity` is the most recent event day up to `endDate` (no lower bound), so an
> inactive facility that transmitted before the window still shows its true last-seen
> date rather than a blank; it is `null` only if the facility was never active up to
> `endDate`. Sort order is district ↑, facility name ↑, then most-recent activity first
> (applied by the service, not the database).

---

## 10. Deviation Analytics

### 10.1 GET `/v1/insights/deviations/by-action`

Most commonly deviated-from protocol steps, grouped by `actionId`. Identifies systemic bottlenecks in care delivery.

**Required Scope:** `dashboard:read`

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `protocolDefinitionId` | UUID | — | Filter by protocol |
| `facilityId` | String | — | **RI-49** — scope to a single facility (`mv_daily_deviation_kpis.facility_id`) |
| `district` | String | — | Global district filter (blank = all) |
| `startDate` | ISO 8601 (`OffsetDateTime`) | — | Deviations detected after |
| `endDate` | ISO 8601 (`OffsetDateTime`) | — | Deviations detected before |

> No `deviationType` or `limit` param exists on this endpoint — every in-scope action is
> returned (sorted by `totalDeviations` desc), no pagination.

**Response: `200 OK`**

```json
{
  "data": [
    {
      "actionId": "lab-result-review",
      "protocolDefinitionId": "550e8400-e29b-41d4-a716-446655440000",
      "protocolCanonical": "http://openphc.org/fhir/PlanDefinition/anc-high-risk|2.1",
      "totalDeviations": 85,
      "overdueCount": 62,
      "missedCount": 23,
      "orderViolationCount": 0,
      "affectedPatients": 72
    },
    {
      "actionId": "anc-visit-3",
      "protocolDefinitionId": "550e8400-e29b-41d4-a716-446655440000",
      "protocolCanonical": "http://openphc.org/fhir/PlanDefinition/anc-high-risk|2.1",
      "totalDeviations": 58,
      "overdueCount": 40,
      "missedCount": 18,
      "orderViolationCount": 0,
      "affectedPatients": 55
    }
  ]
}
```

---

### 10.2 GET `/v1/insights/deviations/resolution-rate`

Percentage of `OVERDUE` steps that eventually reach `COMPLETED` (recovered) vs. those that progress to `MISSED` (unrecoverable). Measures the system's ability to recover from compliance delays.

**Required Scope:** `dashboard:read`

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `protocolDefinitionId` | UUID | — | Filter by protocol |
| `startDate` | ISO 8601 (`OffsetDateTime`) | — | Deviations detected after |
| `endDate` | ISO 8601 (`OffsetDateTime`) | — | Deviations detected before |

> No `facilityId` param exists on this endpoint.

**Response: `200 OK`**

```json
{
  "data": {
    "totalOverdueDeviations": 180,
    "resolved": {
      "count": 120,
      "percentage": 66.7,
      "avgDaysToResolve": 4.2
    },
    "escalatedToMissed": {
      "count": 60,
      "percentage": 33.3
    },
    "byProtocol": [
      {
        "protocolDefinitionId": "550e8400-e29b-41d4-a716-446655440000",
        "protocolCanonical": "http://openphc.org/fhir/PlanDefinition/anc-high-risk|2.1",
        "totalOverdue": 95,
        "resolvedCount": 68,
        "resolutionRate": 0.72,
        "escalatedCount": 27
      }
    ]
  }
}
```

**How resolution is determined:**
- A step instance that had `deviation_type = 'OVERDUE'` but later reached `state = 'COMPLETED'` is **resolved**.
- A step instance that had `deviation_type = 'OVERDUE'` and later received a `deviation_type = 'MISSED'` is **escalated**.
- `avgDaysToResolve` = AVG of `(completed_at - deviation.detected_at)` in days for resolved overdue steps.

---

## 11. Patient Risk Analytics

### 11.1 GET `/v1/insights/patients/at-risk-hotspots`

Concentration of `at_risk` and `non_compliant` patients by facility. Directs field supervision and outreach resources to the facilities that need them most.

**Required Scope:** `dashboard:read`

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `protocolDefinitionId` | UUID | — | Restrict to patients enrolled in this protocol |
| `startDate` | ISO 8601 (`OffsetDateTime`) | — | Restrict the cohort to enrollments enrolled in [start, end] |
| `endDate` | ISO 8601 (`OffsetDateTime`) | — | End of enrollment window |

> `limit` and `cursor` params are accepted but **not currently applied** — every in-scope
> facility is always returned, no pagination.

**Response: `200 OK`**

```json
{
  "data": [
    {
      "facilityId": "0008",
      "totalPatients": 62,
      "onTrack": { "count": 18, "percentage": 29.0 },
      "atRisk": { "count": 24, "percentage": 38.7 },
      "nonCompliant": { "count": 20, "percentage": 32.3 }
    },
    {
      "facilityId": "0002",
      "totalPatients": 156,
      "onTrack": { "count": 95, "percentage": 60.9 },
      "atRisk": { "count": 38, "percentage": 24.4 },
      "nonCompliant": { "count": 23, "percentage": 14.7 }
    }
  ]
}
```

**Compliance categories per patient** (computed):
| Category | Condition |
|---|---|
| `on_track` | No `OVERDUE` or `MISSED` step instances across all active protocol enrollments |
| `at_risk` | At least one `OVERDUE` step instance, no `MISSED` |
| `non_compliant` | At least one `MISSED` step instance |

> **Note:** A patient's compliance category is determined across **all active protocol instances** at the facility. If a patient is enrolled in two protocols and has a `MISSED` step in one, they are classified as `non_compliant` at that facility.

---

### 11.2 GET `/v1/insights/patients/repeat-deviations`

Patients with deviations across multiple protocols or multiple steps within the same protocol. Identifies patients who need targeted outreach.

**Required Scope:** `dashboard:read`

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `minDeviations` | Integer | `3` | Minimum deviation count to include |
| `facilityId` | String | — | Filter by facility |
| `protocolDefinitionId` | UUID | — | Filter by protocol |
| `startDate` | ISO 8601 (`OffsetDateTime`) | — | Deviations detected after |
| `endDate` | ISO 8601 (`OffsetDateTime`) | — | Deviations detected before |

> `limit` and `cursor` params are accepted but **not currently applied** — every
> qualifying patient is always returned, no pagination.

**Response: `200 OK`**

```json
{
  "data": [
    {
      "patientId": "260225-0002-5501",
      "totalDeviations": 7,
      "overdueCount": 4,
      "missedCount": 3,
      "affectedProtocols": 2,
      "affectedSteps": 5,
      "facilityId": "0002",
      "deviations": [
        {
          "protocolCanonical": "http://openphc.org/fhir/PlanDefinition/anc-high-risk|2.1",
          "actionId": "anc-visit-2",
          "deviationType": "OVERDUE",
          "detectedAt": "2026-02-20T00:00:05Z"
        },
        {
          "protocolCanonical": "http://openphc.org/fhir/PlanDefinition/anc-high-risk|2.1",
          "actionId": "anc-visit-3",
          "deviationType": "MISSED",
          "detectedAt": "2026-03-15T00:00:05Z"
        }
      ]
    }
  ]
}
```

---

## 12. Adoption Metrics

Per-facility e-Buzima adoption tracking against the agreed expected-visit baseline. Backed by `mv_daily_adoption_kpis` and the static `facility` reference list (managed by programme staff via the compliance service).

### 12.1 GET `/v1/insights/facilities/adoption`

Per-facility e-Buzima adoption KPIs, sorted by `adoptionRate` desc.

**Required Scope:** `dashboard:read`

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `facilityId` | String | — | Narrow to a single facility (consistent with the global filter elsewhere) |
| `startDate` | ISO 8601 (`LocalDate`) | — | Start of date range — with `endDate`, aggregates across the range using calendar-day averages |
| `endDate` | ISO 8601 (`LocalDate`) | — | End of date range |

> Without date params, returns today's single-day snapshot (`expectedVisits` = the daily
> baseline). With `startDate`+`endDate` (**RI-33**), the counts are **period totals**:
> `expectedVisits = expected_per_day × calendar_days`, `actualVisits = SUM(actual_patients)`
> over the range, `reportingGap = expectedVisits − actualVisits`, and
> `adoptionRate = actualVisits / expectedVisits × 100`. See data-dictionary §3.13b for the
> full formula, including the zero-baseline handling.

**Response: `200 OK`** (example: a 10-day period)

```json
{
  "data": [
    {
      "facilityId": "0031",
      "facilityName": "Kabuga HC",
      "expectedVisits": 120,
      "actualVisits": 30,
      "adoptionRate": 25.0,
      "reportingGap": 90
    },
    {
      "facilityId": "0002",
      "facilityName": "Kigali South HC",
      "expectedVisits": 100,
      "actualVisits": 90,
      "adoptionRate": 90.0,
      "reportingGap": 10
    }
  ]
}
```

> `adoptionRate` is `0.0` (never a vacuous `100.0`) whenever a facility has both no
> expected baseline and no actual visits, and also whenever it has no adoption row at
> all for the period (regardless of whether it has an expected baseline).

---

### 12.2 GET `/v1/insights/facilities/reference`

The agreed facility list with expected patient volumes, sourced from `facility` (managed by programme staff via the compliance service). Used by admin screens to view/verify the adoption baseline.

**Required Scope:** `dashboard:read`

**Query Parameters:** none

**Response: `200 OK`**

```json
{
  "data": [
    { "facilityId": "0002", "facilityName": "Kigali South HC", "expectedVisitsPerDay": 10 },
    { "facilityId": "0031", "facilityName": "Kabuga HC", "expectedVisitsPerDay": 12 }
  ]
}
```

---

## 13. Ingestion Analytics

Metrics derived from the `inbound_event` table (owned by the Collector Service). These endpoints provide visibility into the full ingestion pipeline — from HTTP receipt to Kafka publication — including rejected events, duplicates, and pipeline loss that are invisible to compliance-level metrics.

> **Data Source:** `inbound_event` table (Collector Service). Unlike sections 4 and 11 which use `event_log` (compliance-matched events only), these endpoints see **every event received** by the platform.

### 13.1 GET `/v1/insights/ingestion/funnel`

Ingestion pipeline status breakdown. Shows how many events were received, accepted, rejected, and deduplicated, with optional time-series trends.

**Required Scope:** `dashboard:read`

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `facilityId` | String | — | Filter by facility FOSA ID |
| `source` | String | — | Filter by source system |
| `startDate` | ISO 8601 | — | Filter by `received_at` start |
| `endDate` | ISO 8601 | — | Filter by `received_at` end |
| `interval` | String | — | If provided, includes time-series trends. Values: `daily`, `weekly`, `monthly` |

**Response: `200 OK`**

```json
{
  "data": {
    "totalReceived": 15000,
    "accepted": 13200,
    "rejected": 1200,
    "duplicate": 600,
    "acceptanceRate": 88.0,
    "rejectionRate": 8.0,
    "duplicateRate": 4.0,
    "breakdown": [
      { "status": "ACCEPTED", "count": 13200, "percentage": 88.0 },
      { "status": "REJECTED", "count": 1200, "percentage": 8.0 },
      { "status": "DUPLICATE", "count": 600, "percentage": 4.0 }
    ],
    "trends": [
      { "period": "2026-03-01", "byStatus": { "ACCEPTED": 4200, "REJECTED": 380, "DUPLICATE": 190 }, "total": 4770 },
      { "period": "2026-03-08", "byStatus": { "ACCEPTED": 4500, "REJECTED": 410, "DUPLICATE": 205 }, "total": 5115 }
    ]
  }
}
```

---

### 13.2 GET `/v1/insights/ingestion/rejections`

Rejection reason analytics — breakdown by `rejection_reason` (from `RejectionReason` enum) with per-source detail.

**Required Scope:** `dashboard:read`

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `facilityId` | String | — | Filter by facility FOSA ID |
| `source` | String | — | Filter by source system |
| `startDate` | ISO 8601 | — | Filter by `received_at` start |
| `endDate` | ISO 8601 | — | Filter by `received_at` end |

**Response: `200 OK`**

```json
{
  "data": {
    "totalRejected": 1200,
    "byReason": [
      { "reason": "INVALID_FHIR", "count": 480, "percentage": 40.0 },
      { "reason": "MISSING_SUBJECT", "count": 300, "percentage": 25.0 },
      { "reason": "INVALID_ENVELOPE", "count": 180, "percentage": 15.0 },
      { "reason": "PAYLOAD_TOO_LARGE", "count": 120, "percentage": 10.0 },
      { "reason": "DESERIALIZATION_ERROR", "count": 72, "percentage": 6.0 },
      { "reason": "UNSUPPORTED_CONTENT_TYPE", "count": 48, "percentage": 4.0 }
    ],
    "bySource": [
      {
        "source": "rhie-mediator",
        "totalEvents": 9200,
        "rejectedEvents": 520,
        "rejectionRate": 5.7,
        "topReasons": [
          { "reason": "INVALID_FHIR", "count": 210, "percentage": 40.4 },
          { "reason": "MISSING_SUBJECT", "count": 150, "percentage": 28.8 }
        ]
      },
      {
        "source": "ebuzima/kigali-south",
        "totalEvents": 4350,
        "rejectedEvents": 680,
        "rejectionRate": 15.6,
        "topReasons": [
          { "reason": "INVALID_FHIR", "count": 270, "percentage": 39.7 },
          { "reason": "INVALID_ENVELOPE", "count": 165, "percentage": 24.3 }
        ]
      }
    ]
  }
}
```

**Rejection Reasons (from Collector `RejectionReason` enum):**

| Reason | Description |
|--------|-------------|
| `INVALID_ENVELOPE` | Missing or invalid CloudEvents required fields |
| `INVALID_FHIR` | FHIR R4 payload failed structural validation |
| `INVALID_JSON` | Non-FHIR JSON payload is not valid JSON or is empty |
| `UNSUPPORTED_CONTENT_TYPE` | `datacontenttype` is not `application/fhir+json` or `application/json` |
| `DUPLICATE` | Duplicate `(id, source)` detected within lookback window |
| `MISSING_SUBJECT` | `subject` field missing (required by CCE for patient routing) |
| `PAYLOAD_TOO_LARGE` | Request body exceeds max-payload-size |
| `DESERIALIZATION_ERROR` | Request body could not be parsed as JSON |
| `KAFKA_PUBLISH_FAILURE` | Kafka broker unavailable or publish timed out |
| `INTERNAL_ERROR` | Unexpected failure during post-persist processing |

---

### 13.3 GET `/v1/insights/ingestion/source-quality`

Source data quality scorecard — per-source acceptance, rejection, and duplicate rates. Ranks sources by reliability.

**Required Scope:** `dashboard:read`

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `facilityId` | String | — | Filter by facility FOSA ID |
| `startDate` | ISO 8601 | — | Filter by `received_at` start |
| `endDate` | ISO 8601 | — | Filter by `received_at` end |

**Response: `200 OK`**

```json
{
  "data": {
    "sources": [
      {
        "source": "rhie-mediator",
        "totalEvents": 9200,
        "accepted": 8400,
        "rejected": 520,
        "duplicate": 280,
        "acceptanceRate": 91.3,
        "rejectionRate": 5.7,
        "duplicateRate": 3.0
      },
      {
        "source": "ebuzima/kigali-south",
        "totalEvents": 4350,
        "accepted": 4080,
        "rejected": 180,
        "duplicate": 90,
        "acceptanceRate": 93.8,
        "rejectionRate": 4.1,
        "duplicateRate": 2.1
      }
    ]
  }
}
```

---

### 13.4 GET `/v1/insights/ingestion/pipeline-loss`

Detects events that were ACCEPTED by the Collector (published to Kafka) but never appeared in the Compliance Service's `event_log`. Indicates events lost in Kafka transit or dropped during compliance processing.

**Required Scope:** `dashboard:read`

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `facilityId` | String | — | Filter by facility FOSA ID |
| `startDate` | ISO 8601 | — | Filter by `received_at` start |
| `endDate` | ISO 8601 | — | Filter by `received_at` end |

**Response: `200 OK`**

```json
{
  "data": {
    "totalAcceptedByCollector": 12480,
    "totalInComplianceEventLog": 12450,
    "lostEvents": 30,
    "lossRate": 0.2,
    "bySource": [
      { "source": "rhie-mediator", "lostEvents": 18 },
      { "source": "ebuzima/kigali-south", "lostEvents": 12 }
    ]
  }
}
```

**How it works:** Joins `inbound_event` (where `status = 'ACCEPTED'`) with `event_log` on `(cloudevents_id, source)`. Events in the first table with no match in the second are considered "lost" in the pipeline. A non-zero `lossRate` warrants investigation of Kafka consumer lag, compliance service errors, or dead-letter queues.

---

## 14. Lookup Endpoints

Lookup endpoints provide dropdown/filter data for the Analytics UI dashboard. All responses are cached with the `lookups` cache tier (60-minute default TTL).

---

### 14.1 GET `/v1/insights/lookups/protocols`

Returns all protocol definitions for use in dropdown filters.

**Required Scope:** `dashboard:read`

**Query Parameters:** none

**Response: `200 OK`**

```json
{
  "data": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440001",
      "url": "https://fhir.openphc.org/PlanDefinition/anc-contact-schedule",
      "version": "1.0.0",
      "canonical": "https://fhir.openphc.org/PlanDefinition/anc-contact-schedule|1.0.0",
      "status": "active",
      "title": "ANC Contact Schedule"
    }
  ]
}
```

> There is no `name` field. `title` is extracted from the JSONB definition's
> `title` attribute, falling back to the URL's last path segment when absent.
> `canonical` is `url + "|" + version` (not a stored column).

---

### 14.2 GET `/v1/insights/lookups/facilities`

Returns the facility reference list (id + name), sourced from `facility`.

**Required Scope:** `dashboard:read`

**Response: `200 OK`**

```json
{
  "data": [
    { "id": "FAC-KGL-001", "name": "Kigali South HC" },
    { "id": "FAC-KGL-002", "name": "Muhima HC" },
    { "id": "FAC-HYE-003", "name": "Huye District HC" }
  ]
}
```

---

### 14.3 GET `/v1/insights/lookups/practitioners`

Returns distinct practitioner references from event data.

**Required Scope:** `dashboard:read`

**Response: `200 OK`**

```json
{
  "data": [
    "Practitioner/HLC-PRAC-2025-00005",
    "Practitioner/HLC-PRAC-2025-00012"
  ]
}
```

---

### 14.4 GET `/v1/insights/lookups/sources`

Returns distinct source system identifiers from inbound event data.

**Required Scope:** `dashboard:read`

**Response: `200 OK`**

```json
{
  "data": [
    "rhie-mediator",
    "ebuzima/kigali-south"
  ]
}
```

---

### 14.5 GET `/v1/insights/lookups/patients`

Returns distinct patient IDs from protocol instances.

**Required Scope:** `dashboard:read`

**Query Parameters:** none

**Response: `200 OK`**

```json
{
  "data": [
    "Patient/260225-0002-5501",
    "Patient/260225-0003-6612"
  ]
}
```

---

## 15. Dashboard

### 15.1 GET `/v1/insights/dashboard/overview`

Top-line HIE transmission and deviation KPIs, plus the top/bottom 3 facilities by compliance rate. Backs the Dashboard's primary landing tiles.

**Required Scope:** `dashboard:read`

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `facilityId` | String | — | Filter by facility |
| `district` | String | — | **RI-53** — filter by district (scopes the patient counts to that district's facilities) |
| `startDate` | ISO 8601 (`OffsetDateTime`) | — | Start of date range (scoped by `event_time`) |
| `endDate` | ISO 8601 (`OffsetDateTime`) | — | End of date range (scoped by `event_time`) |

> **RI-53:** the Dashboard **"Patients Received by HIE"** indicator reads `patientsReceivedHIE` —
> distinct **protocol-tracked** patients (`uniq(subject)` over ACCEPTED inbound events **matched to a
> protocol**, `compliance_event_logs.processing_status = 'MATCHED'`) — **not** a raw source-filtered
> count. It uses the shared matched-cohort query, scoped by **`event_time`**, facility and **`district`**
> (resolved to the district's facilities via the facility reference), consistent with the other cards.
> Events with a blank `facility_id` can't be district-attributed and are excluded when a district is
> selected, so district totals may be less than the all-districts total.

**Response: `200 OK`**

```json
{
  "data": {
    "totalPatientsEBuzima": 150,
    "patientsReceivedHIE": 138,
    "transmissionRate": 92.0,
    "activeFacilities": 19,
    "activeDeviations": 34,
    "newDeviations24h": 3,
    "hieEventCount": 4820,
    "topFacilities": [
      {
        "rank": 1,
        "facilityId": "0015",
        "facilityName": "Muhima HC",
        "totalEnrollments": 89,
        "compliantPatients": 73,
        "nonCompliantPatients": 16,
        "complianceRate": 82.0,
        "activeDeviations": 5,
        "totalEvents": 2800,
        "patientsFromHIE": 41
      }
    ],
    "bottomFacilities": [
      {
        "rank": 1,
        "facilityId": "0031",
        "facilityName": "Kabuga HC",
        "totalEnrollments": 22,
        "compliantPatients": 6,
        "nonCompliantPatients": 16,
        "complianceRate": 27.0,
        "activeDeviations": 14,
        "totalEvents": 210,
        "patientsFromHIE": 3
      }
    ]
  }
}
```

> **Field semantics:**
> - `totalPatientsEBuzima` = distinct patients received via source `ebuzima-direct` (direct E-Buzima EMR integration — pending in most deployments).
> - `patientsReceivedHIE` = distinct patients received via source `ebuzima` (HIE-mediated).
> - `transmissionRate` = `patientsReceivedHIE / totalPatientsEBuzima × 100` (`0` when the denominator is `0`).
> - `activeDeviations` / `newDeviations24h` come from the same intelligence-summary aggregation as `/v1/insights/deviations/intelligence-summary` (`newDeviations24h` = `recentActivity.last24Hours`).
> - `topFacilities` / `bottomFacilities` are the top 3 / bottom 3 facilities by `complianceRate` (via the facility ranking service), each enriched with its HIE patient count.

### 15.2 GET `/v1/insights/dashboard/compliance-summary`

Patient compliance, facility activity, and practitioner step-completion tiles for the
Dashboard. All sections respect the global `facilityId` / `startDate` / `endDate` filters.

**Required Scope:** `dashboard:read`

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `facilityId` | String | — | Narrow patients/practitioner counts to a single facility (matched on the inbound event's payload `facility_id`; activity tile reflects whether that facility transmitted). |
| `startDate` | ISO 8601 | — | Start of date range — the tracked cohort is scoped by inbound event `event_time` (RI-36), **not** `enrolled_at` |
| `endDate` | ISO 8601 | — | End of date range |

**Response: `200 OK`**

```json
{
  "data": {
    "patients": {
      "trackedPatients": 19,
      "compliantPatients": 1,
      "nonCompliantPatients": 18,
      "complianceRate": 5.3
    },
    "facilities": {
      "trackedFacilities": 5,
      "activeFacilities": 3,
      "inactiveFacilities": 2,
      "activeFacilityRate": 60.0
    },
    "practitioners": {
      "trackedPractitioners": 12,
      "above90": 4,
      "between75And90": 5,
      "below75": 3
    }
  }
}
```

> **Definitions (patient block — RI-36, scoped by event activity, not `enrolled_at`):**
> - `trackedPatients` = distinct patients whose events are **considered by a protocol**
>   in the period: `ACCEPTED` inbound events with `event_time` in range whose
>   `cloudevents_id` matched a protocol (`compliance_event_logs.processing_status = 'MATCHED'`),
>   whether the event created a new enrollment or advanced an existing care journey. A
>   patient enrolled in a *prior* period who generates a matched event in this range is now
>   counted (the old `enrolled_at` cohort missed them). Consent-only / unmatched events are
>   excluded. All-time when no range is given; facility scope matches the event payload
>   `facility_id`.
> - `nonCompliantPatients` = of that tracked cohort, those with ≥1 deviation whose **clinical
>   occurrence date** (overdue/missed/order-violation date, coalescing to due date then
>   `detected_at`) falls in the period — the same occurrence clock as the Deviations page,
>   **not** `detected_at`.
> - `compliantPatients` = `trackedPatients − nonCompliantPatients` (floored at 0).
> - `complianceRate` = `compliantPatients ÷ trackedPatients` (0 when none tracked).
> - `activeFacilities` = facilities with ≥1 successful HIE submission
>   (`inbound_event_logs.status = 'ACCEPTED'`) in the period — see
>   §3.13a in the data dictionary.
> - Practitioner buckets are **step-completion** percentiles, not the deviation-based
>   patient compliance.

---

### 15.3 GET `/v1/insights/dashboard/referrals`

Referrals KPI — **referrals received by HIE** for the selected date range with a
compliant / non-compliant split, plus a per-facility breakdown. "Received by HIE" =
**ACCEPTED** inbound referral events (scoped by clinical `event_time`) — prod: an `Encounter`
carrying `TRANSFER_ENCOUNTER`; dev/demo: an accepted event that completed a Referral step.
"Compliant" = those matched to a Referral step in a tracked care journey; "Non-Compliant" =
received − matched; rate = compliant ÷ received. Backed by the `mv_daily_referral_kpis`
materialized view (`referral_count` = received, `matched_count` = compliant).

> **RI-51:** the Dashboard **"Total Referrals"** national indicator reads this endpoint's
> `totalReferralsReceived` (event count), and the Facility Ranking **Referrals** column + the
> Facilities **"Referral Details"** card read its `byFacility[].count` — one source, so all three
> agree and the per-facility counts sum to the national total. This count is **event-grained** (a
> patient referred twice counts twice); it is deliberately distinct from `/patients/referrals/received-by-hie`
> (distinct *patients*) and from the Compliance page's Service Workflow **Referral** step (patients
> who *completed* the referral step).

**Required Scope:** `dashboard:read`

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `facilityId` | String | — | Filter by facility FOSA ID |
| `startDate` | ISO 8601 (`OffsetDateTime`) | — | Start of date range — scoped by inbound event `event_time` |
| `endDate` | ISO 8601 (`OffsetDateTime`) | — | End of date range — scoped by inbound event `event_time` |

**Response: `200 OK`** — `ApiResponse<ReferralsKpiDto>`

```json
{
  "data": {
    "totalReferralsReceived": 1240,
    "compliantReferrals": 1180,
    "nonCompliantReferrals": 60,
    "referralComplianceRate": 95.2,
    "byFacility": [
      { "facilityId": "0002", "facilityName": "Kigali South HC", "district": "Nyarugenge", "count": 480, "compliant": 470, "nonCompliant": 10, "complianceRate": 97.9 },
      { "facilityId": "0015", "facilityName": "Muhima HC", "district": "Gasabo", "count": 320, "compliant": 300, "nonCompliant": 20, "complianceRate": 93.8 }
    ]
  }
}
```

> `totalReferralsReceived` = received by HIE; `compliantReferrals` = matched to a Referral step;
> `nonCompliantReferrals` = received − compliant; `referralComplianceRate` = compliant ÷ received (0
> when none received). `byFacility` returns one entry per facility in the reference list — each with
> its `district` (for the drill-down district/facility filters) and its own compliant/non-compliant
> split — with `count` `0` when a facility received no referrals in the period.

---

## 16. All Protocols Compliance Summary

### 16.1 GET `/v1/insights/protocols/compliance-summary`

Compliance summary aggregated **across all protocols combined** — the same
`ComplianceSummaryDto` shape as §1.1, but totals sum every protocol instead of scoping to
one `protocolDefinitionId`.

**Required Scope:** `dashboard:read`

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `facilityId` | String | — | Filter by facility |
| `startDate` | ISO 8601 (`OffsetDateTime`) | — | Start of date range — cohort scope depends on `dateFilterMode` |
| `endDate` | ISO 8601 (`OffsetDateTime`) | — | End of date range |
| `dateFilterMode` | String | `enrollment` | `enrollment` — `totalEnrollments` counts distinct patients **enrolled in the period**; `eventTime` (RI-36, "Clinical Event Date") — the patient block (tracked/compliant/rate) is the **matched-event cohort** by clinical `event_time`, computed with the SAME queries as the Dashboard "Service Compliance" card (`GET /dashboard/compliance-summary`), so the two reconcile exactly. Step metrics + deviation breakdown keep their snapshot/occurrence-date sources (cohort-independent). |

**Response: `200 OK`**

```json
{
  "data": {
    "totalEnrollments": 1840,
    "compliantPatients": 1324,
    "complianceRate": 72.0,
    "stepMetrics": {
      "totalSteps": 9600,
      "completed": 6800,
      "onTime": 5100,
      "late": 900,
      "early": 800,
      "overdue": 1200,
      "missed": 480,
      "due": 220,
      "pending": 900
    },
    "deviationCount": 1680,
    "deviationBreakdown": {
      "overdue": 1200,
      "missed": 430,
      "orderViolation": 50
    }
  }
}
```

> There is no `protocols: []` wrapper and no `complianceCategory` field — this is a flat,
> pre-aggregated total (not a per-protocol breakdown). Because the DTO is
> `@JsonInclude(NON_NULL)`, `protocolDefinitionId`/`protocolCanonical`/`statusBreakdown`
> (which only apply to the single-protocol §1.1 response) are entirely absent from this
> endpoint's JSON, not `null`.

---

## 17. Practitioner Rankings

### 17.1 GET `/v1/insights/practitioners/ranking`

Rank practitioners by **step-completion percentage**, patients served, or event volume.
The `complianceRate` field on the response is a step-completion ratio
(`completedSteps / totalSteps`), distinct from the deviation-based patient compliance
returned elsewhere — see §3.13 in the data dictionary.

**Required Scope:** `dashboard:read`

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `rankBy` | String | `complianceRate` | Ranking criterion: `complianceRate` (step completion), `totalPatients`, `totalEvents` |
| `order` | String | `desc` | Sort direction: `asc` or `desc` |
| `limit` | Integer | `50` | Page size |
| `startDate` | ISO 8601 | — | Start of date range |
| `endDate` | ISO 8601 | — | End of date range |
| `facilityId` | String | — | Filter by facility |
| `protocolDefinitionId` | UUID | — | Restrict to practitioners with step rows under the selected protocol |

**Response: `200 OK`**

```json
{
  "data": [
    {
      "rank": 1,
      "practitionerRef": "Practitioner/HLC-PRAC-2025-00005",
      "practitionerName": "Dr. Kwizera Emmanuel",
      "facilityId": "0002",
      "facilityName": "Kigali South HC",
      "totalPatients": 42,
      "complianceRate": 89.0,
      "totalSteps": 210,
      "completedSteps": 187,
      "activeDeviations": 3,
      "totalEvents": 640
    }
  ]
}
```

> There is no `complianceCategory` or `deviationCount` field — `activeDeviations` is the raw count, and `complianceRate` (`completedSteps / totalSteps`) is the only ranking-relevant ratio.

---

## 18. Protocol Action Order

### 18.1 GET `/v1/insights/protocols/{protocolDefinitionId}/action-order`

Returns the ordered list of actions defined in the protocol PlanDefinition, with type and title extracted from the JSONB definition.

**Required Scope:** `dashboard:read`

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `protocolDefinitionId` | UUID | Protocol definition ID |

**Response: `200 OK`**

```json
{
  "data": [
    {
      "actionId": "registration",
      "parentActionId": null,
      "type": null,
      "title": "Registration"
    },
    {
      "actionId": "anc-visit-1-referral-escalation",
      "parentActionId": "anc-visit-1-referral",
      "type": "fire-event",
      "title": "ANC Visit 1 Referral Escalation Notification"
    }
  ]
}
```

> **Note:** There is no `stepOrder` or `requiredBehavior` field — the response is exactly
> `actionId`, `parentActionId`, `type`, `title`, in the order actions appear in the
> `PlanDefinition.action[]` array. `type` is extracted from `action.type.coding[0].code`
> in the protocol definition JSONB; actions with `type = "fire-event"` are intelligence
> actions (notifications/escalations). `title` is from `action.title`.

---

## 19. Deviation Intelligence Summary

### 19.1 GET `/v1/insights/deviations/intelligence-summary`

Deviation counts broken down by type and severity, plus recent-activity windows. This is the deviation-focused counterpart to `/v1/insights/intelligence/summary` (§3.3), which tracks delivery success instead.

**Required Scope:** `dashboard:read`

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `startDate` | ISO 8601 (`OffsetDateTime`) | — | Start of date range |
| `endDate` | ISO 8601 (`OffsetDateTime`) | — | End of date range |
| `facilityId` | String | — | Filter by facility |

**Response: `200 OK`**

```json
{
  "data": {
    "totalDeviations": 145,
    "byType": {
      "overdue": 98,
      "missed": 42,
      "orderViolation": 5
    },
    "bySeverity": {
      "warning": 98,
      "critical": 47
    },
    "recentActivity": {
      "last24Hours": 3,
      "last7Days": 21,
      "last30Days": 84
    }
  }
}
```

> There is no `resolvedDeviations` or `intelligenceActions` field on this endpoint —
> resolution stats live at `/v1/insights/deviations/resolution-rate` (§10.2), and
> delivery stats live at `/v1/insights/intelligence/summary` (§3.3). `byType` keys are
> `overdue`/`missed`/`orderViolation` (lowercase); `bySeverity` keys are
> `warning`/`critical` (`OVERDUE` → warning, `MISSED`/`ORDER_VIOLATION` → critical).
