package io.github.dochiri0916.riskgate.riskassessment.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.dochiri0916.riskgate.riskassessment.domain.exception.RiskAssessmentDomainException;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RiskPolicyDomainTests {
    private final RiskPolicyService riskPolicyService = new RiskPolicyService(new RiskScore(90), new RiskScore(70));

    @Test
    @DisplayName("0부터 100까지의 점수는 유효하다")
    void acceptScoreBounds() {
        // given
        final List<Integer> boundaryScores = List.of(0, 100);

        // when
        final List<Integer> acceptedScores = boundaryScores.stream().map(RiskScore::new)
                .map(RiskScore::value).toList();

        // then
        assertEquals(boundaryScores, acceptedScores, "유효한 점수는 입력값을 그대로 보존한다");
    }

    @Test
    @DisplayName("0 미만 점수는 Domain ErrorCode와 함께 거부한다")
    void rejectNegativeScore() {
        // given
        final int invalidScore = -1;

        // when
        final RiskAssessmentDomainException exception = assertThrows(
                RiskAssessmentDomainException.class,
                () -> new RiskScore(invalidScore)
        );

        // then
        assertEquals("RISK-ASSESSMENT-002", exception.errorCode().code(), "점수 범위 오류는 안정된 ErrorCode를 쓴다");
    }

    @Test
    @DisplayName("100 초과 점수는 Domain ErrorCode와 함께 거부한다")
    void rejectScoreAboveMaximum() {
        // given
        final int invalidScore = 101;

        // when
        final RiskAssessmentDomainException exception = assertThrows(
                RiskAssessmentDomainException.class,
                () -> new RiskScore(invalidScore)
        );

        // then
        assertEquals("RISK-ASSESSMENT-002", exception.errorCode().code(), "최대 초과 점수도 같은 ErrorCode를 쓴다");
    }

    @Test
    @DisplayName("build-convention 실패는 점수가 낮아도 BLOCK 한다")
    void blockWhenConventionFails() {
        // given
        final RiskPolicyInput input = input(0, List.of(PolicySignal.BUILD_CONVENTION_FAILED));

        // when
        final RiskPolicyResult result = riskPolicyService.evaluate(input);

        // then
        assertEquals(RiskDecision.BLOCK, result.decision(), "컨벤션 실패는 항상 BLOCK 판정이다");
        assertEquals(0, result.score().value(), "Analyzer 점수를 그대로 보존한다");
        assertEquals(List.of(PolicyReason.BUILD_CONVENTION_FAILED), result.reasons().values(), "판정 사유를 반환한다");
    }

    @Test
    @DisplayName("critical Semgrep 신호는 BLOCK 한다")
    void blockWhenSemgrepIsCritical() {
        // given
        final RiskPolicyInput input = input(15, List.of(PolicySignal.SEMGREP_CRITICAL));

        // when
        final RiskPolicyResult result = riskPolicyService.evaluate(input);

        // then
        assertEquals(RiskDecision.BLOCK, result.decision(), "critical Semgrep은 점수와 무관하게 BLOCK 한다");
        assertEquals(15, result.score().value(), "Analyzer 점수를 그대로 보존한다");
        assertEquals(List.of(PolicyReason.SEMGREP_CRITICAL), result.reasons().values(), "판정 사유를 반환한다");
    }

    @Test
    @DisplayName("두 hard gate 신호는 판정 이유에 모두 남긴다")
    void preserveEveryHardGateReason() {
        // given
        final List<PolicySignal> signals = List.of(
                PolicySignal.BUILD_CONVENTION_FAILED,
                PolicySignal.SEMGREP_CRITICAL
        );

        // when
        final RiskPolicyResult result = riskPolicyService.evaluate(input(100, signals));

        // then
        assertEquals(2, result.reasons().values().size(), "복수 hard gate 이유를 모두 보존한다");
    }

    @Test
    @DisplayName("점수 70부터 89까지 REVIEW 하고 90부터 BLOCK 한다")
    void applyScoreThresholds() {
        // given
        final RiskPolicyResult review = riskPolicyService.evaluate(input(70, List.of()));
        final RiskPolicyResult block = riskPolicyService.evaluate(input(90, List.of()));

        // when
        final List<RiskDecision> decisions = List.of(review.decision(), block.decision());

        // then
        assertEquals(List.of(RiskDecision.REVIEW, RiskDecision.BLOCK), decisions, "임계값 경계에 맞게 판정한다");
    }

    @Test
    @DisplayName("점수 89는 review 임계값에 따라 REVIEW 한다")
    void reviewBelowBlockThreshold() {
        // given
        final RiskPolicyInput input = input(89, List.of());

        // when
        final RiskPolicyResult result = riskPolicyService.evaluate(input);

        // then
        assertEquals(RiskDecision.REVIEW, result.decision(), "block 임계값 바로 아래는 REVIEW 한다");
    }

    @Test
    @DisplayName("점수 69는 PASS 한다")
    void passBelowReviewThreshold() {
        // given
        final RiskPolicyInput input = input(69, List.of());

        // when
        final RiskPolicyResult result = riskPolicyService.evaluate(input);

        // then
        assertEquals(RiskDecision.PASS, result.decision(), "review 임계값보다 낮은 점수는 PASS 한다");
    }

    @Test
    @DisplayName("Jev risk가 없으면 기존 deterministic PASS를 유지한다")
    void passWhenJevRisksAreLow() {
        // given
        final JevAssessment jev = jev(0.84, 0.84, 0.84, 0.84);

        // when
        final RiskPolicyResult result = riskPolicyService.evaluate(new RiskPolicyInput(
                new RiskScore(0), new PolicySignals(List.of()), jev));

        // then
        assertEquals(RiskDecision.PASS, result.decision(), "모든 Jev risk가 초기 임계값보다 낮으면 PASS 한다");
    }

    @Test
    @DisplayName("각 Jev risk가 임계값 이상이면 대응 reason code와 함께 REVIEW 한다")
    void reviewForEachJevRiskThreshold() {
        // given
        final List<JevAssessment> assessments = List.of(
                jev(0.85, 0, 0, 0), jev(0, 0.85, 0, 0), jev(0, 0, 0.85, 0), jev(0, 0, 0, 0.85));

        // when
        final List<RiskPolicyResult> results = assessments.stream()
                .map(assessment -> riskPolicyService.evaluate(new RiskPolicyInput(
                        new RiskScore(0), new PolicySignals(List.of()), assessment)))
                .toList();
        final List<PolicyReason> reasons = results.stream()
                .flatMap(result -> result.reasons().values().stream()).toList();

        // then
        assertEquals(List.of(RiskDecision.REVIEW, RiskDecision.REVIEW,
                RiskDecision.REVIEW, RiskDecision.REVIEW), results.stream().map(RiskPolicyResult::decision).toList(),
                "각 Jev risk가 단독으로 임계값에 도달하면 REVIEW 한다");
        assertEquals(List.of(PolicyReason.HIGH_SECURITY_RISK, PolicyReason.HIGH_AUTHORIZATION_RISK,
                PolicyReason.HIGH_DATA_INTEGRITY_RISK, PolicyReason.HIGH_BREAKING_CHANGE_RISK), reasons,
                "각 risk에 대응하는 reason code를 반환한다");
    }

    @Test
    @DisplayName("복수 Jev risk는 모두 REVIEW 사유로 반환하고 BLOCK하지 않는다")
    void reviewAllHighJevRisksButNeverBlock() {
        // given
        final JevAssessment assessment = jev(0.9, 0.91, 0.85, 0.99);

        // when
        final RiskPolicyResult result = riskPolicyService.evaluate(new RiskPolicyInput(
                new RiskScore(0), new PolicySignals(List.of()), assessment));

        // then
        assertEquals(RiskDecision.REVIEW, result.decision(), "Jev만으로 BLOCK하지 않고 REVIEW 한다");
        assertEquals(List.of(PolicyReason.HIGH_SECURITY_RISK, PolicyReason.HIGH_AUTHORIZATION_RISK,
                PolicyReason.HIGH_DATA_INTEGRITY_RISK, PolicyReason.HIGH_BREAKING_CHANGE_RISK),
                result.reasons().values(), "모든 임계값 초과 reason code를 반환한다");
    }

    @Test
    @DisplayName("deterministic BLOCK은 Jev risk와 관계없이 유지한다")
    void deterministicBlockHasPriorityOverJev() {
        // given
        final JevAssessment low = jev(0, 0, 0, 0);
        final JevAssessment high = jev(1, 1, 1, 1);

        // when
        final RiskPolicyResult lowResult = riskPolicyService.evaluate(new RiskPolicyInput(
                new RiskScore(0), new PolicySignals(List.of(PolicySignal.BUILD_CONVENTION_FAILED)), low));
        final RiskPolicyResult highResult = riskPolicyService.evaluate(new RiskPolicyInput(
                new RiskScore(0), new PolicySignals(List.of(PolicySignal.BUILD_CONVENTION_FAILED)), high));

        // then
        assertEquals(RiskDecision.BLOCK, lowResult.decision(), "낮은 Jev risk가 deterministic BLOCK을 낮추지 않는다");
        assertEquals(RiskDecision.BLOCK, highResult.decision(), "높은 Jev risk도 deterministic BLOCK을 덮지 않는다");
    }

    @Test
    @DisplayName("누락된 Jev 임계값은 정책 설정 오류다")
    void rejectMissingJevRiskThreshold() {
        // given
        final RiskScore blockThreshold = new RiskScore(90);
        final RiskScore reviewThreshold = new RiskScore(70);

        // when
        final RiskAssessmentDomainException exception = assertThrows(
                RiskAssessmentDomainException.class,
                () -> new RiskPolicyService(blockThreshold, reviewThreshold, null)
        );

        // then
        assertEquals("RISK-ASSESSMENT-003", exception.errorCode().code(), "누락된 Jev 임계값을 설정 오류로 처리한다");
    }

    @Test
    @DisplayName("누락된 Jev review 확률 임계값은 거부한다")
    void rejectMissingJevReviewProbability() {
        // given
        final Runnable invalidConfiguration = () -> new JevRiskThresholds(null);

        // when
        final RiskAssessmentDomainException exception = assertThrows(
                RiskAssessmentDomainException.class,
                invalidConfiguration::run
        );

        // then
        assertEquals("RISK-ASSESSMENT-003", exception.errorCode().code(), "누락된 확률은 정책 설정 오류다");
    }

    private JevAssessment jev(
            final double security, final double authorization, final double integrity, final double breakingChange
    ) {
        return new JevAssessment(new RiskProbability(security), new RiskProbability(authorization),
                new RiskProbability(integrity), new RiskProbability(breakingChange));
    }

    @Test
    @DisplayName("review 임계값은 block 임계값보다 낮아야 한다")
    void rejectInvalidThresholdOrder() {
        // given
        final RiskScore blockThreshold = new RiskScore(70);
        final RiskScore reviewThreshold = new RiskScore(90);

        // when
        final RiskAssessmentDomainException exception = assertThrows(
                RiskAssessmentDomainException.class,
                () -> new RiskPolicyService(blockThreshold, reviewThreshold)
        );

        // then
        assertEquals("RISK-ASSESSMENT-003", exception.errorCode().code(), "임계값 순서 위반을 정책 설정 오류로 분류한다");
    }

    @Test
    @DisplayName("review 임계값이 block 임계값과 같으면 정책 설정 오류다")
    void rejectEqualThresholds() {
        // given
        final RiskScore threshold = new RiskScore(70);

        // when
        final RiskAssessmentDomainException exception = assertThrows(
                RiskAssessmentDomainException.class,
                () -> new RiskPolicyService(threshold, threshold)
        );

        // then
        assertEquals("RISK-ASSESSMENT-003", exception.errorCode().code(), "동일한 임계값을 거부한다");
    }

    @Test
    @DisplayName("누락된 block 임계값은 정책 설정 오류다")
    void rejectMissingBlockThreshold() {
        // given
        final RiskScore reviewThreshold = new RiskScore(70);

        // when
        final RiskAssessmentDomainException exception = assertThrows(
                RiskAssessmentDomainException.class,
                () -> new RiskPolicyService(null, reviewThreshold)
        );

        // then
        assertEquals("RISK-ASSESSMENT-003", exception.errorCode().code(), "누락된 block 임계값을 거부한다");
    }

    @Test
    @DisplayName("누락된 review 임계값은 정책 설정 오류다")
    void rejectMissingReviewThreshold() {
        // given
        final RiskScore blockThreshold = new RiskScore(90);

        // when
        final RiskAssessmentDomainException exception = assertThrows(
                RiskAssessmentDomainException.class,
                () -> new RiskPolicyService(blockThreshold, null)
        );

        // then
        assertEquals("RISK-ASSESSMENT-003", exception.errorCode().code(), "누락된 review 임계값을 거부한다");
    }

    @Test
    @DisplayName("잘못된 정책 모델 구성은 같은 정책 입력 ErrorCode를 반환한다")
    void rejectNullPolicyComponents() {
        // given
        final RiskScore score = new RiskScore(10);
        final PolicySignals signals = new PolicySignals(List.of());
        final PolicyReasons reasons = new PolicyReasons(List.of());
        final List<PolicySignal> nullSignals = Arrays.asList((PolicySignal) null);
        final List<PolicyReason> nullReasons = Arrays.asList((PolicyReason) null);

        // when
        final List<RiskAssessmentDomainException> exceptions = List.of(
                assertThrows(RiskAssessmentDomainException.class, () -> new PolicySignals(null)),
                assertThrows(RiskAssessmentDomainException.class, () -> new PolicySignals(nullSignals)),
                assertThrows(RiskAssessmentDomainException.class, () -> new PolicyReasons(null)),
                assertThrows(RiskAssessmentDomainException.class, () -> new PolicyReasons(nullReasons)),
                assertThrows(RiskAssessmentDomainException.class, () -> new RiskPolicyInput(null, signals)),
                assertThrows(RiskAssessmentDomainException.class, () -> new RiskPolicyInput(score, null)),
                assertThrows(RiskAssessmentDomainException.class, () -> new RiskPolicyResult(null, score, reasons)),
                assertThrows(
                        RiskAssessmentDomainException.class,
                        () -> new RiskPolicyResult(RiskDecision.PASS, null, reasons)
                ),
                assertThrows(
                        RiskAssessmentDomainException.class,
                        () -> new RiskPolicyResult(RiskDecision.PASS, score, null)
                )
        );

        // then
        assertEquals(9, exceptions.size(), "모든 null 구성 요소를 Domain 예외로 거부한다");
        assertEquals(
                true,
                exceptions.stream().allMatch(exception -> "RISK-ASSESSMENT-004".equals(exception.errorCode().code())),
                "정책 모델의 잘못된 입력은 안정된 ErrorCode를 사용한다"
        );
    }

    private RiskPolicyInput input(final int score, final List<PolicySignal> signals) {
        return new RiskPolicyInput(new RiskScore(score), new PolicySignals(signals));
    }
}
