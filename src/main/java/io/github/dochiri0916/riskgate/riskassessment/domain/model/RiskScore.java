package io.github.dochiri0916.riskgate.riskassessment.domain.model;

import io.github.dochiri0916.riskgate.riskassessment.domain.exception.RiskAssessmentDomainException;

public record RiskScore(int value) {
    public RiskScore {
        if (value < 0 || value > 100) {
            throw RiskAssessmentDomainException.invalidScore(value);
        }
    }
}
