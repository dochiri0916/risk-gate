package io.github.dochiri0916.riskgate.riskassessment.adapter.in.bootstrap;

import io.github.dochiri0916.riskgate.riskassessment.application.port.out.SecurityRiskAssessmentPort.FailureKind;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.JevAssessment;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.io.PrintStream;
import tools.jackson.databind.ObjectMapper;

final class LocalJevOutput {
    private LocalJevOutput() { }

    static Map<String, Object> assessment(final JevAssessment assessment) {
        return Map.of(
                "status", "PASS",
                "securityRisk", assessment.securityRisk().value(),
                "authorizationRisk", assessment.authorizationRisk().value(),
                "dataIntegrityRisk", assessment.dataIntegrityRisk().value(),
                "breakingChangeRisk", assessment.breakingChangeRisk().value()
        );
    }

    static Map<String, Object> failure(final FailureKind failureKind) {
        return Map.of("status", "ERROR", "failureKind", failureKind.name());
    }

    static Map<String, Object> failureResult(final FailureKind failureKind) {
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", "LOCAL");
        result.put("decision", "ERROR");
        result.put("reasonCodes", List.of("JEV_ANALYSIS_FAILED"));
        result.put("semgrep", "NOT_RUN");
        result.put("jev", failure(failureKind));
        return result;
    }

    static void writeFailure(
            final ObjectMapper objectMapper, final PrintStream output, final FailureKind failureKind
    ) {
        objectMapper.writeValue(output, failureResult(failureKind));
        output.println();
    }
}
