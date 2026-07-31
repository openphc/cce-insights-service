# CCE Insights Service — Data Dictionary

Comprehensive reference for all database tables queried, DTOs, query parameters, aggregation formulas, and metrics used by the Insights Service.

> The Insights Service does **not own** any database tables. All tables are owned by the Compliance Service, Intelligence Service, or Collector Service. This data dictionary documents the read-only view used by the Insights Service.

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
| `definition` | `JSONB` | Yes | Full FHIR R4 PlanDefinition — protocol name from `definition->'name'`, title from `definition->'title'`, action order from `definition->'action'` array (type from `action.type.coding[0].code`, title from `action.title`), `relatedArtifact` array with documentation URLs and thumbnail image references |
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

### 1.6 `inbound_event`

Request audit log & rejection tracking. Owned by the **Collector Service** — every HTTP request is persisted as-is before processing. Used for ingestion funnel, rejection analytics, source data quality, and pipeline loss detection.

| Column | Type | Used By Insights | Purpose |
|--------|------|------------------|---------|
| `id` | `UUID` | Yes | PK (UUIDv7, time-ordered) |
| `cloudevents_id` | `VARCHAR` | Yes | CloudEvents `id` — used for pipeline loss detection (JOIN to `event_log`) |
| `source` | `VARCHAR` | Yes | CloudEvents source — group-by key for source metrics |
| `type` | `VARCHAR` | Yes | CloudEvents type |
| `spec_version` | `VARCHAR` | No | Always "1.0" |
| `subject` | `VARCHAR` | Yes | Patient UPID |
| `event_time` | `TIMESTAMPTZ` | Yes | Source-provided event time |
| `data_content_type` | `VARCHAR` | No | MIME type of data payload |
| `facility_id` | `VARCHAR` | Yes | Facility FOSA ID — filter key |
| `correlation_id` | `VARCHAR` | No | Distributed tracing ID |
| `source_event_id` | `VARCHAR` | No | Source system's internal event ID |
| `raw_payload` | `JSONB` | Yes | Full original request body — resourceType extracted via `raw_payload->'data'->>'resourceType'` |
| `status` | `VARCHAR` | Yes | `RECEIVED`, `ACCEPTED`, `REJECTED`, `DUPLICATE` — group-by key for funnel metrics |
| `rejection_reason` | `VARCHAR` | Yes | Rejection reason code (if status = REJECTED) — group-by key for rejection analytics |
| `error_details` | `TEXT` | No | Stack trace or validation error messages |
| `received_at` | `TIMESTAMPTZ` | Yes | Server-side receipt timestamp (UTC) — filter + sort key |

> **Deduplication constraint:** `UNIQUE(cloudevents_id, source)` — primary deduplication key.

### 1.7 `intelligence_delivery`

Intelligence action delivery records. Owned by the **Intelligence Service**. Used for tracking fire-event actions, escalation notifications, and delivery status.

| Column | Type | Used By Insights | Purpose |
|--------|------|------------------|---------|
| `id` | `UUID` | Yes | PK |
| `intelligence_event_id` | `VARCHAR` | Yes | Intelligence event identifier |
| `action_definition_id` | `VARCHAR` | Yes | Protocol action definition reference |
| `destination_adaptor_mapping_id` | `UUID` | Yes | FK → `destination_adaptor_mapping.id` |
| `action_type` | `VARCHAR` | Yes | Intelligence action type (e.g., `fire-event`) |
| `status` | `VARCHAR` | Yes | Delivery status |
| `subject` | `VARCHAR` | Yes | Patient UPID |
| `protocol_canonical` | `VARCHAR` | Yes | Protocol reference |
| `action_id` | `VARCHAR` | Yes | Protocol action ID |
| `severity` | `VARCHAR` | Yes | Alert severity |
| `destination` | `VARCHAR` | Yes | Delivery destination |
| `attempt_count` | `INTEGER` | Yes | Delivery attempt count |
| `created_at` | `TIMESTAMPTZ` | Yes | Record creation timestamp |
| `updated_at` | `TIMESTAMPTZ` | Yes | Last update timestamp |
| `delivered_at` | `TIMESTAMPTZ` | Yes | Successful delivery timestamp |

