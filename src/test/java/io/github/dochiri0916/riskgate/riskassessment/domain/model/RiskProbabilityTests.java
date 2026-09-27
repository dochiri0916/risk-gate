package io.github.dochiri0916.riskgate.riskassessment.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dochiri0916.riskgate.riskassessment.domain.exception.RiskAssessmentDomainException;
import io.github.dochiri0916.riskgate.riskassessment.domain.exception.RiskAssessmentErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RiskProbabilityTests {
    @Test
    @DisplayName("0과 1을 포함한 확률을 보존한다")
    void acceptsProbabilityBoundaries() {
        // given
        final RiskProbability zero = new RiskProbability(0.0);
        final RiskProbability one = new RiskProbability(1.0);

        // when
        final double zeroValue = zero.value();
        final double oneValue = one.value();

        // then
        assertThat(zeroValue).isEqualTo(0.0);
        assertThat(oneValue).isEqualTo(1.0);
    }

    @Test
    @DisplayName("범위를 벗어난 확률과 유한하지 않은 값은 거부한다")
    void rejectsInvalidProbabilityValues() {
        // given
        final double[] invalidValues = {-0.01, 1.01, Double.NaN, Double.POSITIVE_INFINITY};

        // when
        final var errors = java.util.Arrays.stream(invalidValues)
                .mapToObj(value -> {
                    try {
                        new RiskProbability(value);
                        throw new AssertionError("Expected invalid probability");
                    } catch (RiskAssessmentDomainException exception) {
                        return exception.errorCode();
                    }
                })
                .toList();

        // then
        assertThat(errors).containsOnly(RiskAssessmentErrorCode.INVALID_JEV_ASSESSMENT).hasSize(4);
    }

    @Test
    @DisplayName("Jev assessment는 확률 Value Object를 모두 요구한다")
    void rejectsMissingAssessmentDimensions() {
        // given
        final RiskProbability probability = new RiskProbability(0.5);

        // when
        final var errors = java.util.List.of(
                assessmentError(null, probability, probability, probability),
                assessmentError(probability, null, probability, probability),
                assessmentError(probability, probability, null, probability),
                assessmentError(probability, probability, probability, null)
        );

        // then
        assertThat(errors).containsOnly(RiskAssessmentErrorCode.INVALID_JEV_ASSESSMENT).hasSize(4);
    }

    private RiskAssessmentErrorCode assessmentError(
            final RiskProbability security,
            final RiskProbability authorization,
            final RiskProbability integrity,
            final RiskProbability breakingChange
    ) {
        try {
            new JevAssessment(security, authorization, integrity, breakingChange);
            throw new AssertionError("Expected missing assessment dimension");
        } catch (RiskAssessmentDomainException exception) {
            return (RiskAssessmentErrorCode) exception.errorCode();
        }
    }
}
