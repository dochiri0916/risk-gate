package io.github.dochiri0916.riskgate.riskassessment.domain.model;

import io.github.dochiri0916.riskgate.riskassessment.domain.exception.RiskAssessmentDomainException;
import java.util.List;

public record PolicyReasons(List<PolicyReason> values) {
    public PolicyReasons {
        if (values == null || values.stream().anyMatch(reason -> reason == null)) {
            throw RiskAssessmentDomainException.invalidPolicyInput();
        }
        values = List.copyOf(values);
    }
}
