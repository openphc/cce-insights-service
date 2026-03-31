# CCE Insights Service — API Reference

All endpoints are accessed through the **CCE Gateway Service** (not directly by external callers). The Gateway validates OAuth tokens and enforces the `dashboard:read` scope. The Insights Service receives pre-authenticated requests with `user_id`, `facility_id`, `roles` forwarded as HTTP headers.

> **Naming:** This service is referred to as "Analytics Service" in the CCE Solution Design v0.3. Implementation uses **Insights Service** (`cce-insights-service`).

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
| `startDate` | ISO 8601 | — | Start of date range |
| `endDate` | ISO 8601 | — | End of date range |

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
    "complianceRate": 0.72,
    "stepMetrics": {
      "totalSteps": 1240,
      "completed": 680,
      "onTime": 520,
      "late": 120,
      "early": 40,
      "overdue": 180,
      "missed": 90,
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
| `protocolDefinitionId` | UUID | — | Filter by specific protocol |
| `startDate` | ISO 8601 | — | Start of date range |
| `endDate` | ISO 8601 | — | End of date range |

**Response: `200 OK`**

```json
{
  "data": {
    "facilityId": "0002",
    "totalPatients": 156,
    "totalEnrollments": 312,
    "overallComplianceRate": 0.68,
    "protocolBreakdown": [
      {
        "protocolDefinitionId": "550e8400-e29b-41d4-a716-446655440000",
        "protocolCanonical": "http://openphc.org/fhir/PlanDefinition/anc-high-risk|2.1",
        "enrollments": 89,
        "complianceRate": 0.74,
        "activeDeviations": 12
      },
      {
        "protocolDefinitionId": "660e8400-e29b-41d4-a716-446655440000",
        "protocolCanonical": "http://openphc.org/fhir/PlanDefinition/child-immunization|1.0",
        "enrollments": 223,
        "complianceRate": 0.65,
        "activeDeviations": 34
      }
    ]
  }
}
```

---

### 1.3 GET `/v1/insights/protocols/{protocolDefinitionId}/patients`

List patients enrolled in a protocol, filterable by compliance status.

**Required Scope:** `dashboard:read`

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `status` | String | — | Filter: `on_track`, `at_risk`, `non_compliant` |
| `facilityId` | String | — | Filter by facility |
| `limit` | Integer | `50` | Page size (max 200) |
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
      "complianceRate": 0.50,
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

**Compliance Categories:**
| Category | Definition |
|---|---|
| `on_track` | All steps completed on time or early, no active overdue/missed steps |
| `at_risk` | Has one or more overdue steps (not yet missed) |
| `non_compliant` | Has one or more missed steps |

---

## 2. Patient Compliance

### 2.1 GET `/v1/insights/patients/{patientId}/compliance-timeline`

Full compliance timeline for a patient across all enrolled protocols. Combines event history and step status into a chronological view.

**Required Scope:** `dashboard:read`

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `startDate` | ISO 8601 | — | Start of date range |
| `endDate` | ISO 8601 | — | End of date range |

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
        "complianceRate": 0.50,
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
      "enrolledAt": "2026-01-15T10:00:00Z",
      "status": "active",
      "complianceRate": 0.50,
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
    "complianceRate": 0.50,
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
| `sort` | String | `detected_at:desc` | Sort field and direction |
| `limit` | Integer | `50` | Page size (max 200) |
| `cursor` | String | — | Pagination cursor |

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
      "facilityId": "0002"
    }
  ],
  "pagination": {
    "limit": 50,
    "next_cursor": "eyJpZCI6NDU2fQ==",
    "has_more": false
  }
}
```

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
        "total": 15
      },
      {
        "period": "2026-02-24",
        "overdue": 8,
        "missed": 5,
        "total": 13
      },
      {
        "period": "2026-03-03",
        "overdue": 15,
        "missed": 2,
        "total": 17
      }
    ]
  }
}
```

### 3.3 GET `/v1/insights/intelligence/summary`

Intelligence events summary — counts by type and time period.

**Required Scope:** `dashboard:read`

