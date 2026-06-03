#!/usr/bin/env python3
"""
Generate demo data SQL for 367 additional RMNCH patients.
Patients: NID-2026-061 to NID-2026-427
Dates: Dec 2025 - May 2026 (all before June 2026)
Facilities: 12 Sierra Leone health facilities
"""
import uuid
import random
from datetime import datetime, timedelta, timezone

random.seed(42)

# Constants
PROTOCOL_DEF_ID = 'f8f9dfc2-8ef7-4434-aff8-e3621c1b99ad'
PROTOCOL_CANONICAL = 'http://mdtlabs.com/PlanDefinition/rmnch-protocol|1.0.0'

FACILITIES = [
    ('facility-bo', 'Bo Government Hospital'),
    ('facility-freetown', 'Connaught Hospital Freetown'),
    ('facility-kenema', 'Kenema Government Hospital'),
    ('facility-makeni', 'Makeni Regional Hospital'),
    ('facility-bonthe', 'Bonthe District Hospital'),
    ('facility-kailahun', 'Kailahun Government Hospital'),
    ('facility-moyamba', 'Moyamba District Hospital'),
    ('facility-kambia', 'Kambia District Hospital'),
    ('facility-port', 'Port Loko Government Hospital'),
    ('facility-pujehun', 'Pujehun District Hospital'),
    ('facility-tonkolili', 'Tonkolili District Hospital'),
]

PRACTITIONERS = [
    ('1001', 'Dr. Aminata Kamara'),
    ('1002', 'Dr. Mohamed Sesay'),
    ('1003', 'Nurse Fatmata Koroma'),
    ('1004', 'Midwife Isatu Bangura'),
    ('1005', 'CHW Mariama Conteh'),
    ('1006', 'Dr. Ibrahim Koroma'),
    ('1007', 'Nurse Kadiatu Mansaray'),
    ('1008', 'Dr. Sahr Lebbie'),
    ('1009', 'Nurse Adama Turay'),
    ('1010', 'CHW Tenneh Bah'),
]

FIRST_NAMES = [
    'Aminata', 'Fatmata', 'Mariama', 'Isatu', 'Kadiatu', 'Hawa', 'Adama',
    'Tenneh', 'Salamatu', 'Finda', 'Kumba', 'Yeabu', 'Mamie', 'Sia',
    'Baindu', 'Jeneba', 'Rugiatu', 'Zainab', 'Mabinty', 'Umu',
    'Massah', 'Agnes', 'Marie', 'Patricia', 'Sarah', 'Abibatu',
    'Musu', 'Iye', 'Nancy', 'Ramatu', 'Naasu', 'Francess',
]

LAST_NAMES = [
    'Kamara', 'Sesay', 'Koroma', 'Bangura', 'Conteh', 'Turay', 'Mansaray',
    'Jalloh', 'Kargbo', 'Bah', 'Dumbuya', 'Kallon', 'Lebbie', 'Tarawally',
    'Fofanah', 'Sawaneh', 'Sillah', 'Kanu', 'Gbla', 'Lahai',
    'Ngegba', 'Momoh', 'Williams', 'Johnson', 'Kenneh',
]

# Step definitions in protocol order with timing
# Based on actual DB trigger_index and protocol definition
STEPS = [
    # (action_id, required_behavior, source, encounter_type, resource_type, days_after_prev)
    ('registration', 'could', 'spice', None, 'RelatedPerson', 0),
    ('pregnancy-profile', 'could', 'spice', 'PWPROFILE', 'Encounter', 7),
    ('anc-visit-1', 'could', 'spice', 'ANC', 'Encounter', 14),
    ('anc-visit-1-referral', 'must', 'spice', 'Pregnant, Referred', 'ServiceRequest', 1),
    ('anc-visit-1-referral-consultation', 'must', 'openMRS', '479e4805-90d9-44f5-b252-333d8a687e52', 'Encounter', 1),
    ('anc-visit-1-referral-ack', 'must', 'spice', 'referral-response', 'ServiceRequest', 1),
    ('anc-visit-2', 'could', 'spice', 'ANC', 'Encounter', 30),
    ('anc-visit-2-referral', 'must', 'spice', 'Pregnant, Referred', 'ServiceRequest', 1),
    ('anc-visit-2-referral-consultation', 'must', 'openMRS', '479e4805-90d9-44f5-b252-333d8a687e52', 'Encounter', 1),
    ('anc-visit-2-referral-ack', 'must', 'spice', 'referral-response', 'ServiceRequest', 1),
    ('anc-visit-3', 'could', 'spice', 'ANC', 'Encounter', 30),
    ('anc-visit-3-referral', 'must', 'spice', 'Pregnant, Referred', 'ServiceRequest', 1),
    ('anc-visit-3-referral-consultation', 'must', 'openMRS', '479e4805-90d9-44f5-b252-333d8a687e52', 'Encounter', 1),
    ('anc-visit-3-referral-ack', 'must', 'spice', 'referral-response', 'ServiceRequest', 1),
    ('pregnancy-outcome', 'could', 'spice', 'PREGNANCYOUTCOME', 'Encounter', 21),
    ('pnc', 'could', 'spice', 'PNC_MOTHER', 'Encounter', 7),
]

