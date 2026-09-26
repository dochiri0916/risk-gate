package io.github.dochiri0916.riskgate.riskassessment.adapter.out.analysis;

import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase.AssessRiskCommand;
import io.github.dochiri0916.riskgate.riskassessment.application.port.out.RiskAnalyzerPort;
import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase.FindingResult;
import java.util.Comparator;
import org.springframework.stereotype.Component;

@Component
public final class DeterministicRiskAnalyzerAdapter implements RiskAnalyzerPort {
    @Override
    public Analysis analyze(final AssessRiskCommand command) {
        final var findings = command.findings().stream()
                .map(finding -> new FindingResult(
                        finding.category(), finding.severity(), finding.source(), finding.summary(), finding.evidence()
                ))
                .toList();
        final int score = findings.stream()
                .map(FindingResult::severity)
                .max(Comparator.comparingInt(severity -> severity.score()))
                .map(severity -> severity.score())
                .orElse(0);
        return new Analysis(score, findings);
    }
}