### 1.8 `receiver_adaptor`

Adaptor registry for intelligence delivery receivers.

| Column | Type | Used By Insights | Purpose |
|--------|------|------------------|---------|
| `id` | `UUID` | Yes | PK |
| `name` | `VARCHAR` | Yes | Adaptor name |
| `status` | `VARCHAR` | Yes | Adaptor status |
| `created_at` | `TIMESTAMPTZ` | No | Record creation |
| `updated_at` | `TIMESTAMPTZ` | No | Last update |

### 1.9 `destination_adaptor_mapping`

Destination routing configuration for intelligence delivery.

| Column | Type | Used By Insights | Purpose |
|--------|------|------------------|---------|
| `id` | `UUID` | Yes | PK |
| `destination` | `VARCHAR` | Yes | Destination identifier |
| `receiver_adaptor_id` | `UUID` | Yes | FK → `receiver_adaptor.id` |
| `status` | `VARCHAR` | Yes | Mapping status |
| `created_at` | `TIMESTAMPTZ` | No | Record creation |
| `updated_at` | `TIMESTAMPTZ` | No | Last update |

### 1.10 `facility` (schema/01 — CDC-sourced from compliance service)

Facility roster managed by the compliance service (`FacilityService.upsertFacility()`). Defines the
denominator for facility activity metrics and the e-Buzima adoption baseline. Populated via Debezium
CDC: `PostgreSQL → Kafka (cce.public.facility) → ClickHouse`.
Engine: `ReplacingMergeTree(_version, _is_deleted)` ORDER BY `(id)` — dedup uses Debezium LSN as
version; deleted facilities are physically removed on background merge (`clean_deleted_rows = Always`).
Always read with `FINAL` to see the deduplicated, delete-purged view.

| Column | Type | Description |
|--------|------|-------------|
| `id` | `UUID` | PostgreSQL primary key — ReplacingMergeTree ORDER BY key |
| `facility_id` | `String` | HIE-assigned facility identifier — UNIQUE in source |
| `facility_name` | `String` | Display name from FHIR Reference.display |
| `expected_patients_per_day` | `UInt32` | Daily patient throughput baseline for adoption calculation; 0 = not configured |
| `created_at` | `DateTime64(6)` | Row creation timestamp from compliance service |
| `updated_at` | `DateTime64(6)` | Last update timestamp — reflects compliance service update |
| `_version` | `UInt64` | Debezium source.lsn — monotonic version for ReplacingMergeTree dedup |
| `_is_deleted` | `UInt8` | 1 when source row was DELETEd in PostgreSQL |

### 1.11 Daily KPI Materialized Views (schema/07)

Five refreshable MVs that snapshot compliance, adoption, deviation, event pipeline, and
referral KPIs every 30 seconds. (Compliance is APPEND-mode now()-snapshots; adoption, deviation,
event and referral are event_time-keyed full-recompute over a 12-month rolling window, so backdated
events self-heal.) Backing tables use `ReplacingMergeTree(refreshed_at)` with `snapshot_date` as the first
ORDER BY key, so within a day multiple refresh rows deduplicate to the latest (use `FINAL`), and
across days all snapshots are preserved.

Always filter with `FINAL` and `WHERE snapshot_date = today()` for the current day's snapshot.

**`mv_daily_compliance_kpis`** — one row per `(snapshot_date, protocol_definition_id)`

