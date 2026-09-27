package io.github.dochiri0916.riskgate.riskassessment.application.port.out;

import io.github.dochiri0916.riskgate.riskassessment.domain.model.JevAssessment;

@FunctionalInterface
public interface SecurityRiskAssessmentPort {
    JevAssessment assess(ChangeContext context);

    enum FailureKind {
        CONFIGURATION_ERROR,
        REQUEST_ERROR,
        UNAUTHORIZED,
        FORBIDDEN,
        RATE_LIMITED,
        SERVER_ERROR,
        TIMEOUT,
        MALFORMED_RESPONSE,
        HTTP_ERROR,
        CONNECTION_ERROR
    }

    final class SecurityRiskAssessmentException extends RuntimeException {
        private final FailureKind kind;

        public SecurityRiskAssessmentException(final FailureKind failureKind) {
            super("Security risk assessment failed: " + failureKind);
            this.kind = failureKind;
        }

        public FailureKind failureKind() {
            return kind;
        }
    }

}
