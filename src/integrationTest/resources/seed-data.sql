-- Seed data for integration tests

-- Protocol Definition
INSERT INTO protocol_definition (id, url, version, status, definition) VALUES
('550e8400-e29b-41d4-a716-446655440000', 'http://openphc.org/fhir/PlanDefinition/anc-high-risk', '2.1', 'active',
 '{"resourceType":"PlanDefinition","action":[{"id":"anc-visit-1"},{"id":"anc-visit-2"},{"id":"anc-visit-3"}]}');

-- Protocol Instances (3 patients)
INSERT INTO protocol_instance (id, protocol_definition_id, patient_id, protocol_canonical, status, enrolled_at) VALUES
('660e8400-e29b-41d4-a716-446655440001', '550e8400-e29b-41d4-a716-446655440000', '260225-0002-5501',
 'http://openphc.org/fhir/PlanDefinition/anc-high-risk|2.1', 'ACTIVE', '2026-01-15T10:00:00Z'),
('660e8400-e29b-41d4-a716-446655440002', '550e8400-e29b-41d4-a716-446655440000', '260225-0002-5502',
 'http://openphc.org/fhir/PlanDefinition/anc-high-risk|2.1', 'ACTIVE', '2026-01-20T08:00:00Z'),
('660e8400-e29b-41d4-a716-446655440003', '550e8400-e29b-41d4-a716-446655440000', '260225-0002-5503',
 'http://openphc.org/fhir/PlanDefinition/anc-high-risk|2.1', 'COMPLETED', '2026-01-10T09:00:00Z');

-- Step Instances for Patient 1 (on_track — 2 completed, 1 pending)
INSERT INTO step_instance (id, protocol_instance_id, action_id, repeat_index, state, due_date, completed_at, completed_by_source, completion_status) VALUES
('770e8400-e29b-41d4-a716-446655440001', '660e8400-e29b-41d4-a716-446655440001', 'anc-visit-1', 0, 'COMPLETED',
 '2026-01-20T00:00:00Z', '2026-01-20T09:30:00Z', 'ebuzima/kigali-south', 'ON_TIME'),
('770e8400-e29b-41d4-a716-446655440002', '660e8400-e29b-41d4-a716-446655440001', 'anc-visit-2', 0, 'COMPLETED',
 '2026-02-15T00:00:00Z', '2026-02-14T10:00:00Z', 'ebuzima/kigali-south', 'EARLY'),
('770e8400-e29b-41d4-a716-446655440003', '660e8400-e29b-41d4-a716-446655440001', 'anc-visit-3', 0, 'PENDING',
 '2026-03-10T00:00:00Z', NULL, NULL, NULL);

-- Step Instances for Patient 2 (at_risk — 1 completed, 1 overdue, 1 pending)
INSERT INTO step_instance (id, protocol_instance_id, action_id, repeat_index, state, due_date, overdue_date, completed_at, completed_by_source, completion_status) VALUES
('770e8400-e29b-41d4-a716-446655440004', '660e8400-e29b-41d4-a716-446655440002', 'anc-visit-1', 0, 'COMPLETED',
 '2026-01-25T00:00:00Z', NULL, '2026-01-27T11:00:00Z', 'rhie-mediator', 'LATE'),
('770e8400-e29b-41d4-a716-446655440005', '660e8400-e29b-41d4-a716-446655440002', 'anc-visit-2', 0, 'OVERDUE',
 '2026-02-20T00:00:00Z', '2026-02-25T00:00:00Z', NULL, NULL, NULL),
('770e8400-e29b-41d4-a716-446655440006', '660e8400-e29b-41d4-a716-446655440002', 'anc-visit-3', 0, 'PENDING',
 '2026-03-15T00:00:00Z', NULL, NULL, NULL, NULL);

-- Step Instances for Patient 3 (non_compliant — 2 completed, 1 missed)
INSERT INTO step_instance (id, protocol_instance_id, action_id, repeat_index, state, due_date, missed_date, completed_at, completed_by_source, completion_status) VALUES
('770e8400-e29b-41d4-a716-446655440007', '660e8400-e29b-41d4-a716-446655440003', 'anc-visit-1', 0, 'COMPLETED',
 '2026-01-15T00:00:00Z', NULL, '2026-01-15T10:30:00Z', 'ebuzima/kigali-south', 'ON_TIME'),
('770e8400-e29b-41d4-a716-446655440008', '660e8400-e29b-41d4-a716-446655440003', 'anc-visit-2', 0, 'COMPLETED',
 '2026-02-10T00:00:00Z', NULL, '2026-02-12T09:00:00Z', 'rhie-mediator', 'LATE'),
('770e8400-e29b-41d4-a716-446655440009', '660e8400-e29b-41d4-a716-446655440003', 'anc-visit-3', 0, 'MISSED',
 '2026-03-05T00:00:00Z', '2026-03-12T00:00:00Z', NULL, NULL, NULL);

