package io.github.dochiri0916.riskgate.riskassessment.application.port.in;

import io.github.dochiri0916.riskgate.riskassessment.domain.model.RiskCategory;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.RiskDecision;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.RiskSeverity;
import java.util.List;
import java.util.UUID;

@FunctionalInterface
public interface AssessRiskUseCase {
    AssessRiskResult assess(AssessRiskCommand command);

    record AssessRiskResult(
            UUID assessmentId,
            String repository,
            String commitSha,
            RiskDecision decision,
            int score,
            List<String> reasonCodes,
            List<FindingResult> findings,
            String policyVersion
    ) {
        public AssessRiskResult {
            reasonCodes = List.copyOf(reasonCodes);
            findings = List.copyOf(findings);
        }
    }

    record FindingResult(
            RiskCategory category,
            RiskSeverity severity,
            String source,
            String summary,
            String evidence
    ) {
        public FindingResult {
            evidence = evidence == null ? "" : evidence;
        }
    }

    record AssessRiskCommand(
            String reportVersion,
            String repository,
            String commitSha,
            Integer pullRequestNumber,
            List<Check> checks,
            List<FindingInput> findings,
            List<ChangedFile> changedFiles
    ) {
        public AssessRiskCommand {
            checks = List.copyOf(checks);
            findings = List.copyOf(findings);
            changedFiles = List.copyOf(changedFiles);
        }

        public record FindingInput(
                RiskCategory category,
                RiskSeverity severity,
                String source,
                String summary,
                String evidence
        ) {
            public FindingInput {
                evidence = evidence == null ? "" : evidence;
            }
        }

        public record Check(String tool, String name, String status) { }
        public record ChangedFile(String path, String changeType) { }
    }
}
