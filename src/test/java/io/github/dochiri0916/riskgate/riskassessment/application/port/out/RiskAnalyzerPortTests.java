package io.github.dochiri0916.riskgate.riskassessment.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dochiri0916.riskgate.riskassessment.application.port.out.RiskAnalyzerPort.Analysis;
import io.github.dochiri0916.riskgate.riskassessment.application.port.out.RiskAnalyzerPort.AnalysisRequest;
import io.github.dochiri0916.riskgate.riskassessment.application.port.out.RiskAnalyzerPort.Finding;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.RiskDecision;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.RiskScore;
import java.lang.reflect.ParameterizedType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RiskAnalyzerPortTests {
    @Test
    @DisplayName("분석 포트는 전용 입력과 결과 및 위험 점수를 사용한다")
    void portContractUsesItsOwnRequestAndFindingsWithRiskScore() throws NoSuchMethodException {
        // given
        final var analyze = RiskAnalyzerPort.class.getMethod("analyze", AnalysisRequest.class);
        final var requestFindings = (ParameterizedType) AnalysisRequest.class.getRecordComponents()[0]
                .getGenericType();
        final var analyzedFindings = (ParameterizedType) Analysis.class.getRecordComponents()[1]
                .getGenericType();

        // when & then
        assertThat(analyze.getReturnType()).isEqualTo(Analysis.class);
        assertThat(Analysis.class.getRecordComponents()).hasSize(2);
        assertThat(requestFindings.getActualTypeArguments()).containsExactly(Finding.class);
        assertThat(Analysis.class.getRecordComponents()[0].getType()).isEqualTo(RiskScore.class);
        assertThat(Analysis.class.getRecordComponents()[1].getType()).isEqualTo(List.class);
        assertThat(analyzedFindings.getActualTypeArguments()).containsExactly(Finding.class);
        assertThat(Analysis.class.getRecordComponents())
                .noneMatch(component -> component.getType().equals(RiskDecision.class));
    }
}
