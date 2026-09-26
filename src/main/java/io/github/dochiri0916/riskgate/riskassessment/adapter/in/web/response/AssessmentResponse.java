package io.github.dochiri0916.riskgate.riskassessment.adapter.in.web.response;

import java.util.List;
import java.util.UUID;

public record AssessmentResponse(
        UUID assessmentId,
        String repository,
        String commitSha,
        String decision,
        int score,
        List<String> reasonCodes,
        List<FindingResponse> findings,
        String policyVersion
) {
    public AssessmentResponse {
        reasonCodes = List.copyOf(reasonCodes);
        findings = List.copyOf(findings);
    }

    public record FindingResponse(String category, String severity, String source, String summary, String evidence) { }
}
