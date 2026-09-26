package io.github.dochiri0916.riskgate.riskassessment.domain.model;

public enum RiskCategory {
    CONCURRENCY,
    IDEMPOTENCY,
    TRANSACTION,
    DATA_INTEGRITY,
    PERFORMANCE,
    SECURITY,
    BREAKING_CHANGE,
    OPERABILITY
}
