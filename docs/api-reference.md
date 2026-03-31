# CCE Insights Service — API Reference

All endpoints are accessed through the **CCE Gateway Service** (not directly by external callers). The Gateway validates OAuth tokens and enforces the `dashboard:read` scope. The Insights Service receives pre-authenticated requests with `user_id`, `facility_id`, `roles` forwarded as HTTP headers.

> **Naming:** This service is referred to as "Analytics Service" in the CCE Solution Design v0.3. Implementation uses **Insights Service** (`cce-insights-service`).

---

## 1. Compliance Summaries

### 1.1 GET `/v1/protocols/{protocolDefinitionId}/compliance-summary`

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

### 1.2 GET `/v1/facilities/{facilityId}/compliance-summary`

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

### 1.3 GET `/v1/protocols/{protocolDefinitionId}/patients`

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

### 2.1 GET `/v1/patients/{patientId}/compliance-timeline`

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

### 2.2 GET `/v1/patients/{patientId}/protocol-tracking`

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

### 2.3 GET `/v1/patients/{patientId}/protocol-tracking/{protocolInstanceId}`

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

## 3. Deviations & Intelligence

### 3.1 GET `/v1/deviations`

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

### 3.2 GET `/v1/deviations/trends`

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

### 3.3 GET `/v1/intelligence/summary`

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

## 4. Exports

### 4.1 GET `/v1/exports/compliance-report`

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

## 5. Error Responses

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

## 6. Actuator Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/actuator/health` | Aggregate health status |
| GET | `/actuator/health/liveness` | Kubernetes liveness probe |
| GET | `/actuator/health/readiness` | Kubernetes readiness probe |
| GET | `/actuator/prometheus` | Prometheus metrics scrape |
