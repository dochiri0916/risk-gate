package io.github.dochiri0916.riskgate.riskassessment.domain.exception;

import io.github.dochiri0916.riskgate.global.exception.DomainException;
import java.util.Map;

public final class RiskAssessmentDomainException extends DomainException {
    private static final long serialVersionUID = 1L;
    private RiskAssessmentDomainException(
            final RiskAssessmentErrorCode errorCode,
            final Map<String, Object> extensions
    ) {
        super(errorCode, extensions);
    }

    public static RiskAssessmentDomainException invalidReportSchema(final String version) {
        return new RiskAssessmentDomainException(
                RiskAssessmentErrorCode.INVALID_REPORT_SCHEMA,
                Map.of("reportVersion", version)
        );
    }

    public static RiskAssessmentDomainException invalidScore(final int score) {
        return new RiskAssessmentDomainException(
                RiskAssessmentErrorCode.INVALID_SCORE,
                Map.of("score", score)
        );
    }

    public static RiskAssessmentDomainException invalidPolicyConfiguration() {
        return new RiskAssessmentDomainException(RiskAssessmentErrorCode.INVALID_POLICY_CONFIGURATION, Map.of());
    }

    public static RiskAssessmentDomainException invalidPolicyInput() {
        return new RiskAssessmentDomainException(RiskAssessmentErrorCode.INVALID_POLICY_INPUT, Map.of());
    }

}