| Column | Description |
|--------|-------------|
| `snapshot_date` | Calendar day of the snapshot |
| `refreshed_at` | Refresh timestamp — version for ReplacingMergeTree dedup |
| `protocol_definition_id` | UUID of the protocol |
| `total_enrollments`, `status_active`, `status_completed`, `status_withdrawn`, `status_expired` | Enrollment status breakdown |
| `tracked_patients`, `compliant_count`, `non_compliant_count`, `compliance_rate_pct` | Patient compliance summary |
| `total_deviations`, `overdue_deviations`, `missed_deviations`, `order_violation_deviations` | Deviation breakdown |
| `step_total`, `step_completed`, `step_overdue`, `step_missed`, `step_due`, `step_pending`, `step_on_time`, `step_early`, `step_late` | Step state and timing metrics |

> **Removed:** `mv_daily_facility_kpis` and `mv_daily_facility_activity_summary` were
> dropped. The Facilities ranking is now computed live from the enrolled-patient cohort
> joined to `inbound_event_logs`, and the active-facility tiles read
> `mv_event_volume_hourly` (event_time-keyed) — see §3.9 and §3.13a.

**`mv_daily_adoption_kpis`** — one row per `(snapshot_date, facility_id)`, joined with `facility`

| Column | Description |
|--------|-------------|
| `snapshot_date` | Calendar day |
| `facility_id` | FOSA facility ID |
| `facility_name` | From `facility` |
| `expected_patients_per_day` | From `facility` |
| `actual_patients` | Distinct patients with a compliance event at this facility on this day |
| `adoption_rate_pct` | `actual_patients / expected_patients_per_day × 100` |
| `reporting_gap` | `expected_patients_per_day − actual_patients` |

**`mv_daily_deviation_kpis`** — one row per `(snapshot_date, protocol_definition_id)`

| Column | Description |
|--------|-------------|
| `snapshot_date` | Calendar day |
| `protocol_definition_id` | UUID of the protocol |
| `total_deviations`, `overdue_count`, `missed_count`, `order_violation_count` | Daily snapshot deviation counts |

> **Important — date-range queries must not SUM this MV.** Each daily row is a *stock*
> snapshot (open deviations as of that day), not a flow of new deviations, so summing
> across days double-counts deviations that remain open. The Deviations page header
> instead counts distinct rows from the `deviations` base table filtered by
> `detected_at` — see `DeviationRepository.countByTypeFiltered`. **RI-34**'s
> `findDeviationsByFacilityAndType` (the "Deviations by Facility and Type" chart) follows
> the same rule — it queries the raw `deviations` table via `mv_patient_facility_latest`,
> not this MV.

**`mv_daily_event_kpis`** — one row per `snapshot_date` (global pipeline summary)

| Column | Description |
|--------|-------------|
| `snapshot_date` | Calendar day |
| `total_events` | Total events received (from `mv_event_volume_hourly`) |
| `matched_count`, `zero_match_count`, `duplicate_count` | Processing status breakdown |
| `matched_rate_pct`, `zero_match_rate_pct` | Processing quality rates |
| `pipeline_loss_count` | `total_events − total_processed` |

**`mv_daily_referral_kpis`** — one row per `(event_time day, facility_id)`

| Column | Description |
|--------|-------------|
| `snapshot_date` | Clinical `event_time` day of the referral |
| `facility_id` | FOSA facility ID |
| `referral_count` | **Referrals received by HIE** — ACCEPTED inbound referral events on this day. Prod: an `Encounter` whose `Encounter.type[].coding[].display = 'TRANSFER_ENCOUNTER'` (ingestion-based, match-independent). Dev/demo fallback: an accepted event that completed a Referral step (the demo referral is a markerless `ServiceRequest`). Deduped so prod never double-counts. |
| `matched_count` | Of those received, the ones matched to (that completed) a Referral step — the **"compliant"** referrals. Non-compliant = `referral_count − matched_count`. |

> Keyed on clinical `event_time` (not `received_at`), so it is a functional/clinical metric.
> **Ingestion-based** (RI-35): counts every accepted transfer the HIE received, independent of whether
> the compliance engine matched it — so it no longer under-reports (transfers for not-yet-enrolled
> patients) or lags behind step matching / CDC. Backs the `GET /v1/insights/dashboard/referrals` KPI:
> Received by HIE, Compliant (matched), Non-Compliant (received − matched) and Referral Compliance Rate
> (matched ÷ received), plus a per-facility breakdown with district.

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
| `NOT_STARTED` | Step not yet activated | — |
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
| `COMPLIANT` | All steps completed on time/early, no active overdue/missed |
| `MODERATE` | One or more overdue steps (not yet missed) |
| `NON_COMPLIANT` | One or more missed steps |

