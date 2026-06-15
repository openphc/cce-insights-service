package org.openphc.cce.insights.service;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.domain.entity.ProtocolInstance;
import org.openphc.cce.insights.domain.entity.StepInstance;
import org.openphc.cce.insights.domain.repository.ProtocolInstanceRepository;
import org.openphc.cce.insights.domain.repository.StepInstanceRepository;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ExportService {

    private final ProtocolInstanceRepository protocolInstanceRepository;
    private final StepInstanceRepository stepInstanceRepository;

    public void writeComplianceCsv(UUID protocolDefinitionId, String facilityId,
                                     OffsetDateTime startDate, OffsetDateTime endDate,
                                     OutputStream outputStream) {
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
        writer.println("patient_id,protocol_canonical,status,enrolled_at," +
                "total_steps,completed_steps,overdue_steps,missed_steps,compliance_rate");

        List<ProtocolInstance> instances;
        if (protocolDefinitionId != null) {
            instances = protocolInstanceRepository.findByProtocolDefinitionId(protocolDefinitionId);
        } else {
            instances = protocolInstanceRepository.findAll();
        }

        for (ProtocolInstance pi : instances) {
            List<StepInstance> steps = stepInstanceRepository.findByProtocolInstanceId(pi.getId());
            long total = steps.size();
            long completed = steps.stream().filter(s -> s.getCompletedAt() != null).count();
            long overdue = steps.stream()
                    .filter(s -> s.getState().name().equals("OVERDUE")).count();
            long missed = steps.stream()
                    .filter(s -> s.getState().name().equals("MISSED")).count();
            double rate = total > 0 ? Math.round((double) completed / total * 100.0) / 100.0 : 0;

            writer.printf("%s,%s,%s,%s,%d,%d,%d,%d,%.2f%n",
                    escapeCsv(pi.getPatientId()),
                    escapeCsv(pi.getProtocolCanonical()),
                    pi.getStatus(),
                    pi.getEnrolledAt(),
                    total, completed, overdue, missed, rate);
        }
        writer.flush();
    }

    public List<Map<String, Object>> exportComplianceJson(UUID protocolDefinitionId, String facilityId,
                                                           OffsetDateTime startDate, OffsetDateTime endDate) {
        List<ProtocolInstance> instances;
        if (protocolDefinitionId != null) {
            instances = protocolInstanceRepository.findByProtocolDefinitionId(protocolDefinitionId);
        } else {
            instances = protocolInstanceRepository.findAll();
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (ProtocolInstance pi : instances) {
            List<StepInstance> steps = stepInstanceRepository.findByProtocolInstanceId(pi.getId());
            long total = steps.size();
            long completed = steps.stream().filter(s -> s.getCompletedAt() != null).count();
            long overdue = steps.stream()
                    .filter(s -> s.getState().name().equals("OVERDUE")).count();
            long missed = steps.stream()
                    .filter(s -> s.getState().name().equals("MISSED")).count();
            double rate = total > 0 ? Math.round((double) completed / total * 100.0) / 100.0 : 0;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("patientId", pi.getPatientId());
            row.put("protocolCanonical", pi.getProtocolCanonical());
            row.put("status", pi.getStatus());
            row.put("enrolledAt", pi.getEnrolledAt());
            row.put("totalSteps", total);
            row.put("completedSteps", completed);
            row.put("overdueSteps", overdue);
            row.put("missedSteps", missed);
            row.put("complianceRate", rate);
            results.add(row);
        }
        return results;
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
