package io.github.dochiri0916.riskgate.riskassessment.adapter.in.web;

import io.github.dochiri0916.riskgate.riskassessment.adapter.in.web.request.AssessmentRequest;
import io.github.dochiri0916.riskgate.riskassessment.adapter.in.web.request.AssessmentRequest.CheckRequest;
import io.github.dochiri0916.riskgate.riskassessment.adapter.in.web.request.AssessmentRequest.ChangedFileRequest;
import io.github.dochiri0916.riskgate.riskassessment.adapter.in.web.request.AssessmentRequest.FindingRequest;
import io.github.dochiri0916.riskgate.riskassessment.adapter.in.web.response.AssessmentResponse;
import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase;
import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase.AssessRiskCommand;
import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase.AssessRiskCommand.Check;
import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase.AssessRiskCommand.ChangedFile;
import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase.AssessRiskCommand.FindingInput;
import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase.AssessRiskResult;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.RiskCategory;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.RiskSeverity;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/risk-assessments")
public final class RiskAssessmentController {
    private final AssessRiskUseCase assessRiskUseCase;

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public AssessmentResponse assess(@Valid @RequestBody final AssessmentRequest request) {
        final List<Check> checks = request.checks().stream()
                .map(RiskAssessmentController::toCommandCheck)
                .toList();
        final List<FindingInput> findings = request.findings().stream()
                .map(RiskAssessmentController::toCommandFinding)
                .toList();
        final List<ChangedFile> changedFiles = request.changedFiles().stream()
                .map(RiskAssessmentController::toCommandChangedFile)
                .toList();
        final AssessRiskCommand command = new AssessRiskCommand(
                request.reportVersion(), request.repository(), request.commitSha(), request.pullRequestNumber(),
                checks, findings, changedFiles
        );
        final AssessRiskResult result = assessRiskUseCase.assess(command);
        final List<AssessmentResponse.FindingResponse> resultFindings = result.findings().stream()
                .map(finding -> new AssessmentResponse.FindingResponse(
                        finding.category().name(), finding.severity().name(), finding.source(),
                        finding.summary(), finding.evidence()
                ))
                .toList();
        return new AssessmentResponse(
                result.assessmentId(), result.repository(), result.commitSha(), result.decision().name(),
                result.score(), result.reasonCodes(), resultFindings, result.policyVersion()
        );
    }

    private static Check toCommandCheck(final CheckRequest check) {
        return new Check(check.tool(), check.name(), check.status());
    }

    private static FindingInput toCommandFinding(final FindingRequest finding) {
        return new FindingInput(
                RiskCategory.valueOf(finding.category()),
                RiskSeverity.valueOf(finding.severity()),
                finding.source(),
                finding.summary(),
                finding.evidence()
        );
    }

    private static ChangedFile toCommandChangedFile(final ChangedFileRequest file) {
        return new ChangedFile(file.path(), file.changeType());
    }
}
