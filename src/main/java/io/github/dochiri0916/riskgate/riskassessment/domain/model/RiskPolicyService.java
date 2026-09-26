package io.github.dochiri0916.riskgate.riskassessment.domain.model;

import java.util.ArrayList;
import java.util.List;

public final class RiskPolicyService {
    private final RiskScore blockThreshold;
    private final RiskScore reviewThreshold;

    public RiskPolicyService(final RiskScore blockThreshold, final RiskScore reviewThreshold) {
        if (blockThreshold == null || reviewThreshold == null
                || reviewThreshold.value() >= blockThreshold.value()) {
            throw io.github.dochiri0916.riskgate.riskassessment.domain.exception.RiskAssessmentDomainException
                    .invalidPolicyConfiguration();
        }
        this.blockThreshold = blockThreshold;
        this.reviewThreshold = reviewThreshold;
    }

    public RiskPolicyResult evaluate(final RiskPolicyInput input) {
        final List<PolicyReason> reasons = new ArrayList<>();
        if (input.signals().values().contains(PolicySignal.BUILD_CONVENTION_FAILED)) {
            reasons.add(PolicyReason.BUILD_CONVENTION_FAILED);
        }
        if (input.signals().values().contains(PolicySignal.SEMGREP_CRITICAL)) {
            reasons.add(PolicyReason.SEMGREP_CRITICAL);
        }
        if (!reasons.isEmpty()) {
            return result(RiskDecision.BLOCK, Math.max(input.score().value(), blockThreshold.value()), reasons);
        }
        if (input.score().value() >= blockThreshold.value()) {
            return result(RiskDecision.BLOCK, input.score().value(), List.of(PolicyReason.RISK_SCORE_BLOCK_THRESHOLD));
        }
        if (input.score().value() >= reviewThreshold.value()) {
            return result(
                    RiskDecision.REVIEW,
                    input.score().value(),
                    List.of(PolicyReason.RISK_SCORE_REVIEW_THRESHOLD)
            );
        }
        return result(RiskDecision.PASS, input.score().value(), List.of(PolicyReason.WITHIN_POLICY_THRESHOLDS));
    }

    private RiskPolicyResult result(
            final RiskDecision decision,
            final int score,
            final List<PolicyReason> reasons
    ) {
        return new RiskPolicyResult(decision, new RiskScore(score), new PolicyReasons(reasons));
    }
}