> **Note:** In release 1.0.0, this endpoint aggregates deviation records as a proxy for intelligence events. Full intelligence event aggregation will be available when the Compliance Service enables intelligence trigger publishing.

**Response: `200 OK`**

```json
{
  "data": {
    "totalDeviations": 270,
    "byType": {
      "overdue": 180,
      "missed": 90
    },
    "bySeverity": {
      "warning": 180,
      "critical": 90
    },
    "recentActivity": {
      "last24Hours": 8,
      "last7Days": 42,
      "last30Days": 145
    }
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
      "matched": 9820,
      "zeroMatch": 2540,
      "duplicate": 120
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
    ]
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
| `resourceType` | String | — | Filter by FHIR resource type (e.g., `Encounter`) |
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
| `limit` | Integer | `50` | Page size (max 200) |
| `cursor` | String | — | Pagination cursor |

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
  ],
  "pagination": {
    "limit": 50,
    "next_cursor": null,
    "has_more": false
  }
}
```

---

### 4.5 GET `/v1/insights/events/by-practitioner`

Event counts grouped by practitioner, with resource type breakdown. Practitioner references are extracted from the `event_log.data` JSONB payload using resource-type-specific paths (e.g., `participant[0].individual.reference` for Encounter, `performer[0].reference` for Observation).

**Required Scope:** `dashboard:read`

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `facilityId` | String | — | Filter by facility FOSA ID |
| `resourceType` | String | — | Filter by FHIR resource type |
| `startDate` | ISO 8601 | — | Start of date range |
| `endDate` | ISO 8601 | — | End of date range |
| `limit` | Integer | `50` | Page size (max 200) |
| `cursor` | String | — | Pagination cursor |

**Response: `200 OK`**

```json
{
  "data": [
    {
      "practitionerRef": "Practitioner/HLC-PRAC-2025-00005",
      "practitionerDisplay": "Dr. Aziz Muhammed",
      "facilityId": "0002",
      "totalEvents": 480,
      "byResourceType": [
        { "resourceType": "Encounter", "count": 180 },
        { "resourceType": "Observation", "count": 150 },
        { "resourceType": "Condition", "count": 80 },
        { "resourceType": "MedicationRequest", "count": 70 }
      ]
    },
    {
      "practitionerRef": "Practitioner/f830114a-bc0b-410e-b8c7-79e61c0df653",
      "practitionerDisplay": "Dr. Marie Uwimana",
      "facilityId": "0002",
      "totalEvents": 320,
      "byResourceType": [
        { "resourceType": "Encounter", "count": 120 },
        { "resourceType": "Observation", "count": 95 },
        { "resourceType": "Condition", "count": 55 },
        { "resourceType": "MedicationRequest", "count": 50 }
      ]
    }
  ],
  "pagination": {
    "limit": 50,
    "next_cursor": null,
    "has_more": false
  }
}
```

> **Note on practitioner extraction:** The `practitionerDisplay` field is extracted from the FHIR reference's `display` property when available (e.g., `data->'participant'->0->'individual'->>'display'`). If the source system does not include a display name, this field will be `null`.

> **Note on practitioner JSONB extraction paths:**
> | Resource Type | JSONB Path for Reference | JSONB Path for Display |
> |---|---|---|
> | Encounter | `data->'participant'->0->'individual'->>'reference'` | `data->'participant'->0->'individual'->>'display'` |
> | Observation | `data->'performer'->0->>'reference'` | `data->'performer'->0->>'display'` |
> | Condition | `data->'asserter'->>'reference'` | `data->'asserter'->>'display'` |
> | MedicationRequest | `data->'requester'->>'reference'` | `data->'requester'->>'display'` |
> | MedicationDispense | `data->'performer'->0->'actor'->>'reference'` | `data->'performer'->0->'actor'->>'display'` |
> | MedicationAdministration | `data->'performer'->0->'actor'->>'reference'` | `data->'performer'->0->'actor'->>'display'` |
> | ServiceRequest | `data->'requester'->>'reference'` | `data->'requester'->>'display'` |
> | Procedure | `data->'performer'->0->'actor'->>'reference'` | `data->'performer'->0->'actor'->>'display'` |
> | Immunization | `data->'performer'->0->'actor'->>'reference'` | `data->'performer'->0->'actor'->>'display'` |

