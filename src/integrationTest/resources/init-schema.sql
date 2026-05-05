-- Schema for CCE Insights Service integration tests
-- Mirrors the Compliance Service schema (read-only)

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE protocol_definition (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    url VARCHAR(512) NOT NULL,
    version VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    definition JSONB,
    loaded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(url, version)
);

CREATE TABLE protocol_instance (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    protocol_definition_id UUID NOT NULL REFERENCES protocol_definition(id),
    patient_id VARCHAR(128) NOT NULL,
    protocol_canonical VARCHAR(600) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    enrolled_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE step_instance (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    protocol_instance_id UUID NOT NULL REFERENCES protocol_instance(id),
    action_id VARCHAR(256) NOT NULL,
    repeat_index INT NOT NULL DEFAULT 0,
    state VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    due_date TIMESTAMPTZ,
    overdue_date TIMESTAMPTZ,
    missed_date TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    completed_by_source VARCHAR(256),
    completion_status VARCHAR(32)
);

CREATE TABLE deviation (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    protocol_instance_id UUID NOT NULL REFERENCES protocol_instance(id),
    step_instance_id UUID NOT NULL REFERENCES step_instance(id),
    deviation_type VARCHAR(32) NOT NULL,
    detected_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    metadata JSONB
);

CREATE TABLE event_log (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    cloudevents_id VARCHAR(256),
    subject VARCHAR(512),
    type VARCHAR(256),
    event_time TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    source VARCHAR(256),
    data JSONB,
    processing_status VARCHAR(32) DEFAULT 'MATCHED',
    facility_id VARCHAR(64),
    protocol_instance_id UUID,
    protocol_definition_id UUID,
    action_id VARCHAR(256),
    matched_step_instance_id UUID
);

CREATE TABLE inbound_event (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    cloudevents_id VARCHAR(50) NOT NULL,
    source VARCHAR(100) NOT NULL,
    type VARCHAR(100) NOT NULL,
    spec_version VARCHAR(10) NOT NULL DEFAULT '1.0',
    subject VARCHAR(100),
    event_time TIMESTAMPTZ,
    data_content_type VARCHAR(50) NOT NULL,
    facility_id VARCHAR(100),
    correlation_id VARCHAR(100),
    source_event_id VARCHAR(100),
    raw_payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',
    rejection_reason VARCHAR(50),
    error_details TEXT,
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(cloudevents_id, source)
);

CREATE INDEX idx_inbound_event_subject ON inbound_event(subject);
CREATE INDEX idx_inbound_event_source ON inbound_event(source);
CREATE INDEX idx_inbound_event_status ON inbound_event(status);
CREATE INDEX idx_inbound_event_received ON inbound_event(received_at);

-- Intelligence Service tables (read-only from insights perspective)

CREATE TABLE receiver_adaptor (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(256) NOT NULL,
    definition JSONB,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    config JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE destination_adaptor_mapping (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    destination VARCHAR(512) NOT NULL,
    receiver_adaptor_id UUID NOT NULL REFERENCES receiver_adaptor(id),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE intelligence_delivery (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    intelligence_event_id UUID NOT NULL,
    action_definition_id UUID,
    destination_adaptor_mapping_id UUID REFERENCES destination_adaptor_mapping(id),
    action_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    subject VARCHAR(512),
    protocol_canonical VARCHAR(600),
    action_id VARCHAR(256),
    severity VARCHAR(16),
    destination VARCHAR(512),
    attempt_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    delivered_at TIMESTAMPTZ
);
