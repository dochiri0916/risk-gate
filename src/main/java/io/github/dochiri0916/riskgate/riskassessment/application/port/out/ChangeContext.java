package io.github.dochiri0916.riskgate.riskassessment.application.port.out;

import java.util.List;

public record ChangeContext(
        List<ChangedFile> changedFiles,
        String diff,
        String buildConventionStatus,
        List<DeterministicFinding> deterministicFindings
) {
    public ChangeContext {
        changedFiles = List.copyOf(changedFiles);
        deterministicFindings = List.copyOf(deterministicFindings);
    }

    @Override
    public String toString() {
        return "ChangeContext[changedFiles=" + changedFiles.size() + ", diff=<redacted>]";
    }

    public record ChangedFile(String path, String changeType) { }

    public record DeterministicFinding(String category, String severity, String code, String evidence) {
        public DeterministicFinding {
            evidence = evidence == null ? "" : evidence;
        }
    }
}