-- Deviations
INSERT INTO deviation (id, protocol_instance_id, step_instance_id, deviation_type, detected_at) VALUES
('880e8400-e29b-41d4-a716-446655440001', '660e8400-e29b-41d4-a716-446655440002', '770e8400-e29b-41d4-a716-446655440005',
 'OVERDUE', '2026-02-25T00:00:05Z'),
('880e8400-e29b-41d4-a716-446655440002', '660e8400-e29b-41d4-a716-446655440003', '770e8400-e29b-41d4-a716-446655440009',
 'MISSED', '2026-03-12T00:00:05Z');

-- Event Log entries
INSERT INTO event_log (id, cloudevents_id, subject, type, event_time, source, data, processing_status, facility_id, protocol_instance_id, protocol_definition_id, action_id, matched_step_instance_id) VALUES
('990e8400-e29b-41d4-a716-446655440001', 'ce-001', 'Patient/260225-0002-5501', 'org.openphc.fhir.Encounter.create',
 '2026-01-20T09:30:00Z', 'ebuzima/kigali-south',
 '{"resourceType":"Encounter","participant":[{"individual":{"reference":"Practitioner/HLC-PRAC-2025-00005","display":"Dr. Aziz Muhammed"}}]}',
 'MATCHED', '0002', '660e8400-e29b-41d4-a716-446655440001', '550e8400-e29b-41d4-a716-446655440000', 'anc-visit-1', '770e8400-e29b-41d4-a716-446655440001'),
('990e8400-e29b-41d4-a716-446655440002', 'ce-002', 'Patient/260225-0002-5501', 'org.openphc.fhir.Observation.create',
 '2026-01-20T09:35:00Z', 'ebuzima/kigali-south',
 '{"resourceType":"Observation","performer":[{"reference":"Practitioner/HLC-PRAC-2025-00005","display":"Dr. Aziz Muhammed"}]}',
 'MATCHED', '0002', '660e8400-e29b-41d4-a716-446655440001', '550e8400-e29b-41d4-a716-446655440000', 'anc-visit-1', '770e8400-e29b-41d4-a716-446655440001'),
('990e8400-e29b-41d4-a716-446655440003', 'ce-003', 'Patient/260225-0002-5502', 'org.openphc.fhir.Encounter.create',
 '2026-01-27T11:00:00Z', 'rhie-mediator',
 '{"resourceType":"Encounter","participant":[{"individual":{"reference":"Practitioner/f830114a","display":"Dr. Marie Uwimana"}}]}',
 'MATCHED', '0015', '660e8400-e29b-41d4-a716-446655440002', '550e8400-e29b-41d4-a716-446655440000', 'anc-visit-1', '770e8400-e29b-41d4-a716-446655440004'),
('990e8400-e29b-41d4-a716-446655440004', 'ce-004', 'Patient/260225-0002-5503', 'org.openphc.fhir.Encounter.create',
 '2026-01-15T10:30:00Z', 'ebuzima/kigali-south',
 '{"resourceType":"Encounter","participant":[{"individual":{"reference":"Practitioner/HLC-PRAC-2025-00005","display":"Dr. Aziz Muhammed"}}]}',
 'MATCHED', '0002', '660e8400-e29b-41d4-a716-446655440003', '550e8400-e29b-41d4-a716-446655440000', 'anc-visit-1', '770e8400-e29b-41d4-a716-446655440007'),
('990e8400-e29b-41d4-a716-446655440005', 'ce-005', 'Patient/260225-0002-9999', 'org.openphc.fhir.Condition.create',
 '2026-02-01T08:00:00Z', 'rhie-mediator',
 '{"resourceType":"Condition","asserter":{"reference":"Practitioner/HLC-PRAC-2025-00010","display":"Dr. Jean"}}',
 'ZERO_MATCH', '0002', NULL, NULL, NULL, NULL),
('990e8400-e29b-41d4-a716-446655440006', 'ce-006', 'Patient/260225-0002-5501', 'org.openphc.fhir.Encounter.create',
 '2026-01-20T09:30:00Z', 'ebuzima/kigali-south',
 '{"resourceType":"Encounter"}',
 'DUPLICATE', '0002', NULL, NULL, NULL, NULL);

-- Inbound Event entries (Collector audit log)
-- ACCEPTED events (matching event_log records)
INSERT INTO inbound_event (id, cloudevents_id, source, type, subject, event_time, data_content_type, facility_id, raw_payload, status, received_at) VALUES
('aa0e8400-e29b-41d4-a716-446655440001', 'ce-001', 'ebuzima/kigali-south', 'org.openphc.fhir.Encounter.create',
 'Patient/260225-0002-5501', '2026-01-20T09:30:00Z', 'application/fhir+json', '0002',
 '{"data":{"resourceType":"Encounter","participant":[{"individual":{"reference":"Practitioner/HLC-PRAC-2025-00005"}}]}}',
 'ACCEPTED', '2026-01-20T09:30:02Z'),
