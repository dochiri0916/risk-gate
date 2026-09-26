package io.github.dochiri0916.riskgate.riskassessment.domain.model;

import io.github.dochiri0916.riskgate.riskassessment.domain.exception.RiskAssessmentDomainException;

public record RiskPolicyResult(RiskDecision decision, RiskScore score, PolicyReasons reasons) {
    public RiskPolicyResult {
        if (decision == null || score == null || reasons == null) {
            throw RiskAssessmentDomainException.invalidPolicyInput();
        }
    }
}
