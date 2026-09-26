package io.github.dochiri0916.riskgate.riskassessment.application.port.out;

import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase.AssessRiskCommand;
import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase.FindingResult;
import java.util.List;

@FunctionalInterface
public interface RiskAnalyzerPort {
    Analysis analyze(AssessRiskCommand command);

    record Analysis(int score, List<FindingResult> findings) {
        public Analysis {
            findings = List.copyOf(findings);
        }
    }

}
