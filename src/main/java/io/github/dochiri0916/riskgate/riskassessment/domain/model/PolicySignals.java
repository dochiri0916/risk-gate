package io.github.dochiri0916.riskgate.riskassessment.domain.model;

import io.github.dochiri0916.riskgate.riskassessment.domain.exception.RiskAssessmentDomainException;
import java.util.List;

public record PolicySignals(List<PolicySignal> values) {
    public PolicySignals {
        if (values == null || values.stream().anyMatch(signal -> signal == null)) {
            throw RiskAssessmentDomainException.invalidPolicyInput();
        }
        values = List.copyOf(values);
    }
}
