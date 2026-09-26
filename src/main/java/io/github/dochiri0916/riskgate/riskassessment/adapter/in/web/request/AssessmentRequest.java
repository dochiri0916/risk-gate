package io.github.dochiri0916.riskgate.riskassessment.adapter.in.web.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AssessmentRequest(
        @NotBlank @Size(max = 20) String reportVersion,
        @NotBlank @Size(max = 300) String repository,
        @NotBlank @Pattern(regexp = "[0-9a-fA-F]{7,64}") String commitSha,
        @Positive Integer pullRequestNumber,
        @NotNull @Valid BuildConventionReportRequest buildConventionReport,
        @NotNull @Valid SemgrepReportRequest semgrepReport,
        @NotNull @Valid List<FindingRequest> findings,
        @NotNull @Valid List<ChangedFileRequest> changedFiles,
        String diff
) {
    public AssessmentRequest {
        findings = findings == null ? null : List.copyOf(findings);
        changedFiles = changedFiles == null ? null : List.copyOf(changedFiles);
        diff = diff == null ? "" : diff;
    }

    @Override
    public String toString() {
        return "AssessmentRequest[diff=<redacted>]";
    }

    public record FindingRequest(
            @NotBlank @Pattern(regexp = "CONCURRENCY|IDEMPOTENCY|TRANSACTION|DATA_INTEGRITY|PERFORMANCE|SECURITY|"
                    + "BREAKING_CHANGE|OPERABILITY")
            String category,
            @NotBlank @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL") String severity,
            @NotBlank @Size(max = 80) String source,
            @NotBlank @Size(max = 1000) String summary,
            @Size(max = 4000) String evidence
    ) { }

    public record ChangedFileRequest(
            @NotBlank @Size(max = 1000) String path,
            @NotBlank @Pattern(regexp = "ADDED|MODIFIED|DELETED|RENAMED") String changeType
    ) { }
}
