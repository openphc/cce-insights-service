# CCE Insights Service — Data Dictionary

Comprehensive reference for all database tables queried, DTOs, query parameters, aggregation formulas, and metrics used by the Insights Service.

> The Insights Service does **not own** any database tables. All tables are owned by the Compliance Service. This data dictionary documents the read-only view used by the Insights Service.

---

## 1. Database Tables (Read-Only)

### 1.1 `protocol_definition`

Protocol definition metadata. Queried for display names and protocol versioning.

| Column | Type | Used By Insights | Purpose |
|--------|------|------------------|----------|
| `id` | `UUID` | Yes | PK — join target for protocol-level APIs |
| `url` | `VARCHAR` | Yes | Protocol canonical URL |
| `version` | `VARCHAR` | Yes | Protocol version |
| `status` | `VARCHAR` | Yes | Filter active vs retired protocols |
| `definition` | `JSONB` | Partial | Full FHIR R4 PlanDefinition — protocol name extracted from `definition->'name'` when needed for display |
| `loaded_at` | `TIMESTAMPTZ` | Yes | When protocol was loaded |

### 1.2 `protocol_instance`

Patient enrollments in protocols. Primary table for compliance aggregation.

| Column | Type | Used By Insights | Purpose |
|--------|------|------------------|---------|
| `id` | `UUID` | Yes | PK |
| `protocol_definition_id` | `UUID` | Yes | FK → `protocol_definition.id` |
| `patient_id` | `VARCHAR` | Yes | Patient UPID — group-by key |
| `protocol_canonical` | `VARCHAR` | Yes | `url|version` for display |
| `status` | `VARCHAR` | Yes | `ACTIVE`, `COMPLETED`, `WITHDRAWN`, `EXPIRED` |
| `enrolled_at` | `TIMESTAMPTZ` | Yes | Enrollment timestamp |
| `created_at` | `TIMESTAMPTZ` | Yes | Record creation |
| `updated_at` | `TIMESTAMPTZ` | Yes | Last status change |

> **Note:** `protocol_instance` does not have a `facility_id` column. Facility-based filtering is achieved by joining through `event_log.facility_id` (using `event_log.protocol_instance_id`).

### 1.3 `step_instance`

Individual protocol steps per patient. Primary table for compliance calculations.

| Column | Type | Used By Insights | Purpose |
|--------|------|------------------|---------|
| `id` | `UUID` | Yes | PK |
| `protocol_instance_id` | `UUID` | Yes | FK → `protocol_instance.id` |
| `action_id` | `VARCHAR` | Yes | PlanDefinition action ID |
| `repeat_index` | `INTEGER` | Yes | Recurrence index |
| `state` | `VARCHAR` | Yes | `PENDING`, `DUE`, `OVERDUE`, `MISSED`, `COMPLETED`, `SKIPPED` |
| `due_date` | `TIMESTAMPTZ` | Yes | When step becomes due |
| `overdue_date` | `TIMESTAMPTZ` | Yes | When step becomes overdue |
| `missed_date` | `TIMESTAMPTZ` | Yes | When step becomes missed |
| `completed_at` | `TIMESTAMPTZ` | Yes | Completion timestamp |
| `completed_by_source` | `VARCHAR` | Yes | Source system that completed |
| `completion_status` | `VARCHAR` | Yes | `EARLY`, `ON_TIME`, `LATE` |

### 1.4 `deviation`

Recorded deviations from protocol pathways.

| Column | Type | Used By Insights | Purpose |
|--------|------|------------------|---------|
| `id` | `UUID` | Yes | PK |
| `protocol_instance_id` | `UUID` | Yes | FK → `protocol_instance.id` |
| `step_instance_id` | `UUID` | Yes | FK → `step_instance.id` |
| `deviation_type` | `VARCHAR` | Yes | `OVERDUE` or `MISSED` |
| `detected_at` | `TIMESTAMPTZ` | Yes | When deviation was detected |
| `metadata` | `JSONB` | Yes | Additional context (due date, overdue date) |

