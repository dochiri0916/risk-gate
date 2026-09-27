package io.github.dochiri0916.riskgate.riskassessment.application.service;

import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase;
import io.github.dochiri0916.riskgate.riskassessment.application.port.out.RiskAnalyzerPort;
import io.github.dochiri0916.riskgate.riskassessment.application.port.out.DiffLimitPort;
import io.github.dochiri0916.riskgate.riskassessment.application.port.out.ChangeContext;
import io.github.dochiri0916.riskgate.riskassessment.application.port.out.SecurityRiskAssessmentPort;
import io.github.dochiri0916.riskgate.riskassessment.application.port.out.RiskAnalyzerPort.Analysis;
import io.github.dochiri0916.riskgate.riskassessment.application.port.out.RiskAnalyzerPort.AnalysisRequest;
import io.github.dochiri0916.riskgate.riskassessment.application.port.out.RiskAnalyzerPort.Finding;
import io.github.dochiri0916.riskgate.riskassessment.domain.exception.RiskAssessmentDomainException;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.PolicyReason;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.PolicySignal;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.PolicySignals;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.RiskPolicyInput;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.RiskPolicyResult;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.RiskPolicyService;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.RiskDecision;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.RiskSeverity;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.JevAssessment;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public final class AssessRiskService implements AssessRiskUseCase {
    private static final String SUPPORTED_REPORT_VERSION = "1";
    private static final String SUPPORTED_BUILD_CONVENTION_SCHEMA_VERSION = "1.0";
    private static final String POLICY_VERSION = "1";
    private final RiskAnalyzerPort riskAnalyzerPort;
    private final RiskPolicyService riskPolicyService;
    private final DiffLimitPort diffLimitPort;
    private final SecurityRiskAssessmentPort securityRiskAssessmentPort;

    @Override
    @Transactional
    public AssessRiskResult assess(final AssessRiskCommand command) {
        if (!SUPPORTED_REPORT_VERSION.equals(command.reportVersion())) {
            throw RiskAssessmentDomainException.invalidReportSchema(command.reportVersion());
        }
        if (!SUPPORTED_BUILD_CONVENTION_SCHEMA_VERSION.equals(command.buildConventionReport().schemaVersion())) {
            throw RiskAssessmentDomainException.invalidBuildConventionSchema(
                    command.buildConventionReport().schemaVersion()
            );
        }
        if (command.diff().length() > diffLimitPort.maxLength()) {
            throw RiskAssessmentDomainException.diffTooLarge();
        }
        final List<Finding> analyzerFindings = new ArrayList<>(command.findings().stream()
                .map(finding -> new Finding(
                        finding.category(), finding.severity(), finding.source(), finding.summary(), finding.evidence()
                ))
                .toList());
        if (command.semgrepReport() != null) {
            command.semgrepReport().results().forEach(result -> analyzerFindings.add(new Finding(
                    io.github.dochiri0916.riskgate.riskassessment.domain.model.RiskCategory.SECURITY,
                    result.severity(), "SEMGREP", result.message(),
                    result.ruleId() + " at " + result.path() + ":" + result.line()
            )));
        }
        final AnalysisRequest request = new AnalysisRequest(analyzerFindings, command.changedFiles(), command.diff());
        final Analysis analysis = riskAnalyzerPort.analyze(request);
        final RiskPolicyInput deterministicInput = new RiskPolicyInput(analysis.score(), signalsFor(command));
        final RiskPolicyResult deterministicResult = riskPolicyService.evaluate(deterministicInput);
        final boolean deterministicBlock = deterministicResult.decision()
                == RiskDecision.BLOCK;
        final JevAssessment jevAssessment = command.jevEnabled() && !deterministicBlock
                ? securityRiskAssessmentPort.assess(changeContext(command)) : null;
        final RiskPolicyResult policyResult = jevAssessment == null
                ? deterministicResult
                : riskPolicyService.evaluate(
                        new RiskPolicyInput(analysis.score(), signalsFor(command), jevAssessment));
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
                analysis.findings().stream()
                        .map(finding -> new FindingResult(
                                finding.category(), finding.severity(), finding.source(), finding.summary(),
                                finding.evidence()
                        ))
                        .toList(),
                POLICY_VERSION,
                jevAssessment
        );
    }

    private ChangeContext changeContext(final AssessRiskCommand command) {
        return new ChangeContext(
                command.changedFiles().stream()
                        .map(file -> new ChangeContext.ChangedFile(file.path(), file.changeType())).toList(),
                command.diff(),
                command.buildConventionReport().status(),
                command.findings().stream().map(finding -> new ChangeContext.DeterministicFinding(
                        finding.category().name(), finding.severity().name(), finding.source(), finding.evidence()
                )).toList()
        );
    }

    private PolicySignals signalsFor(final AssessRiskCommand command) {
        final List<PolicySignal> signals = new ArrayList<>();
        final boolean conventionFailed = "FAIL".equals(command.buildConventionReport().status());
        final boolean criticalSemgrepFinding = command.semgrepReport() != null
                && command.semgrepReport().results().stream()
                        .anyMatch(finding -> RiskSeverity.CRITICAL == finding.severity());
        if (conventionFailed) {
            signals.add(PolicySignal.BUILD_CONVENTION_FAILED);
        }
        if (criticalSemgrepFinding) {
            signals.add(PolicySignal.SEMGREP_CRITICAL);
        }
        return new PolicySignals(signals);
    }
}
