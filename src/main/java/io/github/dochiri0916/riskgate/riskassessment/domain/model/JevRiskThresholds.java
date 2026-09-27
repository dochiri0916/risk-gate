package io.github.dochiri0916.riskgate.riskassessment.domain.model;

import io.github.dochiri0916.riskgate.riskassessment.domain.exception.RiskAssessmentDomainException;

public record JevRiskThresholds(RiskProbability reviewThreshold) {
    public JevRiskThresholds {
        if (reviewThreshold == null) {
            throw RiskAssessmentDomainException.invalidPolicyConfiguration();
        }
    }

    public static JevRiskThresholds initialCalibration() {
        return new JevRiskThresholds(new RiskProbability(0.85));
    }
}
