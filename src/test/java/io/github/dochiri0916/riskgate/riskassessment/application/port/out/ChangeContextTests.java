package io.github.dochiri0916.riskgate.riskassessment.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChangeContextTests {
    @Test
    @DisplayName("change context는 입력 목록을 복사하고 diff를 숨긴다")
    void copiesContextListsAndRedactsDiff() {
        // given
        final List<ChangeContext.ChangedFile> files = new ArrayList<>();
        files.add(new ChangeContext.ChangedFile("A.java", "MODIFIED"));
        final List<ChangeContext.DeterministicFinding> findings = new ArrayList<>();
        findings.add(new ChangeContext.DeterministicFinding("SECURITY", "HIGH", "RULE", "A.java"));

        // when
        final ChangeContext context = new ChangeContext(files, "secret source", "PASS", findings);
        files.clear();
        findings.clear();

        // then
        assertThat(context.changedFiles()).hasSize(1);
        assertThat(context.deterministicFindings()).hasSize(1);
        assertThat(context.toString()).contains("diff=<redacted>").doesNotContain("secret source");
    }

    @Test
    @DisplayName("deterministic finding은 null evidence를 빈 값으로 정규화한다")
    void normalizesNullEvidence() {
        // given
        final java.util.concurrent.atomic.AtomicReference<String> evidenceReference =
                new java.util.concurrent.atomic.AtomicReference<>();
        final String rawEvidence = evidenceReference.get();
        // when
        final ChangeContext.DeterministicFinding finding =
                new ChangeContext.DeterministicFinding("SECURITY", "HIGH", "RULE", rawEvidence);

        // then
        assertThat(finding.evidence()).isEmpty();
    }
}
