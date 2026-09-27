package io.github.dochiri0916.riskgate.riskassessment.domain.model;

import io.github.dochiri0916.riskgate.riskassessment.domain.exception.RiskAssessmentDomainException;

public record RiskPolicyInput(RiskScore score, PolicySignals signals, JevAssessment jevAssessment) {
    public RiskPolicyInput {
        if (score == null || signals == null) {
            throw RiskAssessmentDomainException.invalidPolicyInput();
        }
    }

    public RiskPolicyInput(final RiskScore score, final PolicySignals signals) {
        this(score, signals, null);
    }
}
