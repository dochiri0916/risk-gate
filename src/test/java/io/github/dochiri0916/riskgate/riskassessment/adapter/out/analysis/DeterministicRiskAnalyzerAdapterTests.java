package io.github.dochiri0916.riskgate.riskassessment.adapter.out.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dochiri0916.riskgate.riskassessment.application.port.out.RiskAnalyzerPort.AnalysisRequest;
import io.github.dochiri0916.riskgate.riskassessment.application.port.out.RiskAnalyzerPort.Finding;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.RiskCategory;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.RiskScore;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.RiskSeverity;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DeterministicRiskAnalyzerAdapterTests {
    private final DeterministicRiskAnalyzerAdapter analyzer = new DeterministicRiskAnalyzerAdapter();

    @Test
    @DisplayName("위험 항목이 없으면 점수는 0이다")
    void zeroScoreWhenThereAreNoFindings() {
        // given
        final AnalysisRequest request = new AnalysisRequest(List.of(), List.of(), "");

        // when
        final var analysis = analyzer.analyze(request);

        // then
        assertThat(analysis.score()).isEqualTo(new RiskScore(0));
    }

    @Test
    @DisplayName("가장 높은 위험도의 점수를 사용한다")
    void highestSeverityDeterminesRiskScore() {
        // given
        final Finding low = finding(RiskSeverity.LOW);
        final Finding critical = finding(RiskSeverity.CRITICAL);
        final Finding high = finding(RiskSeverity.HIGH);

        // when
        final var analysis = analyzer.analyze(new AnalysisRequest(List.of(low, critical, high), List.of(), ""));

        // then
        assertThat(analysis.score()).isEqualTo(new RiskScore(90));
        assertThat(analysis.findings()).containsExactly(low, critical, high);
    }

    private Finding finding(final RiskSeverity severity) {
        return new Finding(RiskCategory.SECURITY, severity, "AI", "위험", "근거");
    }
}