# Progression profiles (how far along patients are)
# weights determine distribution
PROGRESSION_PROFILES = [
    # (max_completed_steps, protocol_status, weight)
    (16, 'COMPLETED', 40),   # Full journey completed (all steps + PNC)
    (15, 'ACTIVE', 5),       # Up to PNC pending
    (14, 'ACTIVE', 8),       # Up to pregnancy-outcome
    (11, 'ACTIVE', 12),      # Through ANC Visit 3 + referrals
    (10, 'ACTIVE', 8),       # Through ANC Visit 2 referral chain
    (7, 'ACTIVE', 10),       # Through ANC Visit 2
    (6, 'ACTIVE', 15),       # Through ANC Visit 1 + referral chain
    (3, 'ACTIVE', 12),       # Through ANC Visit 1
    (2, 'ACTIVE', 20),       # Through pregnancy-profile
    (1, 'ACTIVE', 15),       # Registration only
]

def weighted_choice(profiles):
    total = sum(p[2] for p in profiles)
    r = random.randint(1, total)
    cumulative = 0
    for p in profiles:
        cumulative += p[2]
        if r <= cumulative:
            return p
    return profiles[-1]


def gen_uuid():
    return str(uuid.uuid4())


def ts(dt):
    return dt.strftime('%Y-%m-%dT%H:%M:%S+00:00')


def sql_escape(s):
    return s.replace("'", "''")


def generate_patient_name():
    return random.choice(FIRST_NAMES), random.choice(LAST_NAMES)


def random_enrollment_date():
    """Generate enrollment date between Dec 2025 and April 2026"""
    start = datetime(2025, 12, 1, tzinfo=timezone.utc)
    end = datetime(2026, 4, 15, tzinfo=timezone.utc)
    days = (end - start).days
    return start + timedelta(days=random.randint(0, days), hours=random.randint(7, 16), minutes=random.randint(0, 59))


