package io.github.dochiri0916.riskgate.riskassessment.domain.model;

public record JevAssessment(
        RiskProbability securityRisk,
        RiskProbability authorizationRisk,
        RiskProbability dataIntegrityRisk,
        RiskProbability breakingChangeRisk
) {
    public JevAssessment {
        if (securityRisk == null || authorizationRisk == null
                || dataIntegrityRisk == null || breakingChangeRisk == null) {
            throw io.github.dochiri0916.riskgate.riskassessment.domain.exception.RiskAssessmentDomainException
                    .invalidJevAssessmentProbability();
        }
    }
}
