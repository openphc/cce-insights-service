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

Inbound clinical event audit trail. Queried for patient timeline views and **event volume analytics**.

| Column | Type | Used By Insights | Purpose |
|--------|------|------------------|---------|  
| `id` | `UUID` | Yes | PK |
| `cloudevents_id` | `VARCHAR` | No | CloudEvents ID (used for idempotency by Compliance Service) |
| `subject` | `VARCHAR` | Yes | Patient UPID — filter key |
| `type` | `VARCHAR` | Yes | CloudEvents `type` for display |
| `event_time` | `TIMESTAMPTZ` | Yes | Clinical event timestamp |
| `received_at` | `TIMESTAMPTZ` | Yes | Server ingestion timestamp |
| `source` | `VARCHAR` | Yes | Origin system (e.g., `rhie-mediator`, `ebuzima/kigali-south`) — group-by key for source system metrics |
| `data` | `JSONB` | Yes | Full CloudEvent data payload. Contains `resourceType` (group-by key) and practitioner references (extracted via JSONB path queries). See §3.3 for extraction paths. |
| `processing_status` | `VARCHAR` | Yes | `MATCHED`, `ZERO_MATCH`, `DUPLICATE` — used for processing quality metrics |
| `facility_id` | `VARCHAR` | Yes | Facility FOSA ID — group-by key for facility metrics |
| `protocol_instance_id` | `UUID` | Yes | Matched protocol instance (NULL for zero-match/duplicate) |
| `protocol_definition_id` | `UUID` | Yes | Matched protocol definition (NULL for zero-match/duplicate) |
| `action_id` | `VARCHAR` | Yes | Matched PlanDefinition action ID |
| `matched_step_instance_id` | `UUID` | Yes | Step completed by this event |

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

### 3.3 Practitioner Reference Extraction (JSONB)

Practitioner references are embedded within the `event_log.data` JSONB column at resource-type-specific paths. The Insights Service uses `COALESCE` across multiple JSONB paths to extract the practitioner reference regardless of resource type.

| Resource Type | Reference Path | Display Path |
|---|---|---|
| `Encounter` | `data->'participant'->0->'individual'->>'reference'` | `data->'participant'->0->'individual'->>'display'` |
| `Observation` | `data->'performer'->0->>'reference'` | `data->'performer'->0->>'display'` |
| `Condition` | `data->'asserter'->>'reference'` | `data->'asserter'->>'display'` |
| `MedicationRequest` | `data->'requester'->>'reference'` | `data->'requester'->>'display'` |
| `MedicationDispense` | `data->'performer'->0->'actor'->>'reference'` | `data->'performer'->0->'actor'->>'display'` |
| `MedicationAdministration` | `data->'performer'->0->'actor'->>'reference'` | `data->'performer'->0->'actor'->>'display'` |
| `ServiceRequest` | `data->'requester'->>'reference'` | `data->'requester'->>'display'` |
| `Procedure` | `data->'performer'->0->'actor'->>'reference'` | `data->'performer'->0->'actor'->>'display'` |
| `Immunization` | `data->'performer'->0->'actor'->>'reference'` | `data->'performer'->0->'actor'->>'display'` |

**Unified extraction expression:**
```sql
COALESCE(
  data->'participant'->0->'individual'->>'reference',   -- Encounter
  data->'performer'->0->>'reference',                    -- Observation
  data->'asserter'->>'reference',                        -- Condition
  data->'requester'->>'reference',                       -- MedicationRequest, ServiceRequest
  data->'performer'->0->'actor'->>'reference'            -- MedicationDispense, Procedure, Immunization
) AS practitioner_ref
```

> **Note:** If a source system does not include practitioner references in event payloads, those events will have `NULL` practitioner_ref and will be excluded from practitioner-grouped metrics. The `display` field is best-effort — availability depends on source system behavior.

### 3.4 Event Volume Formulas

```
event_count_by_resource_type = COUNT(*) FROM event_log WHERE processing_status != 'DUPLICATE' GROUP BY data->>'resourceType'

event_percentage = (resource_type_count / total_non_duplicate_events) * 100

processing_quality_rate = COUNT(status) / total_events  -- per processing_status value
```

### 3.5 Step Analytics Formulas