def main():
    lines = []
    lines.append("-- ============================================================")
    lines.append("-- CCE Demo Data: 367 Additional RMNCH Patients (Dec 2025 - May 2026)")
    lines.append("-- Patients: NID-2026-061 to NID-2026-427")
    lines.append("-- Facilities: 12 Sierra Leone health facilities")
    lines.append("-- Practitioners: 10 healthcare practitioners")
    lines.append("-- ============================================================")
    lines.append("")
    lines.append("-- Protocol Instances")

    all_pi = []  # (pi_id, patient_id, status, enrolled_at, facility_id)
    all_si = []  # (si_id, pi_id, action_id, state, due_date, overdue_date, missed_date, completed_at, source, completion_status, event_id, required_behavior, created_at, updated_at)
    all_events = []  # (ev_id, ce_id, source, src_ev_id, subject, type, event_time, received_at, corr_id, data_json, pi_id, pd_id, action_id, facility_id, processing_status, matched_si_id)
    all_deviations = []  # (id, pi_id, si_id, deviation_type, detected_at, metadata)

    for idx in range(61, 428):  # 61 to 427 = 367 patients
        patient_id = f'NID-2026-{idx:03d}'
        pi_id = gen_uuid()
        enrolled_at = random_enrollment_date()
        facility_id, facility_name = random.choice(FACILITIES)
        first_name, last_name = generate_patient_name()
        profile = weighted_choice(PROGRESSION_PROFILES)
        max_steps = profile[0]
        pi_status = profile[1]

        # Determine which steps should have referral sub-steps
        # ~60% of ANC visits trigger referrals
        has_referral = {
            'anc-visit-1': random.random() < 0.60,
            'anc-visit-2': random.random() < 0.55,
            'anc-visit-3': random.random() < 0.50,
        }

        # Build the actual step sequence for this patient
        patient_steps = []
        for step_def in STEPS:
            action_id = step_def[0]
            # Skip referral sub-steps if parent didn't trigger referral
            if 'referral' in action_id:
                parent = action_id.split('-referral')[0]
                if not has_referral.get(parent, False):
                    continue
            patient_steps.append(step_def)

        # Limit to max_steps
        completed_steps = patient_steps[:max_steps]

        # Determine last step states
        # Last 1-2 steps might be DUE/OVERDUE instead of completed
        num_actually_completed = len(completed_steps)
        pending_steps = []

        # Add 1-2 pending/due steps after completed ones
        remaining = patient_steps[max_steps:]
        if remaining:
            # First remaining step is DUE or PENDING
            pending_steps.append(remaining[0])
            if len(remaining) > 1 and random.random() < 0.3:
                pending_steps.append(remaining[1])

        # Generate protocol instance
        all_pi.append((pi_id, patient_id, pi_status, enrolled_at))

        current_date = enrolled_at
        step_instances = []

        for i, step_def in enumerate(completed_steps):
            action_id, req_beh, source, enc_type, res_type, days_after = step_def
            si_id = gen_uuid()
            ev_id = gen_uuid()
            ce_id = gen_uuid()

            # Calculate due date
            if i == 0:
                due_date = enrolled_at
            else:
                due_date = current_date + timedelta(days=days_after + random.randint(0, 3))

            # Random completion timing
            completion_delay = random.randint(0, 5)
            completed_at = due_date + timedelta(days=completion_delay, hours=random.randint(0, 8))

            # Ensure we don't go past May 2026
            cutoff = datetime(2026, 5, 31, 23, 59, tzinfo=timezone.utc)
            if completed_at > cutoff:
                completed_at = cutoff - timedelta(hours=random.randint(1, 48))
                due_date = min(due_date, completed_at - timedelta(days=1))

            # Determine completion status
            if completion_delay == 0:
                completion_status = 'EARLY'
            elif completion_delay <= 3:
                completion_status = 'ON_TIME'
            else:
                completion_status = 'LATE'

            # Randomly make some overdue (10% chance for non-referral steps)
            overdue_date = None
            missed_date = None
            is_overdue_resolved = False
            if completion_status == 'LATE' and random.random() < 0.3:
                overdue_date = due_date + timedelta(days=3)
                is_overdue_resolved = True

            practitioner = random.choice(PRACTITIONERS)
            current_date = completed_at

            step_instances.append({
                'si_id': si_id, 'pi_id': pi_id, 'action_id': action_id,
                'state': 'COMPLETED', 'due_date': due_date, 'overdue_date': overdue_date,
                'missed_date': missed_date, 'completed_at': completed_at,
                'source': source, 'completion_status': completion_status,
                'ev_id': ev_id, 'req_beh': req_beh
            })

            # Generate event_log entry
            event_time = completed_at
            data_json = generate_event_data(
                action_id, patient_id, res_type, enc_type,
                event_time, facility_id, facility_name, practitioner,
                first_name, last_name
            )

            all_events.append((
                ev_id, ce_id, source, ce_id, patient_id, res_type,
                event_time, event_time + timedelta(seconds=5),
                pi_id, data_json, pi_id, PROTOCOL_DEF_ID,
                action_id, facility_id, 'MATCHED', si_id
            ))

            # Generate deviation if overdue and resolved late
            if is_overdue_resolved:
                dev_id = gen_uuid()
                all_deviations.append((
                    dev_id, pi_id, si_id, 'OVERDUE',
                    overdue_date,
                    f'{{"stepName": "{format_step_name(action_id)}", "reason": "Step exceeded expected timeframe"}}'
                ))

        # Add pending/due steps
        for j, step_def in enumerate(pending_steps):
            action_id, req_beh, source, enc_type, res_type, days_after = step_def
            si_id = gen_uuid()

            due_date = current_date + timedelta(days=days_after + random.randint(0, 3))
            cutoff = datetime(2026, 5, 31, 23, 59, tzinfo=timezone.utc)
            if due_date > cutoff:
                due_date = cutoff - timedelta(days=random.randint(1, 10))

            # Determine state
            now_ref = datetime(2026, 6, 1, tzinfo=timezone.utc)
            if due_date < now_ref - timedelta(days=7):
                state = 'OVERDUE'
                overdue_date = due_date + timedelta(days=3)
                # 20% chance of MISSED
                if random.random() < 0.2:
                    state = 'MISSED'
                    missed_date = due_date + timedelta(days=7)
                    overdue_date = due_date + timedelta(days=3)
                else:
                    missed_date = None
            elif due_date < now_ref:
                state = 'DUE'
                overdue_date = None
                missed_date = None
            else:
                state = 'PENDING'
                overdue_date = None
                missed_date = None

            step_instances.append({
                'si_id': si_id, 'pi_id': pi_id, 'action_id': action_id,
                'state': state, 'due_date': due_date, 'overdue_date': overdue_date,
                'missed_date': missed_date, 'completed_at': None,
                'source': None, 'completion_status': None,
                'ev_id': None, 'req_beh': req_beh
            })

            # Generate deviation for overdue/missed
            if state in ('OVERDUE', 'MISSED'):
                dev_id = gen_uuid()
                dev_type = state
                detected_date = overdue_date if overdue_date else due_date + timedelta(days=3)
                all_deviations.append((
                    dev_id, pi_id, si_id, dev_type,
                    detected_date,
                    f'{{"stepName": "{format_step_name(action_id)}", "reason": "Step {"exceeded expected timeframe" if state == "OVERDUE" else "missed - no completion recorded within window"}"}}'
                ))

            current_date = due_date

        all_si.extend(step_instances)

    # Write SQL
    lines.append("")
    for pi_id, patient_id, status, enrolled_at in all_pi:
        lines.append(
            f"INSERT INTO protocol_instance (id, patient_id, protocol_canonical, protocol_definition_id, enrolled_at, status, created_at, updated_at) "
            f"VALUES ('{pi_id}', '{patient_id}', '{PROTOCOL_CANONICAL}', '{PROTOCOL_DEF_ID}', "
            f"'{ts(enrolled_at)}', '{status}', '{ts(enrolled_at)}', '{ts(enrolled_at)}');"
        )

    lines.append("")
    lines.append("-- Step Instances")
    for si in all_si:
        si_id = si['si_id']
        pi_id = si['pi_id']
        action_id = si['action_id']
        state = si['state']
        due_date = f"'{ts(si['due_date'])}'" if si['due_date'] else 'NULL'
        overdue_date = f"'{ts(si['overdue_date'])}'" if si['overdue_date'] else 'NULL'
        missed_date = f"'{ts(si['missed_date'])}'" if si['missed_date'] else 'NULL'
        completed_at = f"'{ts(si['completed_at'])}'" if si['completed_at'] else 'NULL'
        source = f"'{si['source']}'" if si['source'] else 'NULL'
        completion_status = f"'{si['completion_status']}'" if si['completion_status'] else 'NULL'
        ev_id = f"'{si['ev_id']}'" if si['ev_id'] else 'NULL'
        req_beh = si['req_beh']

        lines.append(
            f"INSERT INTO step_instance (id, protocol_instance_id, action_id, repeat_index, state, due_date, overdue_date, missed_date, completed_at, completed_by_source, completion_status, matched_event_id, required_behavior, created_at, updated_at) "
            f"VALUES ('{si_id}', '{pi_id}', '{action_id}', 0, '{state}', {due_date}, {overdue_date}, {missed_date}, {completed_at}, {source}, {completion_status}, {ev_id}, '{req_beh}', {due_date}, "
            f"{completed_at});"
        )

    lines.append("")
    lines.append("-- Event Log")
    for ev in all_events:
        ev_id, ce_id, source, src_ev_id, subject, res_type, event_time, received_at, corr_id, data_json, pi_id, pd_id, action_id, facility_id, proc_status, matched_si_id = ev
        data_escaped = sql_escape(data_json)
        lines.append(
            f"INSERT INTO event_log (id, cloudevents_id, source, source_event_id, subject, type, event_time, received_at, correlation_id, data, protocol_instance_id, protocol_definition_id, action_id, facility_id, processing_status, matched_step_instance_id) "
            f"VALUES ('{ev_id}', '{ce_id}', '{source}', '{src_ev_id}', '{subject}', '{res_type}', '{ts(event_time)}', '{ts(received_at)}', '{corr_id}', "
            f"'{data_escaped}'::jsonb, '{pi_id}', '{pd_id}', '{action_id}', '{facility_id}', '{proc_status}', '{matched_si_id}');"
        )

    lines.append("")
    lines.append("-- Deviations")
    for dev in all_deviations:
        dev_id, pi_id, si_id, dev_type, detected_at, metadata = dev
        meta_escaped = sql_escape(metadata)
        lines.append(
            f"INSERT INTO deviation (id, protocol_instance_id, step_instance_id, deviation_type, detected_at, metadata) "
            f"VALUES ('{dev_id}', '{pi_id}', '{si_id}', '{dev_type}', '{ts(detected_at)}', '{meta_escaped}'::jsonb);"
        )

    lines.append("")
    with open('demo-data/05-additional-patients.sql', 'w') as f:
        f.write('\n'.join(lines))
    print(f"Generated {len(all_pi)} protocol instances, {len(all_si)} step instances, {len(all_events)} events, {len(all_deviations)} deviations")


