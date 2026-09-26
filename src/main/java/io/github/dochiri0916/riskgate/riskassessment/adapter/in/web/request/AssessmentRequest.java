package io.github.dochiri0916.riskgate.riskassessment.adapter.in.web.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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
        @NotEmpty @Valid List<CheckRequest> checks,
        @NotNull @Valid List<FindingRequest> findings,
        @NotNull @Valid List<ChangedFileRequest> changedFiles
) {
    public AssessmentRequest {
        checks = checks == null ? null : List.copyOf(checks);
        findings = findings == null ? null : List.copyOf(findings);
        changedFiles = changedFiles == null ? null : List.copyOf(changedFiles);
    }

    public record CheckRequest(
            @NotBlank @Size(max = 80) String tool,
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Pattern(regexp = "PASS|FAIL|WARN") String status
    ) { }

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
            @NotBlank @Size(max = 40) String changeType
    ) { }
}