---

### 4.6 GET `/v1/insights/events/by-source`

Event counts grouped by source system, based on `inbound_event` table. Shows ALL events received per source (not just compliance-matched) with **status breakdown** (ACCEPTED, REJECTED, DUPLICATE).

**Data Source:** `inbound_event` (Collector Service)

**Required Scope:** `dashboard:read`

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `facilityId` | String | — | Filter by facility FOSA ID |
| `startDate` | ISO 8601 | — | Start of date range |
| `endDate` | ISO 8601 | — | End of date range |

**Response: `200 OK`**

```json
{
  "data": [
    {
      "source": "rhie-mediator",
      "totalEvents": 9200,
      "byResourceType": [
        { "resourceType": "ACCEPTED", "count": 8400 },
        { "resourceType": "REJECTED", "count": 520 },
        { "resourceType": "DUPLICATE", "count": 280 }
      ]
    },
    {
      "source": "ebuzima/kigali-south",
      "totalEvents": 4350,
      "byResourceType": [
        { "resourceType": "ACCEPTED", "count": 4080 },
        { "resourceType": "REJECTED", "count": 180 },
        { "resourceType": "DUPLICATE", "count": 90 }
      ]
    }
  ]
}
```

> **Note:** The `byResourceType` field reuses the existing DTO structure but contains status categories (ACCEPTED, REJECTED, DUPLICATE) instead of FHIR resource types. For FHIR resource type breakdowns, use `/v1/insights/events/by-resource-type`.

---

### 4.7 GET `/v1/insights/events/source-comparison`

Compare two source systems to identify overlapping (potentially duplicate) events and events unique to each source. Overlap is determined by matching `subject` (patient), CloudEvents `type`, and `event_time` within a configurable time window.

**Data Source:** `inbound_event` (Collector Service) — captures ALL events received, not just compliance-matched.

**Use case:** A client is sending the same clinical events through two different upstream systems (e.g., `rhie-mediator` and `ebuzima/kigali-south`). This endpoint quantifies the overlap and surfaces sample pairs for investigation.

**Required Scope:** `dashboard:read`

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `sourceA` | String | **required** | First source system to compare |
| `sourceB` | String | **required** | Second source system to compare |
| `windowSeconds` | Long | `300` | Time window (seconds) for matching events. Two events for the same patient/resourceType are considered overlapping if their `event_time` differs by ≤ this window. Default: 5 minutes. |
| `facilityId` | String | — | Filter by facility FOSA ID |
| `startDate` | ISO 8601 | — | Events after this time |
| `endDate` | ISO 8601 | — | Events before this time |
| `sampleLimit` | Integer | `20` | Number of sample overlap pairs to return (max 100) |

**Response: `200 OK`**

```json
{
  "data": {
    "sourceA": "rhie-mediator",
    "sourceB": "ebuzima/kigali-south",
    "matchWindowSeconds": 300,
    "sourceASummary": {
      "source": "rhie-mediator",
      "totalEvents": 8400,
      "uniqueEvents": 5200,
      "overlappingEvents": 3200,
      "overlapPercentage": 38.1,
      "uniqueByResourceType": [
        { "resourceType": "Encounter", "count": 1800 },
        { "resourceType": "Observation", "count": 1500 },
        { "resourceType": "Condition", "count": 900 },
        { "resourceType": "MedicationRequest", "count": 600 },
        { "resourceType": "ServiceRequest", "count": 400 }
      ]
    },
    "sourceBSummary": {
      "source": "ebuzima/kigali-south",
      "totalEvents": 4080,
      "uniqueEvents": 880,
      "overlappingEvents": 3200,
      "overlapPercentage": 78.4,
      "uniqueByResourceType": [
        { "resourceType": "Encounter", "count": 300 },
        { "resourceType": "Observation", "count": 250 },
        { "resourceType": "Condition", "count": 180 },
        { "resourceType": "MedicationRequest", "count": 100 },
        { "resourceType": "ServiceRequest", "count": 50 }
      ]
    },
    "overlap": {
      "totalOverlappingEvents": 3200,
      "byResourceType": [
        { "resourceType": "Encounter", "count": 1100 },
        { "resourceType": "Observation", "count": 950 },
        { "resourceType": "Condition", "count": 480 },
        { "resourceType": "MedicationRequest", "count": 380 },
        { "resourceType": "ServiceRequest", "count": 290 }
      ]
    },
    "samples": [
      {
        "eventAId": "990e8400-e29b-41d4-a716-446655440003",
        "eventBId": "990e8400-e29b-41d4-a716-446655440001",
        "subject": "Patient/260225-0002-5501",
        "resourceType": "Encounter",
        "eventTimeA": "2026-01-20T09:30:00Z",
        "eventTimeB": "2026-01-20T09:30:00Z",
        "timeDiffSeconds": 0.0
      }
    ]
  }
}
```

