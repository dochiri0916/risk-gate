package io.github.dochiri0916.riskgate.riskassessment.application.service;

import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase;
import io.github.dochiri0916.riskgate.riskassessment.application.port.out.RiskAnalyzerPort;
import io.github.dochiri0916.riskgate.riskassessment.application.port.out.RiskAnalyzerPort.Analysis;
import io.github.dochiri0916.riskgate.riskassessment.domain.exception.RiskAssessmentDomainException;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.PolicyReason;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.PolicySignal;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.PolicySignals;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.RiskPolicyInput;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.RiskPolicyResult;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.RiskPolicyService;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.RiskScore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public final class AssessRiskService implements AssessRiskUseCase {
    private static final String SUPPORTED_REPORT_VERSION = "1";
    private static final String POLICY_VERSION = "1";
    private final RiskAnalyzerPort riskAnalyzerPort;
    private final RiskPolicyService riskPolicyService;

    @Override
    @Transactional
    public AssessRiskResult assess(final AssessRiskCommand command) {
        if (!SUPPORTED_REPORT_VERSION.equals(command.reportVersion())) {
            throw RiskAssessmentDomainException.invalidReportSchema(command.reportVersion());
        }
        final Analysis analysis = riskAnalyzerPort.analyze(command);
        final RiskPolicyResult policyResult = riskPolicyService.evaluate(
                new RiskPolicyInput(new RiskScore(analysis.score()), signalsFor(command, analysis))
        );
        final List<String> reasonCodes = policyResult.reasons().values().stream()
                .map(PolicyReason::name)
                .toList();
        return new AssessRiskResult(
                UUID.randomUUID(),
                command.repository(),
                command.commitSha(),
                policyResult.decision(),
                policyResult.score().value(),
                reasonCodes,
                analysis.findings(),
                POLICY_VERSION
        );
    }

    private PolicySignals signalsFor(final AssessRiskCommand command, final Analysis analysis) {
        final List<PolicySignal> signals = new ArrayList<>();
        final boolean conventionFailed = command.checks().stream().anyMatch(check ->
                "BUILD_CONVENTION".equalsIgnoreCase(check.tool())
                        && "FAIL".equalsIgnoreCase(check.status())
        );
        final boolean criticalSemgrepFinding = analysis.findings().stream().anyMatch(finding ->
                "SEMGREP".equalsIgnoreCase(finding.source())
                        && "CRITICAL".equals(finding.severity().name())
        );
        if (conventionFailed) {
            signals.add(PolicySignal.BUILD_CONVENTION_FAILED);
        }
        if (criticalSemgrepFinding) {
            signals.add(PolicySignal.SEMGREP_CRITICAL);
        }
        return new PolicySignals(signals);
    }
}