('aa0e8400-e29b-41d4-a716-446655440002', 'ce-002', 'ebuzima/kigali-south', 'org.openphc.fhir.Observation.create',
 'Patient/260225-0002-5501', '2026-01-20T09:35:00Z', 'application/fhir+json', '0002',
 '{"data":{"resourceType":"Observation"}}',
 'ACCEPTED', '2026-01-20T09:35:01Z'),
('aa0e8400-e29b-41d4-a716-446655440003', 'ce-003', 'rhie-mediator', 'org.openphc.fhir.Encounter.create',
 'Patient/260225-0002-5502', '2026-01-27T11:00:00Z', 'application/fhir+json', '0015',
 '{"data":{"resourceType":"Encounter"}}',
 'ACCEPTED', '2026-01-27T11:00:03Z'),
('aa0e8400-e29b-41d4-a716-446655440004', 'ce-004', 'ebuzima/kigali-south', 'org.openphc.fhir.Encounter.create',
 'Patient/260225-0002-5503', '2026-01-15T10:30:00Z', 'application/fhir+json', '0002',
 '{"data":{"resourceType":"Encounter"}}',
 'ACCEPTED', '2026-01-15T10:30:01Z'),
('aa0e8400-e29b-41d4-a716-446655440005', 'ce-005', 'rhie-mediator', 'org.openphc.fhir.Condition.create',
 'Patient/260225-0002-9999', '2026-02-01T08:00:00Z', 'application/fhir+json', '0002',
 '{"data":{"resourceType":"Condition"}}',
 'ACCEPTED', '2026-02-01T08:00:02Z');

-- REJECTED events
INSERT INTO inbound_event (id, cloudevents_id, source, type, subject, event_time, data_content_type, facility_id, raw_payload, status, rejection_reason, error_details, received_at) VALUES
('aa0e8400-e29b-41d4-a716-446655440010', 'ce-rej-001', 'ebuzima/kigali-south', 'org.openphc.fhir.Encounter.create',
 'Patient/260225-0002-5501', '2026-02-10T14:00:00Z', 'application/fhir+json', '0002',
 '{"data":{"resourceType":"Encounter","status":"invalid-value"}}',
 'REJECTED', 'INVALID_FHIR', 'Encounter.status: invalid value', '2026-02-10T14:00:01Z'),
('aa0e8400-e29b-41d4-a716-446655440011', 'ce-rej-002', 'rhie-mediator', 'org.openphc.fhir.Observation.create',
 NULL, '2026-02-12T09:00:00Z', 'application/fhir+json', '0015',
 '{"data":{"resourceType":"Observation"}}',
 'REJECTED', 'MISSING_SUBJECT', 'subject field is required', '2026-02-12T09:00:02Z'),
('aa0e8400-e29b-41d4-a716-446655440012', 'ce-rej-003', 'ebuzima/kigali-south', 'org.openphc.fhir.Encounter.create',
 'Patient/260225-0002-5502', '2026-02-15T11:00:00Z', 'text/plain', '0002',
 '{"data":"not a json object"}',
 'REJECTED', 'UNSUPPORTED_CONTENT_TYPE', 'datacontenttype must be application/fhir+json or application/json', '2026-02-15T11:00:01Z');

-- DUPLICATE events
INSERT INTO inbound_event (id, cloudevents_id, source, type, subject, event_time, data_content_type, facility_id, raw_payload, status, received_at) VALUES
('aa0e8400-e29b-41d4-a716-446655440020', 'ce-006', 'ebuzima/kigali-south', 'org.openphc.fhir.Encounter.create',
 'Patient/260225-0002-5501', '2026-01-20T09:30:00Z', 'application/fhir+json', '0002',
 '{"data":{"resourceType":"Encounter"}}',
 'DUPLICATE', '2026-01-20T09:30:05Z');

-- Cross-source overlapping event (same patient, same event via both sources — for source comparison testing)
INSERT INTO inbound_event (id, cloudevents_id, source, type, subject, event_time, data_content_type, facility_id, raw_payload, status, received_at) VALUES
('aa0e8400-e29b-41d4-a716-446655440030', 'ce-cross-001', 'rhie-mediator', 'org.openphc.fhir.Encounter.create',
 'Patient/260225-0002-5501', '2026-01-20T09:30:30Z', 'application/fhir+json', '0002',
 '{"data":{"resourceType":"Encounter"}}',
 'ACCEPTED', '2026-01-20T09:30:32Z');

-- Pipeline loss event (ACCEPTED by collector but NOT in event_log)
INSERT INTO inbound_event (id, cloudevents_id, source, type, subject, event_time, data_content_type, facility_id, raw_payload, status, received_at) VALUES
('aa0e8400-e29b-41d4-a716-446655440040', 'ce-lost-001', 'rhie-mediator', 'org.openphc.fhir.Encounter.create',
 'Patient/260225-0002-5504', '2026-02-20T15:00:00Z', 'application/fhir+json', '0015',
 '{"data":{"resourceType":"Encounter"}}',
 'ACCEPTED', '2026-02-20T15:00:02Z');