def format_step_name(action_id):
    return ' '.join(w.capitalize() for w in action_id.split('-'))


def generate_event_data(action_id, patient_id, res_type, enc_type, event_time, facility_id, facility_name, practitioner, first_name, last_name):
    """Generate FHIR-like event data JSON"""
    pract_id, pract_name = practitioner
    ts_str = event_time.strftime('%Y-%m-%dT%H:%M:%S+00:00')
    end_str = (event_time + timedelta(hours=1)).strftime('%Y-%m-%dT%H:%M:%S+00:00')
    resource_id = str(random.randint(10000, 99999))

    if res_type == 'RelatedPerson':
        phone = f'+2327600{random.randint(1000, 9999)}'
        birth_year = random.randint(1985, 2005)
        birth_month = random.randint(1, 12)
        birth_day = random.randint(1, 28)
        return (
            f'{{"id": "{resource_id}", "meta": {{"versionId": "1", "lastUpdated": "{ts_str}"}}, '
            f'"resourceType": "RelatedPerson", '
            f'"name": [{{"text": "{first_name} {last_name}", "given": ["{first_name}", "{last_name}"]}}], '
            f'"active": true, "gender": "female", '
            f'"patient": {{"reference": "Patient/{patient_id}"}}, '
            f'"telecom": [{{"use": "mobile", "value": "{phone}", "system": "phone"}}], '
            f'"birthDate": "{birth_year}-{birth_month:02d}-{birth_day:02d}", '
            f'"identifier": [{{"value": "National ID", "system": "http://mdtlabs.com/fhir/identity-type"}}, '
            f'{{"value": "{patient_id}", "system": "http://mdtlabs.com/fhir/national-id"}}, '
            f'{{"value": "ENROLLED", "system": "http://mdtlabs.com/fhir/patient-status"}}], '
            f'"participant": [{{"individual": {{"display": "{pract_name}", "reference": "Practitioner/{pract_id}"}}}}]}}'
        )
    elif res_type == 'ServiceRequest':
        # Referral or referral-ack
        status = 'completed' if 'ack' in action_id else 'active'
        intent = 'order'
        category_code = enc_type  # e.g. "Pregnant, Referred" or "referral-response"
        return (
            f'{{"id": "{resource_id}", "meta": {{"versionId": "1", "lastUpdated": "{ts_str}"}}, '
            f'"resourceType": "ServiceRequest", "status": "{status}", "intent": "{intent}", '
            f'"subject": {{"reference": "Patient/{patient_id}"}}, '
            f'"authoredOn": "{ts_str}", '
            f'"requester": {{"display": "{pract_name}", "reference": "Practitioner/{pract_id}"}}, '
            f'"locationReference": [{{"display": "{facility_name}", "reference": "Location/{facility_id}"}}], '
            f'"identifier": [{{"value": "{category_code}", "system": "http://mdtlabs.com/service-request-type"}}], '
            f'"category": [{{"coding": [{{"code": "{category_code}", "system": "http://mdtlabs.com/fhir/category"}}]}}]}}'
        )
    else:
        # Encounter
        identifiers = [f'{{"value": "{enc_type}", "system": "http://mdtlabs.com/encounter-type"}}']
        if enc_type == 'ANC':
            visit_num = '1' if 'visit-1' in action_id else ('2' if 'visit-2' in action_id else '3')
            identifiers.append(f'{{"value": "assessment", "system": "http://mdtlabs.com/type"}}')
            identifiers.append(f'{{"value": "{visit_num}", "system": "http://mdtlabs.com/visit-count"}}')
        elif enc_type == '479e4805-90d9-44f5-b252-333d8a687e52':
            # Referral consultation encounter
            identifiers.append(f'{{"value": "consultation", "system": "http://mdtlabs.com/type"}}')
        else:
            identifiers.append(f'{{"value": "assessment", "system": "http://mdtlabs.com/type"}}')

        return (
            f'{{"id": "{resource_id}", "meta": {{"versionId": "1", "lastUpdated": "{ts_str}"}}, '
            f'"resourceType": "Encounter", "status": "finished", '
            f'"subject": {{"reference": "Patient/{patient_id}"}}, '
            f'"period": {{"start": "{ts_str}", "end": "{end_str}"}}, '
            f'"location": [{{"location": {{"display": "{facility_name}", "reference": "Location/{facility_id}"}}}}], '
            f'"identifier": [{", ".join(identifiers)}], '
            f'"participant": [{{"individual": {{"display": "{pract_name}", "reference": "Practitioner/{pract_id}"}}}}]}}'
        )


if __name__ == '__main__':
    main()