### 2.6 `InboundStatus`

Status of an `inbound_event` record as it moves through the Collector pipeline.

| Value | Description |
|-------|-------------|
| `RECEIVED` | Initial state — event persisted, not yet processed |
| `ACCEPTED` | Validation passed, event published to Kafka |
| `REJECTED` | Validation or Kafka publish failed — see `rejection_reason` |
| `DUPLICATE` | Event already seen (same `cloudevents_id` + `source`) |

### 2.7 `RejectionReason`

Reason an event was rejected (stored on `inbound_event.rejection_reason`).

| Value | Description |
|-------|-------------|
| `INVALID_ENVELOPE` | Missing or invalid CloudEvents required fields |
| `INVALID_FHIR` | FHIR R4 payload failed structural validation |
| `INVALID_JSON` | Non-FHIR JSON payload is not valid JSON or is empty |
| `UNSUPPORTED_CONTENT_TYPE` | `datacontenttype` is not `application/fhir+json` or `application/json` |
| `DUPLICATE` | Duplicate `(id, source)` detected within lookback window |
| `MISSING_SUBJECT` | `subject` field missing |
| `PAYLOAD_TOO_LARGE` | Request body exceeds max-payload-size |
| `DESERIALIZATION_ERROR` | Request body could not be parsed as JSON |
| `KAFKA_PUBLISH_FAILURE` | Kafka broker unavailable or publish timed out |
| `INTERNAL_ERROR` | Unexpected failure during post-persist processing |

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

processing_status_breakdown (in events/summary):
  For each status in [MATCHED, ZERO_MATCH, DUPLICATE]:
    count = COUNT(processing_status = status)
    percentage = ROUND(count / total_events * 100, 1)
  Returns: { matched: {count, percentage}, zeroMatch: {count, percentage}, duplicate: {count, percentage} }
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
tracked_patients     = uniq(protocol_instance.patient_id) joined with mv_patient_facility_latest
                       (optionally constrained to enrolled_at in selected period)

compliance_rate      = (tracked − non_compliant) / tracked × 100 per facility
                       (non_compliant = uniq patients with any deviation in period)

active_deviations    = uniq patients with any deviation in period, computed live from the
                       same enrolled-patient cohort as compliance_rate (shared cohort keeps
                       deviations consistent with the rate; the former
                       mv_daily_facility_kpis source was dropped)

total_events         = uniq inbound_event_logs.id with status='ACCEPTED' in the period,
                       intersected with the facility reference list
                       (replaces the earlier mv_daily_facility_kpis.event_count path,
                        which only counted compliance-matched events)
```

The events column on the ranking table is intentionally sourced from
`inbound_event_logs` so it matches the Active Facilities tile and the Events → By Facility
table; otherwise a facility that submits accepted-but-unmatched HIE events would appear
active with zero ranked events.

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

Patient compliance category is binary across **all active protocol instances** at a
facility (the legacy `at_risk` middle tier was retired — every patient is either
compliant or non-compliant in the analytics surface):

```
on_track       = patient has NO deviation rows in the selected period
non_compliant  = patient has ≥1 deviation row in the selected period

