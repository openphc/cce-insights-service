-- ============================================================
-- Demo Data Corrections
-- a. ANC Visit # and Referral Initiated steps: same Nurse/CHW practitioner
-- b. Referral Consultation steps: source = openMRS
-- c. Only Referral Consultation + Referral Closure get Doctor practitioner
-- d. Rename 'Referral' -> 'Referral Initiated'
-- e. Rename 'Referral Ack' -> 'Referral Closure'
-- ============================================================

-- d/e: Update protocol definition step titles
UPDATE protocol_definition
SET definition = REPLACE(
    REPLACE(
        REPLACE(
            REPLACE(
                REPLACE(
                    REPLACE(definition::text,
                        '"title": "ANC Visit 1 Referral Ack"', '"title": "ANC Visit 1 Referral Closure"'),
                    '"title": "ANC Visit 2 Referral Ack"', '"title": "ANC Visit 2 Referral Closure"'),
                '"title": "ANC Visit 3 Referral Ack"', '"title": "ANC Visit 3 Referral Closure"'),
            '"title": "ANC Visit 1 Referral"', '"title": "ANC Visit 1 Referral Initiated"'),
        '"title": "ANC Visit 2 Referral"', '"title": "ANC Visit 2 Referral Initiated"'),
    '"title": "ANC Visit 3 Referral"', '"title": "ANC Visit 3 Referral Initiated"')::jsonb
WHERE url = 'http://mdtlabs.com/PlanDefinition/rmnch-protocol';

-- b: Fix source for Referral Consultation steps
UPDATE event_log
SET source = 'openMRS'
WHERE action_id IN (
    'anc-visit-1-referral-consultation',
    'anc-visit-2-referral-consultation',
    'anc-visit-3-referral-consultation'
);

-- c: For non-doctor steps (everything except referral-consultation and referral-ack),
--    replace doctor practitioners with the facility's assigned nurse/CHW.
--    This updates the JSON data field's participant/performer practitioner reference.

-- Update ANC Visit # Referral steps to match their parent ANC Visit practitioner
UPDATE event_log ref
SET data = jsonb_set(
    ref.data,
    '{performer,0}',
    (SELECT jsonb_build_object('display', parent.data->'participant'->0->'individual'->>'display',
                               'reference', parent.data->'participant'->0->'individual'->>'reference')
     FROM event_log parent
     WHERE parent.protocol_instance_id = ref.protocol_instance_id
       AND parent.action_id = REPLACE(ref.action_id, '-referral', '')
       AND parent.processing_status = 'MATCHED'
     LIMIT 1)
)
WHERE ref.action_id IN ('anc-visit-1-referral', 'anc-visit-2-referral', 'anc-visit-3-referral')
  AND ref.processing_status = 'MATCHED';

-- For registration, pregnancy-profile, anc-visit-#, pregnancy-outcome, pnc:
-- Replace any Doctor with a Nurse/CHW based on facility assignment
-- (This uses a deterministic mapping: facility -> first available nurse/chw)
UPDATE event_log
SET data = jsonb_set(
    data,
    '{participant,0,individual}',
    jsonb_build_object(
        'display', CASE facility_id
            WHEN 'facility-bo' THEN 'CHW Tenneh Bah'
            WHEN 'facility-makeni' THEN 'Nurse Fatmata Koroma'
            WHEN 'facility-kenema' THEN 'Nurse Hawa Jalloh'
            WHEN 'facility-kailahun' THEN 'Nurse Isatu Bangura'
            WHEN 'facility-moyamba' THEN 'CHW Mariama Conteh'
            WHEN 'facility-tonkolili' THEN 'Nurse Adama Turay'
            WHEN 'facility-pujehun' THEN 'Nurse Fatmata Koroma'
            WHEN 'facility-bonthe' THEN 'CHW Tenneh Bah'
            WHEN 'facility-kono' THEN 'Nurse Hawa Jalloh'
            WHEN 'facility-freetown-west' THEN 'CHW Mariama Conteh'
            WHEN 'facility-freetown-east' THEN 'Nurse Isatu Bangura'
            WHEN 'facility-kambia' THEN 'Nurse Adama Turay'
            ELSE 'CHW Mariama Conteh'
        END,
        'reference', CASE facility_id
            WHEN 'facility-bo' THEN 'Practitioner/1010'
            WHEN 'facility-makeni' THEN 'Practitioner/1003'
            WHEN 'facility-kenema' THEN 'Practitioner/1007'
            WHEN 'facility-kailahun' THEN 'Practitioner/1004'
            WHEN 'facility-moyamba' THEN 'Practitioner/1005'
            WHEN 'facility-tonkolili' THEN 'Practitioner/1009'
            WHEN 'facility-pujehun' THEN 'Practitioner/1003'
            WHEN 'facility-bonthe' THEN 'Practitioner/1010'
            WHEN 'facility-kono' THEN 'Practitioner/1007'
            WHEN 'facility-freetown-west' THEN 'Practitioner/1005'
            WHEN 'facility-freetown-east' THEN 'Practitioner/1004'
            WHEN 'facility-kambia' THEN 'Practitioner/1009'
            ELSE 'Practitioner/1005'
        END
    )
)
WHERE action_id IN ('registration', 'pregnancy-profile', 'anc-visit-1', 'anc-visit-2', 'anc-visit-3', 'pregnancy-outcome', 'pnc')
  AND processing_status = 'MATCHED'
  AND data->'participant'->0->'individual'->>'display' LIKE 'Dr.%';

-- ============================================================
-- f. Add relatedArtifact to protocol definition
-- ============================================================
UPDATE protocol_definition
SET definition = jsonb_set(
    definition,
    '{relatedArtifact}',
    '[{"type": "documentation", "label": "Reference Guideline", "display": "External clinical guideline", "url": "https://iris.who.int/server/api/core/bitstreams/0affb504-e80a-42db-9b53-7c442d4c72f2/content"}, {"type": "thumbnail", "label": "Thumbnail Image", "display": "Protocol Thumbnail Image", "url": "https://drive.google.com/file/d/1hr9FQjvpdvRIUpK2-tFhITMSOBQD0fq-/view?usp=sharing"}]'::jsonb
)
WHERE id = 'f8f9dfc2-8ef7-4434-aff8-e3621c1b99ad';
