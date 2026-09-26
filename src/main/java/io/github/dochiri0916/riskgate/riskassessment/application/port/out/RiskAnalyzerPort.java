package io.github.dochiri0916.riskgate.riskassessment.application.port.out;

import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase.AssessRiskCommand.ChangedFile;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.RiskCategory;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.RiskScore;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.RiskSeverity;
import java.util.List;

@FunctionalInterface
public interface RiskAnalyzerPort {
    Analysis analyze(AnalysisRequest request);

    record AnalysisRequest(List<Finding> findings, List<ChangedFile> changedFiles, String diff) {
        public AnalysisRequest {
            findings = List.copyOf(findings);
            changedFiles = List.copyOf(changedFiles);
        }

        @Override
        public String toString() {
            return "AnalysisRequest[diff=<redacted>]";
        }
    }

    record Analysis(RiskScore score, List<Finding> findings) {
        public Analysis {
            findings = List.copyOf(findings);
        }
    }

    record Finding(
            RiskCategory category,
            RiskSeverity severity,
            String source,
            String summary,
            String evidence
    ) { }
}
