package io.github.dochiri0916.riskgate.riskassessment.adapter.out.analysis;

import io.github.dochiri0916.riskgate.riskassessment.application.port.out.RiskAnalyzerPort;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.RiskScore;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.RiskSeverity;
import java.util.Comparator;
import org.springframework.stereotype.Component;

@Component
public final class DeterministicRiskAnalyzerAdapter implements RiskAnalyzerPort {
    @Override
    public Analysis analyze(final AnalysisRequest request) {
        final int score = request.findings().stream()
                .map(Finding::severity)
                .max(Comparator.comparingInt(RiskSeverity::score))
                .map(RiskSeverity::score)
                .orElse(0);
        return new Analysis(new RiskScore(score), request.findings());
    }
}
