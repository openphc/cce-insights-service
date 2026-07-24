package org.openphc.cce.insights.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openphc.cce.insights.domain.entity.ComplianceEventLog;
import org.openphc.cce.insights.domain.entity.Deviation;
import org.openphc.cce.insights.domain.entity.ProtocolDefinition;
import org.openphc.cce.insights.domain.entity.ProtocolInstance;
import org.openphc.cce.insights.domain.entity.StepInstance;
import org.openphc.cce.insights.domain.enums.CompletionStatus;
import org.openphc.cce.insights.domain.enums.ProtocolInstanceStatus;
import org.openphc.cce.insights.domain.enums.StepState;
import org.openphc.cce.insights.domain.repository.ComplianceEventLogRepository;
import org.openphc.cce.insights.domain.repository.DailyKpiRepository;
import org.openphc.cce.insights.domain.repository.DeviationRepository;
import org.openphc.cce.insights.domain.repository.ProtocolDefinitionRepository;
import org.openphc.cce.insights.domain.repository.ProtocolInstanceRepository;
import org.openphc.cce.insights.domain.repository.StepInstanceRepository;
import org.openphc.cce.insights.web.dto.PatientTimelineDto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers PatientTimelineService's origin-vs-destination handling for transfer Encounters,
 * exercised end-to-end via getTimeline() so the resolved facilityId/facilityName are observed
 * on the actual JourneyStep the API returns.
 *
 * ComplianceEventLog.facilityId is stubbed blank ("") in most tests here to match production:
 * findByComplianceEventIds (ComplianceEventLogRepositoryImpl) reads compliance_event_logs without
 * joining inbound_event_logs, and compliance_event_logs carries no facility_id column of its own —
 * so the stored value is always blank for this call path, and resolveFacilityId's FHIR-body
 * fallback is the only source of a facility id here.
 */
class PatientTimelineServiceTest {

    private static final String PATIENT_ID = "260227-1651-7600";
    private static final UUID PROTOCOL_INSTANCE_ID = UUID.randomUUID();
    private static final UUID PROTOCOL_DEFINITION_ID = UUID.randomUUID();
    private static final UUID STEP_INSTANCE_ID = UUID.randomUUID();
    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final String ACTION_ID = "transfer-visit";

    private ProtocolInstanceRepository protocolInstanceRepository;
    private StepInstanceRepository stepInstanceRepository;
    private ProtocolDefinitionRepository protocolDefinitionRepository;
    private DeviationRepository deviationRepository;
    private ComplianceEventLogRepository complianceEventLogRepository;
    private DailyKpiRepository dailyKpiRepository;
    private PatientTimelineService service;

    @BeforeEach
    void setUp() {
        protocolInstanceRepository = mock(ProtocolInstanceRepository.class);
        stepInstanceRepository = mock(StepInstanceRepository.class);
        protocolDefinitionRepository = mock(ProtocolDefinitionRepository.class);
        deviationRepository = mock(DeviationRepository.class);
        complianceEventLogRepository = mock(ComplianceEventLogRepository.class);
        dailyKpiRepository = mock(DailyKpiRepository.class);
        when(dailyKpiRepository.getFacilityReference()).thenReturn(List.of());
        service = new PatientTimelineService(protocolInstanceRepository, stepInstanceRepository,
                protocolDefinitionRepository, deviationRepository, complianceEventLogRepository,
                dailyKpiRepository, new ObjectMapper());

        ProtocolInstance instance = ProtocolInstance.builder()
                .id(PROTOCOL_INSTANCE_ID)
                .protocolDefinitionId(PROTOCOL_DEFINITION_ID)
                .patientId(PATIENT_ID)
                .protocolCanonical("http://openphc.org/fhir/PlanDefinition/transfer|1.0")
                .status(ProtocolInstanceStatus.ACTIVE)
                .enrolledAt(OffsetDateTime.parse("2026-07-17T14:01:00Z"))
                .build();
        when(protocolInstanceRepository.findByPatientId(PATIENT_ID)).thenReturn(List.of(instance));

        StepInstance step = StepInstance.builder()
                .id(STEP_INSTANCE_ID)
                .protocolInstanceId(PROTOCOL_INSTANCE_ID)
                .actionId(ACTION_ID)
                .state(StepState.COMPLETED)
                .completedAt(OffsetDateTime.parse("2026-07-17T14:01:00Z"))
                .completionStatus(CompletionStatus.ON_TIME)
                .completedBySource("ebuzima")
                .completedByEventId(EVENT_ID)
                .build();
        when(stepInstanceRepository.findByProtocolInstanceIdIn(List.of(PROTOCOL_INSTANCE_ID)))
                .thenReturn(List.of(step));

        when(deviationRepository.findByProtocolInstanceIdIn(any())).thenReturn(List.of());

        String definitionJson = """
                { "action": [{ "id": "%s", "title": "Transfer Visit" }] }
                """.formatted(ACTION_ID);
        ProtocolDefinition definition = ProtocolDefinition.builder()
                .id(PROTOCOL_DEFINITION_ID)
                .definition(definitionJson)
                .build();
        when(protocolDefinitionRepository.findById(PROTOCOL_DEFINITION_ID)).thenReturn(Optional.of(definition));
    }

