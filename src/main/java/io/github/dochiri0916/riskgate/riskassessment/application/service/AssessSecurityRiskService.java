package io.github.dochiri0916.riskgate.riskassessment.application.service;

import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessSecurityRiskUseCase;
import io.github.dochiri0916.riskgate.riskassessment.application.port.out.SecurityRiskAssessmentPort;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.JevAssessment;
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
        final JevAssessment result = securityRiskAssessmentPort.assess(command.context());
        return new SecurityRiskResult(result);
    }
}