**How matching works:**
- Two events are considered "overlapping" when all three conditions are met:
  1. Same `subject` (patient reference)
  2. Same CloudEvents `type` (matched from `inbound_event.type`)
  3. `|event_time_A - event_time_B|` ≤ `windowSeconds`
- Events with `status = 'DUPLICATE'` (collector-level duplicates) are excluded from both sides.
- `uniqueEvents` = events in that source with no matching counterpart in the other source.
- `overlapPercentage` = `overlappingEvents / totalEvents * 100` for that source.
- ResourceType in the response is extracted from the raw payload FHIR resource, falling back to the CloudEvents `type` if not available.

**Tuning `windowSeconds`:**
| Value | Use case |
|-------|----------|
| `0` | Exact timestamp match only (same event forwarded with identical timestamps) |
| `300` (default) | 5-minute window — accounts for minor processing delays between systems |
| `3600` | 1-hour window — catches events that may have been batched differently |

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
| `facilityId` | String | — | Filter by facility FOSA ID |
| `startDate` | ISO 8601 | — | Filter step instances created after |
| `endDate` | ISO 8601 | — | Filter step instances created before |

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
- `medianDaysToComplete` = Median of the same set (using PostgreSQL `PERCENTILE_CONT(0.5)`)

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
| `facilityId` | String | — | Filter by facility FOSA ID |
| `startDate` | ISO 8601 | — | Enrollments after this date |
| `endDate` | ISO 8601 | — | Enrollments before this date |

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
| `facilityId` | String | — | Filter by facility FOSA ID |
| `startDate` | ISO 8601 | — | Enrollments after this date |
| `endDate` | ISO 8601 | — | Enrollments before this date |

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
| `facilityId` | String | — | Filter by facility FOSA ID |
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
| `rankBy` | String | `complianceRate` | Ranking metric: `complianceRate`, `deviationCount`, `eventVolume` |
| `order` | String | `desc` | `asc` (worst first) or `desc` (best first) |
| `startDate` | ISO 8601 | — | Date range start |
| `endDate` | ISO 8601 | — | Date range end |
| `limit` | Integer | `50` | Page size (max 200) |
| `cursor` | String | — | Pagination cursor |

**Response: `200 OK`**

```json
{
  "data": [
    {
      "rank": 1,
      "facilityId": "0015",
      "totalEnrollments": 89,
      "complianceRate": 0.82,
      "activeDeviations": 5,
      "totalEvents": 2800
    },
    {
      "rank": 2,
      "facilityId": "0002",
      "totalEnrollments": 156,
      "complianceRate": 0.74,
      "activeDeviations": 12,
      "totalEvents": 3200
    },
    {
      "rank": 3,
      "facilityId": "0008",
      "totalEnrollments": 62,
      "complianceRate": 0.58,
      "activeDeviations": 22,
      "totalEvents": 2100
    }
  ],
  "pagination": {
    "limit": 50,
    "next_cursor": null,
    "has_more": false
  }
}
```

---

## 10. Deviation Analytics

### 10.1 GET `/v1/insights/deviations/by-action`

