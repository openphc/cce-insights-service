-- ============================================================
-- CCE Insights Demo Schema (RMNCH Protocol Only)
-- Tables used by: cce-insights-service, cce-insights-ui
-- ============================================================

-- Drop in reverse dependency order
DROP TABLE IF EXISTS deviation CASCADE;
DROP TABLE IF EXISTS step_instance CASCADE;
DROP TABLE IF EXISTS event_log CASCADE;
DROP TABLE IF EXISTS protocol_instance CASCADE;
DROP TABLE IF EXISTS trigger_index CASCADE;
DROP TABLE IF EXISTS protocol_definition CASCADE;

-- ============================================================
-- protocol_definition
-- ============================================================
CREATE TABLE protocol_definition (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    url        VARCHAR NOT NULL,
    version    VARCHAR NOT NULL,
    status     VARCHAR NOT NULL,
    definition JSONB   NOT NULL,
    loaded_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT protocol_definition_url_version_key UNIQUE (url, version),
    CONSTRAINT protocol_definition_status_check CHECK (status IN ('ACTIVE', 'RETIRED'))
);

CREATE INDEX idx_protocol_definition_triggers ON protocol_definition USING gin (definition jsonb_path_ops);

-- ============================================================
-- protocol_instance
-- ============================================================
CREATE TABLE protocol_instance (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id             VARCHAR NOT NULL,
    protocol_canonical     VARCHAR NOT NULL,
    protocol_definition_id UUID NOT NULL REFERENCES protocol_definition(id),
    enrolled_at            TIMESTAMPTZ NOT NULL,
    status                 VARCHAR NOT NULL,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT protocol_instance_status_check CHECK (status IN ('ACTIVE', 'COMPLETED', 'WITHDRAWN', 'EXPIRED'))
);

CREATE INDEX idx_protocol_instance_patient ON protocol_instance (patient_id);
CREATE INDEX idx_protocol_instance_status ON protocol_instance (status) WHERE status = 'ACTIVE';

-- ============================================================
-- step_instance
-- ============================================================
CREATE TABLE step_instance (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    protocol_instance_id UUID NOT NULL REFERENCES protocol_instance(id),
    action_id            VARCHAR NOT NULL,
    repeat_index         INTEGER NOT NULL DEFAULT 0,
    state                VARCHAR NOT NULL,
    due_date             TIMESTAMPTZ,
    overdue_date         TIMESTAMPTZ,
    missed_date          TIMESTAMPTZ,
    completed_at         TIMESTAMPTZ,
    completed_by_source  VARCHAR,
    completion_status    VARCHAR,
    matched_event_id     UUID,
    required_behavior    VARCHAR,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT step_instance_state_check CHECK (state IN ('PENDING', 'DUE', 'OVERDUE', 'MISSED', 'COMPLETED', 'SKIPPED')),
    CONSTRAINT step_instance_completion_status_check CHECK (completion_status IN ('EARLY', 'ON_TIME', 'LATE')),
    CONSTRAINT step_instance_required_behavior_check CHECK (required_behavior IN ('must', 'could', 'must-unless-documented'))
);

CREATE INDEX idx_step_instance_protocol ON step_instance (protocol_instance_id);
CREATE INDEX idx_step_instance_state ON step_instance (state) WHERE state IN ('PENDING', 'DUE', 'OVERDUE');
CREATE INDEX idx_step_instance_due_date ON step_instance (due_date) WHERE state IN ('PENDING', 'DUE', 'OVERDUE');

-- ============================================================
-- event_log
-- ============================================================
CREATE TABLE event_log (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cloudevents_id           VARCHAR NOT NULL,
    source                   VARCHAR NOT NULL,
    source_event_id          VARCHAR,
    subject                  VARCHAR NOT NULL,
    type                     VARCHAR NOT NULL,
    event_time               TIMESTAMPTZ NOT NULL,
    received_at              TIMESTAMPTZ NOT NULL,
    correlation_id           VARCHAR NOT NULL,
    data                     JSONB NOT NULL,
    protocol_instance_id     UUID,
    protocol_definition_id   UUID,
    action_id                VARCHAR,
    facility_id              VARCHAR,
    processing_status        VARCHAR NOT NULL,
    matched_step_instance_id UUID,
    CONSTRAINT event_log_cloudevents_id_source_key UNIQUE (cloudevents_id, source),
    CONSTRAINT event_log_processing_status_check CHECK (processing_status IN ('MATCHED', 'ZERO_MATCH', 'DUPLICATE'))
);

CREATE INDEX idx_event_log_subject ON event_log (subject);
CREATE INDEX idx_event_log_facility ON event_log (facility_id) WHERE facility_id IS NOT NULL;
CREATE UNIQUE INDEX idx_event_log_source_sourceeventid ON event_log (source, source_event_id) WHERE source_event_id IS NOT NULL;

-- ============================================================
-- deviation
-- ============================================================
CREATE TABLE deviation (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    protocol_instance_id  UUID NOT NULL REFERENCES protocol_instance(id),
    step_instance_id      UUID NOT NULL REFERENCES step_instance(id),
    deviation_type        VARCHAR NOT NULL,
    detected_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    intelligence_event_id UUID,
    metadata              JSONB,
    CONSTRAINT deviation_type_check CHECK (deviation_type IN ('OVERDUE', 'MISSED', 'ORDER_VIOLATION'))
);

CREATE INDEX idx_deviation_protocol ON deviation (protocol_instance_id);
CREATE INDEX idx_deviation_type ON deviation (deviation_type);

-- ============================================================
-- trigger_index (used by compliance service, needed for FK)
-- ============================================================
CREATE TABLE trigger_index (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    protocol_definition_id UUID NOT NULL REFERENCES protocol_definition(id),
    action_id              VARCHAR NOT NULL,
    resource_type          VARCHAR NOT NULL,
    trigger_name           VARCHAR NOT NULL,
    filter_json            JSONB,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