### 1.5 `event_log`

Inbound clinical event audit trail. Queried for patient timeline views.

| Column | Type | Used By Insights | Purpose |
|--------|------|------------------|---------|
| `id` | `UUID` | Yes | PK |
| `subject` | `VARCHAR` | Yes | Patient UPID — filter key |
| `type` | `VARCHAR` | Yes | Event type for display |
| `event_time` | `TIMESTAMPTZ` | Yes | Clinical event timestamp |
| `source` | `VARCHAR` | Yes | Origin system |
| `processing_status` | `VARCHAR` | Yes | `MATCHED`, `ZERO_MATCH`, `DUPLICATE` |
| `facility_id` | `VARCHAR` | Yes | Facility filter |

---

## 2. Enum Values

### 2.1 `ProtocolInstanceStatus`

| Value | Description |
|-------|-------------|
| `ACTIVE` | Protocol enrollment is ongoing |
| `COMPLETED` | All steps completed |
| `WITHDRAWN` | Enrollment cancelled |
| `EXPIRED` | Protocol expired without completion |

### 2.2 `StepState`

| Value | Description | Compliance Category |
|-------|-------------|---------------------|
| `PENDING` | Not yet due | on_track |
| `DUE` | Currently due | on_track |
| `OVERDUE` | Past tolerance window | at_risk |
| `MISSED` | Never completed (terminal) | non_compliant |
| `COMPLETED` | Completed by event (terminal) | on_track |
| `SKIPPED` | Skipped (optional) (terminal) | on_track |

### 2.3 `CompletionStatus`

| Value | Description |
|-------|-------------|
| `EARLY` | Completed before `due_date` |
| `ON_TIME` | Completed between `due_date` and `overdue_date` |
| `LATE` | Completed after `overdue_date` |

### 2.4 `DeviationType`

| Value | Description | Severity Mapping |
|-------|-------------|------------------|
| `OVERDUE` | Step became overdue | Warning |
| `MISSED` | Step was missed | Critical |

### 2.5 `ComplianceCategory` (computed — not in DB)

| Value | Definition |
|-------|------------|
| `on_track` | All steps completed on time/early, no active overdue/missed |
| `at_risk` | One or more overdue steps (not yet missed) |
| `non_compliant` | One or more missed steps |

---

## 3. Aggregation Formulas

### 3.1 Compliance Rate

```
compliance_rate = completed_steps / total_steps
```

Where `completed_steps` includes `COMPLETED` (all completion statuses) and `SKIPPED`. `total_steps` counts all step instances for the protocol instance.

### 3.2 Deviation Severity Mapping

| Deviation Type | Severity |
|---|---|
| `OVERDUE` | `warning` |
| `MISSED` | `critical` |

---

## 4. Query Filter Parameters

### 4.1 Common Filters

| Parameter | Type | Applied To | Description |
|-----------|------|-----------|-------------|
| `facilityId` | String | `event_log.facility_id` | FOSA facility ID (joined via event_log) |
| `protocolDefinitionId` | UUID | `protocol_instance.protocol_definition_id` | Protocol filter |
| `startDate` | ISO 8601 | Various timestamp columns | Range start (inclusive) |
| `endDate` | ISO 8601 | Various timestamp columns | Range end (inclusive) |

### 4.2 Pagination

Cursor-based pagination using encoded cursors.

| Parameter | Type | Default | Max | Description |
|-----------|------|---------|-----|-------------|
| `limit` | Integer | 50 | 200 | Page size |
| `cursor` | String | — | — | Opaque cursor from previous response |

---

## 5. Metrics

| Metric Name | Type | Tags | Description |
|-------------|------|------|-------------|
| `cce.insights.request.duration` | Timer | `endpoint`, `status` | REST endpoint response time |
| `cce.insights.query.duration` | Timer | `query_type` | Database query execution time |
| `cce.insights.request.count` | Counter | `endpoint`, `status` | Request count per endpoint |
