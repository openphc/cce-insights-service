package org.openphc.cce.insights;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.openphc.cce.insights.domain.entity.Deviation;
import org.openphc.cce.insights.domain.entity.ComplianceEventLog;
import org.openphc.cce.insights.domain.entity.ProtocolInstance;
import org.openphc.cce.insights.domain.entity.StepInstance;
import org.openphc.cce.insights.domain.enums.DeviationType;
import org.openphc.cce.insights.domain.enums.ProtocolInstanceStatus;
import org.openphc.cce.insights.domain.enums.StepState;
import org.openphc.cce.insights.domain.repository.DeviationRepository;
import org.openphc.cce.insights.domain.repository.ComplianceEventLogRepository;
import org.openphc.cce.insights.domain.repository.ProtocolDefinitionRepository;
import org.openphc.cce.insights.domain.repository.ProtocolInstanceRepository;
import org.openphc.cce.insights.domain.repository.StepInstanceRepository;
import org.openphc.cce.insights.service.PatientTimelineService;
import org.openphc.cce.insights.web.controller.PatientController;
import org.openphc.cce.insights.web.GlobalExceptionHandler;
import org.openphc.cce.insights.web.dto.PatientTimelineDto;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PatientController.class)
@Import(GlobalExceptionHandler.class)
class PatientControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PatientTimelineService patientTimelineService;
    @MockitoBean
    private ProtocolInstanceRepository protocolInstanceRepository;
    @MockitoBean
    private StepInstanceRepository stepInstanceRepository;
    @MockitoBean
    private DeviationRepository deviationRepository;
    @MockitoBean
    private ComplianceEventLogRepository complianceEventLogRepository;
    @MockitoBean
    private ProtocolDefinitionRepository protocolDefinitionRepository;

    private static final String PATIENT_1 = "260225-0002-5501";
    private static final String PATIENT_2 = "260225-0002-5502";
    private static final String PATIENT_3 = "260225-0002-5503";
    private static final UUID PI_ID_1 = UUID.fromString("660e8400-e29b-41d4-a716-446655440001");
    private static final UUID PI_ID_2 = UUID.fromString("660e8400-e29b-41d4-a716-446655440002");
    private static final UUID PI_ID_3 = UUID.fromString("660e8400-e29b-41d4-a716-446655440003");

    @BeforeEach
    void setUp() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        // Timeline for patient 1
        when(patientTimelineService.getTimeline(PATIENT_1))
                .thenReturn(PatientTimelineDto.builder()
                        .patientId(PATIENT_1)
                        .protocols(List.of(PatientTimelineDto.ProtocolTimeline.builder()
                                .protocolInstanceId(PI_ID_1.toString())
                                .protocolCanonical("http://example.org/anc|1.0")
                                .status("active")
                                .complianceRate(1.0)
                                .timeline(List.of(PatientTimelineDto.TimelineEvent.builder()
                                        .timestamp(now.minusDays(30)).type("enrollment")
                                        .description("Enrolled in http://example.org/anc|1.0").build()))
                                .build()))
                        .build());

        // Protocol tracking for patient 1
        ProtocolInstance pi1 = mockProtocolInstance(PI_ID_1, PATIENT_1, "http://example.org/anc|1.0", ProtocolInstanceStatus.ACTIVE, now.minusDays(30));
        when(protocolInstanceRepository.findByPatientId(PATIENT_1)).thenReturn(List.of(pi1));

        StepInstance step1 = mockStepInstance(UUID.randomUUID(), PI_ID_1, "visit-1", StepState.COMPLETED, now.minusDays(10));
        when(stepInstanceRepository.findByProtocolInstanceId(PI_ID_1)).thenReturn(List.of(step1));
        when(stepInstanceRepository.findByProtocolInstanceIdOrderByDueDateAsc(PI_ID_1)).thenReturn(List.of(step1));
        when(deviationRepository.findByProtocolInstanceId(PI_ID_1)).thenReturn(List.of());

        // Protocol tracking detail for patient 2
        ProtocolInstance pi2 = mockProtocolInstance(PI_ID_2, PATIENT_2, "http://example.org/anc|1.0", ProtocolInstanceStatus.ACTIVE, now.minusDays(20));
        when(protocolInstanceRepository.findByPatientId(PATIENT_2)).thenReturn(List.of(pi2));

        StepInstance step2a = mockStepInstance(UUID.randomUUID(), PI_ID_2, "visit-1", StepState.COMPLETED, now.minusDays(15));
        StepInstance step2b = mockStepInstance(UUID.randomUUID(), PI_ID_2, "visit-2", StepState.OVERDUE, null);
        StepInstance step2c = mockStepInstance(UUID.randomUUID(), PI_ID_2, "visit-3", StepState.PENDING, null);
        when(stepInstanceRepository.findByProtocolInstanceId(PI_ID_2)).thenReturn(List.of(step2a, step2b, step2c));
        when(stepInstanceRepository.findByProtocolInstanceIdOrderByDueDateAsc(PI_ID_2)).thenReturn(List.of(step2a, step2b, step2c));

        Deviation dev2 = mockDeviation(UUID.randomUUID(), PI_ID_2, DeviationType.OVERDUE, now.minusDays(5), "http://example.org/anc|1.0");
        when(deviationRepository.findByProtocolInstanceId(PI_ID_2)).thenReturn(List.of(dev2));

        // Patient 3 deviations
        ProtocolInstance pi3 = mockProtocolInstance(PI_ID_3, PATIENT_3, "http://example.org/anc|1.0", ProtocolInstanceStatus.ACTIVE, now.minusDays(25));
        when(protocolInstanceRepository.findByPatientId(PATIENT_3)).thenReturn(List.of(pi3));

        Deviation dev3 = mockDeviation(UUID.randomUUID(), PI_ID_3, DeviationType.MISSED, now.minusDays(3), "http://example.org/anc|1.0");
        when(deviationRepository.findByProtocolInstanceId(PI_ID_3)).thenReturn(List.of(dev3));

        // Patient events
        ComplianceEventLog event1 = mockComplianceEventLog(UUID.randomUUID(), "Patient/" + PATIENT_1, "org.openphc.cce.encounter",
                now.minusDays(10), "ebuzima-direct", "{\"resourceType\":\"Encounter\"}", "MATCHED", "fac-1");
        when(complianceEventLogRepository.findBySubjectOrderByEventTimeDesc("Patient/" + PATIENT_1))
                .thenReturn(List.of(event1));
    }

    @Test
    void getComplianceTimeline_returnsTimeline() throws Exception {
        mockMvc.perform(get("/v1/insights/patients/260225-0002-5501/compliance-timeline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.patientId").value("260225-0002-5501"))
                .andExpect(jsonPath("$.data.protocols").isArray())
                .andExpect(jsonPath("$.data.protocols[0].timeline").isArray());
    }

    @Test
    void getProtocolTracking_returnsList() throws Exception {
        mockMvc.perform(get("/v1/insights/patients/260225-0002-5501/protocol-tracking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void getProtocolTrackingDetail_returnsStepsAndDeviations() throws Exception {
        mockMvc.perform(get("/v1/insights/patients/260225-0002-5502/protocol-tracking/660e8400-e29b-41d4-a716-446655440002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.protocolInstanceId").value("660e8400-e29b-41d4-a716-446655440002"))
                .andExpect(jsonPath("$.data.steps").isArray())
                .andExpect(jsonPath("$.data.steps.length()").value(3))
                .andExpect(jsonPath("$.data.deviations").isArray())
                .andExpect(jsonPath("$.data.deviations.length()").value(1));
    }

    @Test
    void getPatientEvents_returnsEventHistory() throws Exception {
        mockMvc.perform(get("/v1/insights/patients/260225-0002-5501/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].eventId").isString())
                .andExpect(jsonPath("$.data[0].type").isString())
                .andExpect(jsonPath("$.data[0].resourceType").isString())
                .andExpect(jsonPath("$.data[0].processingStatus").isString());
    }

    @Test
    void getPatientEvents_filteredByResourceType() throws Exception {
        mockMvc.perform(get("/v1/insights/patients/260225-0002-5501/events")
                        .param("resourceType", "Encounter"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void getPatientDeviations_returnsDeviationHistory() throws Exception {
        mockMvc.perform(get("/v1/insights/patients/260225-0002-5502/deviations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].deviationType").value("OVERDUE"))
                .andExpect(jsonPath("$.data[0].protocolCanonical").isString());
    }

    @Test
    void getPatientDeviations_filteredByType() throws Exception {
        mockMvc.perform(get("/v1/insights/patients/260225-0002-5503/deviations")
                        .param("deviationType", "MISSED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void getPatientDeviations_emptyForCompliantPatient() throws Exception {
        mockMvc.perform(get("/v1/insights/patients/260225-0002-5501/deviations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // --- Helper methods to create mock entities ---

    private ProtocolInstance mockProtocolInstance(UUID id, String patientId, String canonical,
                                                  ProtocolInstanceStatus status, OffsetDateTime enrolledAt) {
        ProtocolInstance pi = mock(ProtocolInstance.class);
        when(pi.getId()).thenReturn(id);
        when(pi.getPatientId()).thenReturn(patientId);
        when(pi.getProtocolCanonical()).thenReturn(canonical);
        when(pi.getStatus()).thenReturn(status);
        when(pi.getEnrolledAt()).thenReturn(enrolledAt);
        return pi;
    }

    private StepInstance mockStepInstance(UUID id, UUID piId, String actionId,
                                          StepState state, OffsetDateTime completedAt) {
        StepInstance si = mock(StepInstance.class);
        when(si.getId()).thenReturn(id);
        when(si.getProtocolInstanceId()).thenReturn(piId);
        when(si.getActionId()).thenReturn(actionId);
        when(si.getState()).thenReturn(state);
        when(si.getCompletedAt()).thenReturn(completedAt);
        when(si.getDueDate()).thenReturn(OffsetDateTime.now(ZoneOffset.UTC).minusDays(14));
        return si;
    }

    private Deviation mockDeviation(UUID id, UUID piId, DeviationType type,
                                     OffsetDateTime detectedAt, String canonical) {
        Deviation dev = mock(Deviation.class);
        when(dev.getId()).thenReturn(id);
        when(dev.getProtocolInstanceId()).thenReturn(piId);
        when(dev.getStepInstanceId()).thenReturn(UUID.randomUUID());
        when(dev.getDeviationType()).thenReturn(type);
        when(dev.getDetectedAt()).thenReturn(detectedAt);
        return dev;
    }

    private ComplianceEventLog mockComplianceEventLog(UUID id, String subject, String type,
                                   OffsetDateTime eventTime, String source, String data,
                                   String processingStatus, String facilityId) {
        ComplianceEventLog el = mock(ComplianceEventLog.class);
        when(el.getId()).thenReturn(id);
        when(el.getCloudeventsId()).thenReturn("ce-" + id);
        when(el.getSubject()).thenReturn(subject);
        when(el.getType()).thenReturn(type);
        when(el.getEventTime()).thenReturn(eventTime);
        when(el.getSource()).thenReturn(source);
        when(el.getData()).thenReturn(data);
        when(el.getProcessingStatus()).thenReturn(processingStatus);
        when(el.getFacilityId()).thenReturn(facilityId);
        return el;
    }
}