Most commonly deviated-from protocol steps, grouped by `actionId`. Identifies systemic bottlenecks in care delivery.

**Required Scope:** `dashboard:read`

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `protocolDefinitionId` | UUID | — | Filter by protocol |
| `deviationType` | String | — | Filter: `overdue`, `missed` |
| `facilityId` | String | — | Filter by facility |
| `startDate` | ISO 8601 | — | Deviations detected after |
| `endDate` | ISO 8601 | — | Deviations detected before |
| `limit` | Integer | `20` | Page size (max 100) |

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
      "affectedPatients": 72
    },
    {
      "actionId": "anc-visit-3",
      "protocolDefinitionId": "550e8400-e29b-41d4-a716-446655440000",
      "protocolCanonical": "http://openphc.org/fhir/PlanDefinition/anc-high-risk|2.1",
      "totalDeviations": 58,
      "overdueCount": 40,
      "missedCount": 18,
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
| `facilityId` | String | — | Filter by facility |
| `startDate` | ISO 8601 | — | Deviations detected after |
| `endDate` | ISO 8601 | — | Deviations detected before |

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

## 11. Event Processing & Integration Health

### 11.1 GET `/v1/insights/events/processing-quality`

`MATCHED` / `ZERO_MATCH` / `DUPLICATE` ratios per source system. Monitors integration health — a high `ZERO_MATCH` rate signals misconfigured emitters or protocols that don't cover the incoming event types.

**Required Scope:** `dashboard:read`

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `source` | String | — | Filter by source system |
| `facilityId` | String | — | Filter by facility |
| `startDate` | ISO 8601 | 30 days ago | Start of date range |
| `endDate` | ISO 8601 | now | End of date range |

**Response: `200 OK`**

```json
{
  "data": {
    "totalEvents": 12480,
    "overall": {
      "matched": { "count": 9820, "percentage": 78.7 },
      "zeroMatch": { "count": 2540, "percentage": 20.4 },
      "duplicate": { "count": 120, "percentage": 0.9 }
    },
    "bySource": [
      {
        "source": "rhie-mediator",
        "totalEvents": 8400,
        "matched": { "count": 7200, "percentage": 85.7 },
        "zeroMatch": { "count": 1140, "percentage": 13.6 },
        "duplicate": { "count": 60, "percentage": 0.7 }
      },
      {
        "source": "ebuzima/kigali-south",
        "totalEvents": 4080,
        "matched": { "count": 2620, "percentage": 64.2 },
        "zeroMatch": { "count": 1400, "percentage": 34.3 },
        "duplicate": { "count": 60, "percentage": 1.5 }
      }
    ]
  }
}
```

---

## 12. Patient Risk Analytics

### 12.1 GET `/v1/insights/patients/at-risk-hotspots`

Concentration of `at_risk` and `non_compliant` patients by facility. Directs field supervision and outreach resources to the facilities that need them most.

**Required Scope:** `dashboard:read`

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `protocolDefinitionId` | UUID | — | Filter by protocol |
| `startDate` | ISO 8601 | — | Enrollments after this date |
| `endDate` | ISO 8601 | — | Enrollments before this date |
| `limit` | Integer | `50` | Page size (max 200) |
| `cursor` | String | — | Pagination cursor |

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
  ],
  "pagination": {
    "limit": 50,
    "next_cursor": null,
    "has_more": false
  }
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

### 12.2 GET `/v1/insights/patients/repeat-deviations`

Patients with deviations across multiple protocols or multiple steps within the same protocol. Identifies patients who need targeted outreach.

**Required Scope:** `dashboard:read`

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `minDeviations` | Integer | `3` | Minimum deviation count to include |
| `facilityId` | String | — | Filter by facility |
| `protocolDefinitionId` | UUID | — | Filter by protocol |
| `startDate` | ISO 8601 | — | Deviations detected after |
| `endDate` | ISO 8601 | — | Deviations detected before |
| `limit` | Integer | `50` | Page size (max 200) |
| `cursor` | String | — | Pagination cursor |

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
  ],
  "pagination": {
    "limit": 50,
    "next_cursor": null,
    "has_more": false
  }
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
