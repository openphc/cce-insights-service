package org.openphc.cce.insights.web.dto;

import java.util.List;

public record ProtocolPatientsPage(List<PatientComplianceDto> patients, long totalCount) {}
