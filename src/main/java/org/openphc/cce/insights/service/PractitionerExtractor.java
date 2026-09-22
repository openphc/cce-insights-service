package org.openphc.cce.insights.service;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Extracts a Practitioner reference/display pair directly from a FHIR resource body.
 *
 * <p>Standalone from {@code PatientTimelineService}'s own per-patient practitioner extraction:
 * this one requires an actual {@code Practitioner/...} reference to match (a display-only value
 * with no reference is useless as a grouping key for a practitioner leaderboard), and covers the
 * shapes actually seen in real payloads for the resource types practitioners appear on:
 * <ul>
 *   <li>{@code Encounter.participant[].individual}</li>
 *   <li>{@code Observation.performer[]} / {@code Consent.performer[]} - flat Reference</li>
 *   <li>{@code Procedure.performer[].actor} - Reference wrapped under "actor"</li>
 *   <li>{@code MedicationRequest.requester} - bare Reference, not an array</li>
 * </ul>
 */
final class PractitionerExtractor {

    private PractitionerExtractor() {}

    record PractitionerRef(String reference, String display) {}

    static PractitionerRef extract(JsonNode root) {
        JsonNode participants = root.get("participant");
        if (participants != null && participants.isArray()) {
            for (JsonNode p : participants) {
                PractitionerRef found = fromReference(p.get("individual"));
                if (found != null) return found;
            }
        }

        JsonNode performers = root.get("performer");
        if (performers != null && performers.isArray()) {
            for (JsonNode perf : performers) {
                // Procedure wraps the reference under "actor"; Observation/Consent put it directly.
                JsonNode target = perf.has("actor") ? perf.get("actor") : perf;
                PractitionerRef found = fromReference(target);
                if (found != null) return found;
            }
        }

        PractitionerRef found = fromReference(root.get("requester"));
        if (found != null) return found;

        return null;
    }

    private static PractitionerRef fromReference(JsonNode node) {
        if (node == null) return null;
        String ref = node.has("reference") ? node.get("reference").asText(null) : null;
        if (ref == null || !ref.startsWith("Practitioner/")) return null;
        String display = node.has("display") ? node.get("display").asText(null) : null;
        return new PractitionerRef(ref, display);
    }
}
