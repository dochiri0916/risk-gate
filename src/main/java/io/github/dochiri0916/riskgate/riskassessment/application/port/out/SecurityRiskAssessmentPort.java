package io.github.dochiri0916.riskgate.riskassessment.application.port.out;

import java.util.List;

@FunctionalInterface
public interface SecurityRiskAssessmentPort {
    SecurityRiskAssessment assess(SecurityRiskAssessmentRequest request);

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

    record SecurityRiskAssessmentRequest(String diff, List<ChangedFile> changedFiles) {
        public SecurityRiskAssessmentRequest {
            changedFiles = List.copyOf(changedFiles);
        }

        @Override
        public String toString() {
            return "SecurityRiskAssessmentRequest[diff=<redacted>]";
        }
    }

    record ChangedFile(String path, String changeType) { }

    record SecurityRiskAssessment(String model, double probability, int inputTokens, int outputTokens) { }
}