percentage = category_count / total_patients_at_facility * 100
```

> The hotspot endpoint still emits a 3-tier label for backwards compatibility, but the
> Dashboard, Compliance Overview, and Patient List all use the binary model above.

> **Note:** Facility is derived by joining `protocol_instance` → `event_log.facility_id`.

> **RI-36 — Dashboard "Service Compliance" tracked cohort (event activity, not `enrolled_at`):**
> The Dashboard patient tile (`GET /dashboard/compliance-summary`, computed live in
> `DashboardService.getComplianceSummary`) no longer scopes `tracked_patients` by
> `enrolled_at`. **Tracked** = distinct `inbound_event_logs.subject` for `ACCEPTED` events
> with `event_time` in the period whose `cloudevents_id` matched a protocol
> (`compliance_event_logs.processing_status = 'MATCHED'`) — i.e. events *considered by a
> protocol*, whether they created a new enrollment or advanced an existing journey. This
> counts a patient enrolled in a prior period who is active again in the window (the old
> `enrolled_at` cohort dropped them) and excludes Consent-only / unmatched events.
> **Non-compliant** = of that cohort, patients with ≥1 deviation whose **clinical occurrence
> date** (§3.1 occurrence clock, not `detected_at`) is in range. Compliant = tracked −
> non-compliant; rate = compliant ÷ tracked. Per-protocol `mv_daily_compliance_kpis`
> (enrollment-based) is unchanged.
>
> The **Facility Ranking** compliance breakdown (`ProtocolInstanceRepository.countPatient-
> ComplianceByFacility`, backing `GET /facilities/rankings`) uses the **same** matched-event
> cohort so card and breakdown reconcile by definition. Patients are attributed to their
> current facility via `mv_patient_facility_latest`; a tracked patient with no resolved
> facility there appears in the country card but not in any facility row, so the breakdown's
> tracked total can be lower than the card's country total (facility-attribution gap, not
> double-counting).
>
> The `GET /protocols[/{id}]/compliance-summary` endpoints take a `dateFilterMode`:
> **`enrollment`** (default, cohort = `enrolled_at` in range) and **`eventTime`** — user-facing
> label **"Clinical Event Date"** (RI-36). In `eventTime` mode the all-protocols patient block
> reuses the exact card queries (reconciles with the card); the per-protocol path uses
> `findByProtocolDefinitionIdWithActivityBetween`, now keyed on clinical **`event_time`** of a
> protocol-MATCHED event (was step `updated_at`, a system write time). Because
> `compliance_event_logs` has no protocol column (only `cloudevents_id` / `processing_status` /
> `correlation_id`, and `correlation_id` matches neither `protocol_instances.id` nor
> `step_instances.id`), per-protocol `eventTime` is "enrolled in protocol X **AND** has a matched
> event in range", not "events matched to protocol X".
>
> **Naming (important):** `eventTime` / "Clinical Event Date" is the CLINICAL event clock
> (`inbound_event_logs.event_time`). It is **distinct** from the system `updated_at` "activity"
> that the Patient detail page's activity log shows (when CCE processed a record) — the value was
> renamed from `activity`→`eventTime` and the label to "Clinical Event Date" specifically to avoid
> that confusion. The **Compliance Overview** page has no toggle (always `eventTime`); the
> **Patients** page has the Enrollment / Clinical Event Date radio (default Clinical Event Date)
> and shares this same query.

### 3.13a Facility Activity Formulas

A facility is **active** if it has ≥1 `ACCEPTED` inbound event (event_time-keyed) in the
period — the same "any accepted event" definition eBuzima Adoption's actual-visits count uses
(`mv_daily_adoption_kpis`), so a facility with recorded activity is never shown Inactive just
because the compliance-matching pipeline hasn't (or never will) match that event to a protocol
step.

> **Changed (RI-62):** this previously required the event to *also* be matched to a protocol
> (`compliance_event_logs.processing_status='MATCHED'`), on the theory that "active" should mean
> "contributing to a tracked care journey" (keeping it consistent with the Facility Ranking
> "tracked patients" cohort). That made Active/Inactive diverge from Adoption whenever the
> compliance-matching pipeline lagged or had a gap: a facility with real, recorded eBuzima visits
> (`ACCEPTED`, unmatched) would show "Inactive" while its own Adoption row showed non-zero Actual
> Visits — confusing, and arguably wrong, since connectivity/activity and protocol-tracking are
> different concepts. Active is now accepted-only again, matching Adoption; "tracked patients" on
> the Ranking table remains matched-only (unchanged) since that's a genuinely different question
> ("is this patient in a tracked care journey", not "did this facility transmit anything").

```
total_in_scope       = COUNT(*) FROM facility FINAL WHERE _is_deleted = 0

