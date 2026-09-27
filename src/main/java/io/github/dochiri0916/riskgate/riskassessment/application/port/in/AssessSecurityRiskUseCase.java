package io.github.dochiri0916.riskgate.riskassessment.application.port.in;

import io.github.dochiri0916.riskgate.riskassessment.application.port.out.ChangeContext;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.JevAssessment;

@FunctionalInterface
public interface AssessSecurityRiskUseCase {
    SecurityRiskResult assess(AssessSecurityRiskCommand command);

    record AssessSecurityRiskCommand(ChangeContext context) { }

    record SecurityRiskResult(JevAssessment assessment) { }
}
