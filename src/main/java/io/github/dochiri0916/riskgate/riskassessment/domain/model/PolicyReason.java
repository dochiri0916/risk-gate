package io.github.dochiri0916.riskgate.riskassessment.domain.model;

public enum PolicyReason {
    BUILD_CONVENTION_FAILED,
    SEMGREP_CRITICAL,
    RISK_SCORE_BLOCK_THRESHOLD,
    RISK_SCORE_REVIEW_THRESHOLD,
    WITHIN_POLICY_THRESHOLDS
}
