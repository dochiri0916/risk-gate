package io.github.dochiri0916.riskgate.riskassessment.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase.FindingResult;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.RiskCategory;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.RiskSeverity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FindingResultTests {
    @Test
    @DisplayName("누락된 분석 근거는 빈 문자열로 정규화한다")
    void normalizeMissingEvidence() {
        // given
        final FindingResult finding = new FindingResult(
                RiskCategory.OPERABILITY, RiskSeverity.LOW, "AI", "운영 신호 확인", null
        );

        // when
        final String evidence = finding.evidence();

        // then
        assertEquals("", evidence, "선택적 근거가 없으면 빈 문자열을 반환한다");
    }
}
