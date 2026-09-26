package io.github.dochiri0916.riskgate.riskassessment.domain.model;

public enum RiskSeverity {
    LOW(15),
    MEDIUM(40),
    HIGH(70),
    CRITICAL(90);

    private final int defaultScore;

    RiskSeverity(final int score) {
        this.defaultScore = score;
    }

    public int score() {
        return defaultScore;
    }
}