active_facilities    = uniq(facility_id) FROM inbound_event_logs
                       WHERE status = 'ACCEPTED' AND facility_id != ''
                         AND facility_id IN (facility reference)
                         AND toDate(event_time) BETWEEN startDate AND endDate
                       (toDate(event_time) = today() when no range)

inactive_facilities  = total_in_scope − active_facilities

active_facility_rate = active_facilities / total_in_scope × 100
```

> Note: `inactive` now means "no accepted events at all" in the period — same population as
> zero `totalEvents` on the Facility Ranking table and zero Actual Visits on Adoption.

> The denominator (`total_in_scope`) comes from `facility`, not from observed event data.
> This ensures facilities that transmitted no events in the period are counted as inactive
> (not omitted).

**RI-29 drill-down detail** (`GET /v1/insights/facilities/activity-detail`, one row per
in-scope facility — see API reference §9.3):

```
active[facility]        = facility_id ∈ active_facilities (same definition as above,
                           scoped to [startDate, endDate])

last_activity[facility] = toString(max(toDate(hour))) FROM mv_event_volume_hourly
                           WHERE facility_id = :facility_id AND toDate(hour) <= endDate
                           (NO lower bound — unlike `active`, which IS bounded by startDate)
```

> `last_activity` deliberately has no start-date floor: an inactive-in-period facility
> that transmitted before the window still shows its true last-seen day instead of a
> blank. It is `null` only for a facility never active up to `endDate`. The per-facility
> rows (facility name, district, active flag, last activity) are combined in Java rather
> than via a `LEFT JOIN`, to avoid ClickHouse filling unmatched join columns with
> zero-value defaults instead of `NULL`.

### 3.13b e-Buzima Adoption Formulas (mv_daily_adoption_kpis)

```
actual_patients   = COUNT(DISTINCT patient_id) FROM compliance_event_logs
                    WHERE facility_id = :facility_id AND toDate(event_time) = snapshot_date

adoption_rate_pct = actual_patients / expected_patients_per_day × 100
                    (0 when expected_patients_per_day = 0)

reporting_gap     = expected_patients_per_day − actual_patients
                    (negative = over-reporting; positive = under-reporting)
```

Multi-day (date-range) aggregation (via `DailyKpiRepository.getAdoptionKpisByDateRange`):

**RI-33 — period totals, not per-day.** The API response reports the expected and actual
visit counts **over the whole selected period**. The expected figure scales with the number
of days selected (baseline/day × days), so it is no longer a fixed per-1-day number.

```
calendar_days   = (endDate − startDate) + 1
baseline_per_day = max(expected_patients_per_day)
total_actual    = SUM(actual_patients) across MV rows in range

expectedVisits  = baseline_per_day × calendar_days
actualVisits    = round(total_actual)
reportingGap    = expectedVisits − actualVisits
adoptionRate(%) = total_actual / (baseline_per_day × calendar_days) × 100
                  (forced to 0 when baseline_per_day = 0 AND actualVisits = 0)
```

> For a single-day snapshot (`calendar_days = 1`) `expectedVisits` equals the daily
> baseline, so the today view is unchanged. Days without an MV row simply contribute `0`
> to `total_actual` — a facility with sparse reporting is not over-credited.
>
> **Zero-baseline override:** a facility with no expected baseline and no actual visits
> reports `adoptionRate = 0.0`, never a vacuous `100.0` — an unbaselined, inactive
> facility hasn't "adopted" anything. Separately, a facility with **no adoption row at
> all** for the period (`emptyAdoptionDto`) always reports `adoptionRate = 0.0` too,
> regardless of whether it has an expected baseline (previously this case returned
> `100.0` when `expected = 0`, which read as a false "fully adopted").

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
