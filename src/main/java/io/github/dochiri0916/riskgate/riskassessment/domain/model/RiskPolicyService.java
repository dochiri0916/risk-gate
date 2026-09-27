package io.github.dochiri0916.riskgate.riskassessment.domain.model;

import io.github.dochiri0916.riskgate.riskassessment.domain.exception.RiskAssessmentDomainException;
import java.util.ArrayList;
import java.util.List;

public final class RiskPolicyService {
    private final RiskScore blockThreshold;
    private final RiskScore reviewThreshold;
    private final JevRiskThresholds jevRiskThresholds;

    public RiskPolicyService(final RiskScore blockThreshold, final RiskScore reviewThreshold) {
        this(blockThreshold, reviewThreshold, JevRiskThresholds.initialCalibration());
    }

    public RiskPolicyService(
            final RiskScore blockThreshold,
            final RiskScore reviewThreshold,
            final JevRiskThresholds jevRiskThresholds
    ) {
        if (blockThreshold == null || reviewThreshold == null
                || reviewThreshold.value() >= blockThreshold.value() || jevRiskThresholds == null) {
            throw RiskAssessmentDomainException.invalidPolicyConfiguration();
        }
        this.blockThreshold = blockThreshold;
        this.reviewThreshold = reviewThreshold;
        this.jevRiskThresholds = jevRiskThresholds;
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
            return result(RiskDecision.BLOCK, input.score().value(), reasons);
        }
        if (input.score().value() >= blockThreshold.value()) {
            return result(RiskDecision.BLOCK, input.score().value(), List.of(PolicyReason.RISK_SCORE_BLOCK_THRESHOLD));
        }
        final List<PolicyReason> jevReasons = jevReasons(input.jevAssessment());
        if (input.score().value() >= reviewThreshold.value() || !jevReasons.isEmpty()) {
            final List<PolicyReason> reviewReasons = new ArrayList<>();
            if (input.score().value() >= reviewThreshold.value()) {
                reviewReasons.add(PolicyReason.RISK_SCORE_REVIEW_THRESHOLD);
            }
            reviewReasons.addAll(jevReasons);
            return result(RiskDecision.REVIEW, input.score().value(), reviewReasons);
        }
        return result(RiskDecision.PASS, input.score().value(), List.of(PolicyReason.WITHIN_POLICY_THRESHOLDS));
    }

    private List<PolicyReason> jevReasons(final JevAssessment assessment) {
        if (assessment == null) {
            return List.of();
        }
        final double threshold = jevRiskThresholds.reviewThreshold().value();
        final List<PolicyReason> reasons = new ArrayList<>();
        if (assessment.securityRisk().value() >= threshold) {
            reasons.add(PolicyReason.HIGH_SECURITY_RISK);
        }
        if (assessment.authorizationRisk().value() >= threshold) {
            reasons.add(PolicyReason.HIGH_AUTHORIZATION_RISK);
        }
        if (assessment.dataIntegrityRisk().value() >= threshold) {
            reasons.add(PolicyReason.HIGH_DATA_INTEGRITY_RISK);
        }
        if (assessment.breakingChangeRisk().value() >= threshold) {
            reasons.add(PolicyReason.HIGH_BREAKING_CHANGE_RISK);
        }
        return reasons;
    }

    private RiskPolicyResult result(
            final RiskDecision decision,
            final int score,
            final List<PolicyReason> reasons
    ) {
        return new RiskPolicyResult(decision, new RiskScore(score), new PolicyReasons(reasons));
    }
}
