package io.github.dochiri0916.riskgate.riskassessment.application.port.in;

import io.github.dochiri0916.riskgate.riskassessment.application.port.out.SecurityRiskAssessmentPort.ChangedFile;
import java.util.List;

@FunctionalInterface
public interface AssessSecurityRiskUseCase {
    SecurityRiskResult assess(AssessSecurityRiskCommand command);

    record AssessSecurityRiskCommand(String diff, List<ChangedFile> changedFiles) {
        public AssessSecurityRiskCommand {
            changedFiles = List.copyOf(changedFiles);
        }

        @Override
        public String toString() {
            return "AssessSecurityRiskCommand[diff=<redacted>]";
        }
    }

    record SecurityRiskResult(String model, double probability, int inputTokens, int outputTokens) { }
}