```
completion_rate = completed_count / total_instances

avg_days_to_complete = AVG(completed_at - due_date) in days  -- only for COMPLETED steps with a due_date

median_days_to_complete = PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY (completed_at - due_date))
    -- only for COMPLETED steps with a due_date
```

### 3.6 Completion Funnel Formulas

```
reached_count = COUNT(DISTINCT patient_id) with a step_instance for this action_id (any state)

completed_count = COUNT(DISTINCT patient_id) with state = 'COMPLETED' for this action_id

completion_rate = completed_count / reached_count

drop_off_rate = 1 - completion_rate
```

> **Step ordering:** `stepOrder` is derived from the `PlanDefinition.action[]` array ordering and `relatedAction` dependencies defined in the protocol definition. The first step is enrollment itself.

### 3.7 Outcome Distribution Formulas

```
percentage = (status_count / total_instances) * 100
```

Where `status` is one of `ACTIVE`, `COMPLETED`, `WITHDRAWN`, `EXPIRED` from `protocol_instance.status`.

### 3.8 Enrollment Trend Formulas

```
enrollments = COUNT(*) FROM protocol_instance
    WHERE protocol_definition_id = :id
    GROUP BY DATE_TRUNC(:interval, enrolled_at)
```

Supported intervals: `daily`, `weekly`, `monthly`.

### 3.9 Facility Ranking Formulas

```
compliance_rate = AVG(completed_or_skipped_steps / total_steps) per protocol instance at facility

active_deviations = COUNT(deviations) detected within last 30 days at facility

total_events = COUNT(DISTINCT event_log.id) at facility
```

Ranking options (`rankBy` parameter):
| Value | Sort Expression |
|---|---|
| `complianceRate` | `compliance_rate` DESC (best first) or ASC (worst first) |
| `deviationCount` | `active_deviations` ASC (best first) or DESC (worst first) |
| `eventVolume` | `total_events` DESC or ASC |

### 3.10 Deviation By-Action Formulas

```
total_deviations = COUNT(*) FROM deviation WHERE step_instance.action_id = :actionId

overdue_count = COUNT(*) WHERE deviation_type = 'OVERDUE'
missed_count  = COUNT(*) WHERE deviation_type = 'MISSED'

affected_patients = COUNT(DISTINCT protocol_instance.patient_id)
```

### 3.11 Deviation Resolution Rate Formulas

Resolution is determined by tracking the final state of step instances that had an `OVERDUE` deviation:

```
resolved = step_instance.state reached 'COMPLETED' after an OVERDUE deviation was recorded
escalated = step_instance.state reached 'MISSED' after an OVERDUE deviation was recorded

resolution_rate = resolved_count / total_overdue_deviations

avg_days_to_resolve = AVG(step_instance.completed_at - deviation.detected_at) in days
    -- only for resolved (COMPLETED) overdue steps
```

### 3.12 Processing Quality Formulas

```
matched_rate   = COUNT(processing_status = 'MATCHED')   / total_events * 100
zero_match_rate = COUNT(processing_status = 'ZERO_MATCH') / total_events * 100
duplicate_rate  = COUNT(processing_status = 'DUPLICATE')  / total_events * 100
```

Breakdowns are computed per `source` system. A high `ZERO_MATCH` rate indicates misconfigured emitters or protocols that don't cover incoming event types.

### 3.13 At-Risk Hotspot Formulas

Patient compliance category is computed across **all active protocol instances** at a facility:

```
on_track       = patient has NO step_instance with state IN ('OVERDUE', 'MISSED') across all active enrollments
at_risk        = patient has at least one 'OVERDUE' step_instance AND NO 'MISSED'
non_compliant  = patient has at least one 'MISSED' step_instance

percentage = category_count / total_patients_at_facility * 100
```

> **Note:** Facility is derived by joining `protocol_instance` → `event_log.facility_id`.

### 3.14 Repeat Deviation Formulas

```
total_deviations  = COUNT(*) FROM deviation per patient_id
overdue_count     = COUNT(deviation_type = 'OVERDUE') per patient_id
missed_count      = COUNT(deviation_type = 'MISSED') per patient_id
affected_protocols = COUNT(DISTINCT protocol_instance.id) per patient_id
affected_steps    = COUNT(DISTINCT deviation.step_instance_id) per patient_id
```

