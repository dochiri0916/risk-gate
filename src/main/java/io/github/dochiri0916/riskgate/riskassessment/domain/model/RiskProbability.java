package io.github.dochiri0916.riskgate.riskassessment.domain.model;

import io.github.dochiri0916.riskgate.riskassessment.domain.exception.RiskAssessmentDomainException;

public record RiskProbability(double value) {
    public RiskProbability {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw RiskAssessmentDomainException.invalidJevAssessmentProbability();
        }
    }
}
