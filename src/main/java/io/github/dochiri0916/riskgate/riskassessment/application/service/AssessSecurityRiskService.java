package io.github.dochiri0916.riskgate.riskassessment.application.service;

import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessSecurityRiskUseCase;
import io.github.dochiri0916.riskgate.riskassessment.application.port.out.SecurityRiskAssessmentPort;
import io.github.dochiri0916.riskgate.riskassessment.application.port.out.SecurityRiskAssessmentPort.SecurityRiskAssessment;
import io.github.dochiri0916.riskgate.riskassessment.application.port.out.SecurityRiskAssessmentPort.SecurityRiskAssessmentRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public final class AssessSecurityRiskService implements AssessSecurityRiskUseCase {
    private final SecurityRiskAssessmentPort securityRiskAssessmentPort;

    @Override
    @Transactional
    public SecurityRiskResult assess(final AssessSecurityRiskCommand command) {
        final SecurityRiskAssessment result = securityRiskAssessmentPort.assess(
                new SecurityRiskAssessmentRequest(command.diff(), command.changedFiles())
        );
        return new SecurityRiskResult(
                result.model(), result.probability(), result.inputTokens(), result.outputTokens()
        );
    }
}