Only patients with `total_deviations >= :minDeviations` (default 3) are included.

---

## 4. Query Filter Parameters

### 4.1 Common Filters

| Parameter | Type | Applied To | Description |
|-----------|------|-----------|-------------|
| `facilityId` | String | `event_log.facility_id` | FOSA facility ID (joined via event_log) |
| `protocolDefinitionId` | UUID | `protocol_instance.protocol_definition_id` | Protocol filter |
| `startDate` | ISO 8601 | Various timestamp columns | Range start (inclusive) |
| `endDate` | ISO 8601 | Various timestamp columns | Range end (inclusive) |

### 4.2 Event Volume Filters

| Parameter | Type | Applied To | Description |
|-----------|------|-----------|-------------|
| `resourceType` | String | `event_log.data->>'resourceType'` | FHIR resource type (e.g., `Encounter`, `Observation`) |
| `source` | String | `event_log.source` | Source system identifier |
| `facilityId` | String | `event_log.facility_id` | Facility FOSA ID |
| `interval` | String | `DATE_TRUNC` | Aggregation period: `daily`, `weekly`, `monthly` |

### 4.3 Protocol Analytics Filters

| Parameter | Type | Applied To | Description |
|-----------|------|-----------|-------------|
| `protocolDefinitionId` | UUID | `protocol_instance.protocol_definition_id` | Protocol filter (path param) |
| `facilityId` | String | `event_log.facility_id` (via join) | Facility filter |
| `interval` | String | `DATE_TRUNC` | Aggregation: `daily`, `weekly`, `monthly` (enrollment trends) |

### 4.4 Facility Ranking Filters

| Parameter | Type | Applied To | Description |
|-----------|------|-----------|-------------|
| `rankBy` | String | Sort expression | `complianceRate`, `deviationCount`, or `eventVolume` |
| `order` | String | Sort direction | `asc` (worst first) or `desc` (best first) |
| `protocolDefinitionId` | UUID | `protocol_instance.protocol_definition_id` | Rank within a specific protocol |

### 4.5 Deviation Analytics Filters

| Parameter | Type | Applied To | Description |
|-----------|------|-----------|-------------|
| `deviationType` | String | `deviation.deviation_type` | Filter: `overdue`, `missed` |
| `protocolDefinitionId` | UUID | `protocol_instance.protocol_definition_id` | Protocol filter |
| `facilityId` | String | `event_log.facility_id` (via join) | Facility filter |

### 4.6 Patient Risk Filters

| Parameter | Type | Applied To | Description |
|-----------|------|-----------|-------------|
| `minDeviations` | Integer | `HAVING COUNT(*) >=` | Minimum deviations to include (repeat deviations, default 3) |
| `protocolDefinitionId` | UUID | `protocol_instance.protocol_definition_id` | Protocol filter |
| `facilityId` | String | `event_log.facility_id` (via join) | Facility filter |

### 4.3 Pagination

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

---

## 6. FHIR Resource Types (Event Volume)

The following FHIR resource types are commonly observed in `event_log.data->>'resourceType'` across RHIE and CHW integrations. The Insights Service does not restrict or validate resource types — it groups by whatever values exist in the data.

| Resource Type | Clinical Context | Typical Volume |
|---|---|---|
| `Encounter` | Visit registration, consultations, transfers | High |
| `Observation` | Vital signs, lab results, chief complaints, clinical findings | High |
| `Condition` | Diagnoses (ICD-11) | Medium |
| `MedicationRequest` | Prescriptions (e-Prescription) | Medium |
| `MedicationDispense` | Pharmacy dispensing | Medium |
| `MedicationAdministration` | Medication given to patient | Low–Medium |
| `ServiceRequest` | Lab orders, imaging orders, referrals | Medium |
| `Procedure` | Clinical procedures (ICHI codes) | Low |
| `Immunization` | Vaccinations (NPC codes) | Low–Medium |
| `AllergyIntolerance` | Allergy records | Low |
| `ImagingStudy` | Imaging results (DICOM) | Low |
| `DiagnosticReport` | Lab and imaging reports | Low |
| `Consent` | Patient consent records | Low |

> **Note:** Non-FHIR events (`datacontenttype: application/json`) do not have a `resourceType` field. These will appear as `null` in resource type groupings and should be filtered or grouped separately.
