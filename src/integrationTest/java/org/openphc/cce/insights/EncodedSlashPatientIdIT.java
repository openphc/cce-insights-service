package org.openphc.cce.insights;

import org.junit.jupiter.api.Test;
import org.openphc.cce.insights.config.TomcatConfig;
import org.openphc.cce.insights.domain.repository.DeviationRepository;
import org.openphc.cce.insights.domain.repository.MatcherEventLogRepository;
import org.openphc.cce.insights.domain.repository.ProtocolDefinitionRepository;
import org.openphc.cce.insights.domain.repository.ProtocolInstanceRepository;
import org.openphc.cce.insights.domain.repository.StepInstanceRepository;
import org.openphc.cce.insights.service.PatientTimelineService;
import org.openphc.cce.insights.web.GlobalExceptionHandler;
import org.openphc.cce.insights.web.controller.PatientController;
import org.openphc.cce.insights.web.dto.PatientTimelineDto;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Runs the real embedded Tomcat (MockMvc never reaches Tomcat, which is what rejected an encoded
 * slash) to check that a patient id carrying one — {@code Group/856237}, sent as
 * {@code Group%2F856237} — reaches the controller as a single, decoded path variable.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = {TomcatConfig.class, PatientController.class, GlobalExceptionHandler.class})
@ImportAutoConfiguration({
        ServletWebServerFactoryAutoConfiguration.class,
        DispatcherServletAutoConfiguration.class,
        WebMvcAutoConfiguration.class,
        HttpMessageConvertersAutoConfiguration.class,
        JacksonAutoConfiguration.class})
class EncodedSlashPatientIdIT {

    private static final String PATIENT_ID_WITH_SLASH = "Group/856237";

    @LocalServerPort
    private int serverPort;

    @MockitoBean
    private PatientTimelineService patientTimelineService;
    @MockitoBean
    private ProtocolInstanceRepository protocolInstanceRepository;
    @MockitoBean
    private StepInstanceRepository stepInstanceRepository;
    @MockitoBean
    private DeviationRepository deviationRepository;
    @MockitoBean
    private MatcherEventLogRepository matcherEventLogRepository;
    @MockitoBean
    private ProtocolDefinitionRepository protocolDefinitionRepository;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void patientIdWithEncodedSlashReachesTheControllerDecoded() throws Exception {
        when(patientTimelineService.getTimeline(PATIENT_ID_WITH_SLASH))
                .thenReturn(PatientTimelineDto.builder()
                        .patientId(PATIENT_ID_WITH_SLASH)
                        .protocols(List.of())
                        .build());

        HttpResponse<String> timelineResponse =
                get("/v1/insights/patients/Group%2F856237/compliance-timeline");

        assertThat(timelineResponse.statusCode()).isEqualTo(200);
        assertThat(timelineResponse.body()).contains("\"patientId\":\"Group/856237\"");
    }

    @Test
    void plainPatientIdStillWorks() throws Exception {
        when(patientTimelineService.getTimeline("859061"))
                .thenReturn(PatientTimelineDto.builder().patientId("859061").protocols(List.of()).build());

        assertThat(get("/v1/insights/patients/859061/compliance-timeline").statusCode()).isEqualTo(200);
    }

    private HttpResponse<String> get(String rawPath) throws Exception {
        // URI.create keeps the path exactly as written, so %2F is sent encoded rather than as '/'.
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + serverPort + rawPath)).GET().build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