    /** Blank stored facilityId, matching production — see class javadoc. */
    private void stubEventData(String fhirJson) {
        stubEventData("", fhirJson);
    }

    private void stubEventData(String storedFacilityId, String fhirJson) {
        ComplianceEventLog eventLog = ComplianceEventLog.builder()
                .id(EVENT_ID)
                .facilityId(storedFacilityId)
                .data(fhirJson)
                .build();
        when(complianceEventLogRepository.findByComplianceEventIds(List.of(EVENT_ID)))
                .thenReturn(List.of(eventLog));
    }

    private PatientTimelineDto.JourneyStep singleJourneyStep() {
        PatientTimelineDto dto = service.getTimeline(PATIENT_ID, null, null);
        assertThat(dto.getProtocols()).hasSize(1);
        List<PatientTimelineDto.JourneyStep> journey = dto.getProtocols().get(0).getJourney();
        assertThat(journey).hasSize(1);
        return journey.get(0);
    }

    @Test
    void transferEncounter_prefersHospitalizationOriginOverDestinationLocation() {
        String fhirJson = """
                {
                  "resourceType": "Encounter",
                  "hospitalization": {
                    "origin": { "reference": "Location/1651", "display": "Minazi Health Center" },
                    "destination": { "reference": "Location/0302", "display": "Ruli DH" }
                  },
                  "location": [{
                    "location": { "reference": "Location/0302", "display": "Ruli DH" }
                  }]
                }
                """;
        stubEventData(fhirJson);

        PatientTimelineDto.JourneyStep step = singleJourneyStep();
        assertThat(step.getFacilityId()).isEqualTo("1651");
        assertThat(step.getFacilityName()).isEqualTo("Minazi Health Center");
    }

    @Test
    void transferEncounter_noOrigin_sourceFacilityExtensionGuardsAgainstDestinationName() {
        // hospitalization.origin absent; source-facility extension is the true source (1651),
        // but location[0] still holds the destination (0302/Ruli DH) with a mismatched id —
        // its display must NOT be trusted.
        String fhirJson = """
                {
                  "resourceType": "Encounter",
                  "location": [{
                    "location": { "reference": "Location/0302", "display": "Ruli DH" }
                  }],
                  "extension": [
                    { "url": "http://example.org/fhir/StructureDefinition/source-facility", "valueString": "1651" }
                  ]
                }
                """;
        stubEventData(fhirJson);

        PatientTimelineDto.JourneyStep step = singleJourneyStep();
        assertThat(step.getFacilityId()).isEqualTo("1651");
        assertThat(step.getFacilityName()).isNull();
    }

    @Test
    void plainEncounter_sourceFacilityExtensionMatchesLocation_recoversDisplayName() {
        String fhirJson = """
                {
                  "resourceType": "Encounter",
                  "location": [{
                    "location": { "reference": "Location/0030", "display": "Kacyiru Health Center" }
                  }],
                  "extension": [
                    { "url": "http://example.org/fhir/StructureDefinition/source-facility", "valueString": "0030" }
                  ]
                }
                """;
        stubEventData(fhirJson);

        PatientTimelineDto.JourneyStep step = singleJourneyStep();
        assertThat(step.getFacilityId()).isEqualTo("0030");
        assertThat(step.getFacilityName()).isEqualTo("Kacyiru Health Center");
    }

    @Test
    void observationBackedStep_fallsBackToFacilityDimensionLookupForName() {
        // Observation (e.g. Vitals Recording / Chief Complaints) carries no location/hospitalization
        // field extractFacilityName knows how to read — only the source-facility extension id.
        // The facility dimension lookup (dailyKpiRepository) must fill in the name in that case.
        String fhirJson = """
                {
                  "resourceType": "Observation",
                  "extension": [
                    { "url": "http://example.org/fhir/StructureDefinition/source-facility", "valueString": "1228" }
                  ]
                }
                """;
        stubEventData(fhirJson);
        when(dailyKpiRepository.getFacilityReference())
                .thenReturn(List.<Object[]>of(new Object[] { "1228", "Solace Ministries Health Center" }));

        PatientTimelineDto.JourneyStep step = singleJourneyStep();
        assertThat(step.getFacilityId()).isEqualTo("1228");
        assertThat(step.getFacilityName()).isEqualTo("Solace Ministries Health Center");
    }

    @Test
    void storedFacilityId_takesPrecedenceOverFhirBodyDerivation() {
        // If ComplianceEventLogRepositoryImpl is ever changed to join inbound_event_logs (so
        // el.getFacilityId() is populated), that already-correct pipeline value must win over
        // re-deriving one from the FHIR body.
        String fhirJson = """
                {
                  "resourceType": "Encounter",
                  "hospitalization": {
                    "origin": { "reference": "Location/1651", "display": "Minazi Health Center" }
                  }
                }
                """;
        stubEventData("9999", fhirJson);

        PatientTimelineDto.JourneyStep step = singleJourneyStep();
        assertThat(step.getFacilityId()).isEqualTo("9999");
    }
}
