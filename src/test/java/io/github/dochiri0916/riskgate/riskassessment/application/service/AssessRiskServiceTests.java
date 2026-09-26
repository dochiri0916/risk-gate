package io.github.dochiri0916.riskgate.riskassessment.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase.AssessRiskCommand;
import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase.AssessRiskCommand.BuildConventionReport;
import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase.AssessRiskCommand.Coverage;
import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase.AssessRiskCommand.Mutation;
import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase.AssessRiskCommand.SemgrepReport;
import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase.AssessRiskCommand.SemgrepFinding;
import io.github.dochiri0916.riskgate.riskassessment.application.port.out.RiskAnalyzerPort;
import io.github.dochiri0916.riskgate.riskassessment.application.port.out.RiskAnalyzerPort.Analysis;
import io.github.dochiri0916.riskgate.riskassessment.application.port.out.RiskAnalyzerPort.AnalysisRequest;
import io.github.dochiri0916.riskgate.riskassessment.application.port.out.RiskAnalyzerPort.Finding;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.RiskCategory;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.RiskDecision;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.RiskPolicyService;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.RiskScore;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.RiskSeverity;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AssessRiskServiceTests {
    private final RiskPolicyService policy = new RiskPolicyService(new RiskScore(90), new RiskScore(70));

    @Test
    @DisplayName("분석 점수를 정책에 전달하고 분석 항목을 응답으로 변환한다")
    void passesAnalyzerScoreToPolicyAndMapsFindingsToResponse() {
        // given
        final AtomicReference<AnalysisRequest> received = new AtomicReference<>();
        final Finding analyzedFinding = new Finding(
                RiskCategory.SECURITY, RiskSeverity.LOW, "AI", "분석 결과", "분석 근거"
        );
        final RiskAnalyzerPort analyzer = request -> {
            received.set(request);
            return new Analysis(new RiskScore(70), List.of(analyzedFinding));
        };
        final AssessRiskService service = new AssessRiskService(
                analyzer, new RiskPolicyService(new RiskScore(90), new RiskScore(70)), () -> 262144
        );
        final AssessRiskCommand command = new AssessRiskCommand(
                "1", "org/service", "abcdef1234567", null, report("PASS", List.of()), new SemgrepReport(List.of()),
                List.of(new AssessRiskCommand.FindingInput(
                        RiskCategory.CONCURRENCY, RiskSeverity.HIGH, "INPUT", "입력 결과", "입력 근거"
                )), List.of(), ""
        );

        // when
        final var result = service.assess(command);

        // then
        assertThat(received.get().findings()).containsExactly(new Finding(
                RiskCategory.CONCURRENCY, RiskSeverity.HIGH, "INPUT", "입력 결과", "입력 근거"
        ));
        assertThat(result.repository()).isEqualTo("org/service");
        assertThat(result.commitSha()).isEqualTo("abcdef1234567");
        assertThat(result.decision()).isEqualTo(RiskDecision.REVIEW);
        assertThat(result.score()).isEqualTo(70);
        assertThat(result.reasonCodes()).containsExactly("RISK_SCORE_REVIEW_THRESHOLD");
        assertThat(result.findings()).hasSize(1);
        assertThat(result.findings().get(0).category()).isEqualTo(analyzedFinding.category());
        assertThat(result.findings().get(0).severity()).isEqualTo(analyzedFinding.severity());
        assertThat(result.findings().get(0).source()).isEqualTo(analyzedFinding.source());
        assertThat(result.findings().get(0).summary()).isEqualTo(analyzedFinding.summary());
        assertThat(result.findings().get(0).evidence()).isEqualTo(analyzedFinding.evidence());
        assertThat(result.policyVersion()).isEqualTo("1");
    }

    @Test
    @DisplayName("Build Convention 실패는 분석 결과와 관계없이 원래 점수로 차단한다")
    void buildConventionFailureBlocksWithoutChangingAnalyzerScore() {
        // given
        final RiskAnalyzerPort analyzer = request -> new Analysis(new RiskScore(10), List.of());
        final AssessRiskService service = new AssessRiskService(analyzer, policy, () -> 262144);
        final AssessRiskCommand command = command("FAIL", List.of());

        // when
        final var result = service.assess(command);

        // then
        assertThat(result.decision()).isEqualTo(RiskDecision.BLOCK);
        assertThat(result.score()).isEqualTo(10);
        assertThat(result.reasonCodes()).containsExactly("BUILD_CONVENTION_FAILED");
    }

    @Test
    @DisplayName("Build Convention 전체 상태가 PASS이면 개별 check만으로 차단하지 않는다")
    void overallBuildConventionStatusControlsBlockSignal() {
        // given
        final RiskAnalyzerPort analyzer = request -> new Analysis(new RiskScore(5), List.of());
        final AssessRiskService service = new AssessRiskService(analyzer, policy, () -> 262144);
        final BuildConventionReport report = report("PASS", List.of(
                new AssessRiskCommand.Check("BUILD_CONVENTION", "architecture", "FAIL")
        ));
        final AssessRiskCommand command = new AssessRiskCommand(
                "1", "org/service", "abcdef1234567", null, report, new SemgrepReport(List.of()),
                List.of(), List.of(), ""
        );

        // when
        final var result = service.assess(command);

        // then
        assertThat(result.decision()).isEqualTo(RiskDecision.PASS);
        assertThat(result.score()).isEqualTo(5);
        assertThat(result.reasonCodes()).containsExactly("WITHIN_POLICY_THRESHOLDS");
    }

    @Test
    @DisplayName("원본 Semgrep Critical 결과는 분석기가 제거해도 원래 점수로 차단한다")
    void semgrepCriticalBlocksWhenAnalyzerDropsFinding() {
        // given
        final RiskAnalyzerPort analyzer = request -> new Analysis(new RiskScore(10), List.of());
        final AssessRiskService service = new AssessRiskService(analyzer, policy, () -> 262144);
        final AssessRiskCommand command = new AssessRiskCommand("1", "org/service", "abcdef1234567", null,
                report("PASS", List.of()), new SemgrepReport(List.of(
                new SemgrepFinding(RiskSeverity.CRITICAL, "security.rule", "src/Order.java", 12, "위험")
        )), List.of(), List.of(), "");

        // when
        final var result = service.assess(command);

        // then
        assertThat(result.decision()).isEqualTo(RiskDecision.BLOCK);
        assertThat(result.score()).isEqualTo(10);
        assertThat(result.reasonCodes()).containsExactly("SEMGREP_CRITICAL");
        assertThat(result.findings()).isEmpty();
    }

    @Test
    @DisplayName("Semgrep 일반 위험 항목은 결정적 차단 신호를 만들지 않는다")
    void nonCriticalSemgrepFindingFollowsAnalyzerScore() {
        // given
        final RiskAnalyzerPort analyzer = request -> new Analysis(new RiskScore(10), request.findings());
        final AssessRiskService service = new AssessRiskService(analyzer, policy, () -> 262144);
        final AssessRiskCommand command = new AssessRiskCommand("1", "org/service", "abcdef1234567", null,
                report("PASS", List.of()), new SemgrepReport(List.of(
                new SemgrepFinding(RiskSeverity.LOW, "security.rule", "src/Order.java", 12, "위험")
        )), List.of(), List.of(), "");

        // when
        final var result = service.assess(command);

        // then
        assertThat(result.decision()).isEqualTo(RiskDecision.PASS);
        assertThat(result.score()).isEqualTo(10);
        assertThat(result.reasonCodes()).containsExactly("WITHIN_POLICY_THRESHOLDS");
        assertThat(result.findings()).extracting(finding -> finding.source())
                .containsExactly("SEMGREP");
    }

    @Test
    @DisplayName("분석기가 위험 항목을 Critical로 바꿔도 원본에 없던 차단 신호를 만들지 않는다")
    void analyzerReclassificationDoesNotCreateDeterministicSignal() {
        // given
        final Finding reclassified = new Finding(
                RiskCategory.SECURITY, RiskSeverity.CRITICAL, "SEMGREP", "재분류", "근거"
        );
        final RiskAnalyzerPort analyzer = request -> new Analysis(new RiskScore(15), List.of(reclassified));
        final AssessRiskService service = new AssessRiskService(analyzer, policy, () -> 262144);
        final AssessRiskCommand command = command("PASS", List.of(new AssessRiskCommand.FindingInput(
                RiskCategory.SECURITY, RiskSeverity.LOW, "SEMGREP", "원본", "근거"
        )));

        // when
        final var result = service.assess(command);

        // then
        assertThat(result.decision()).isEqualTo(RiskDecision.PASS);
        assertThat(result.score()).isEqualTo(15);
        assertThat(result.reasonCodes()).containsExactly("WITHIN_POLICY_THRESHOLDS");
        assertThat(result.findings().get(0).severity()).isEqualTo(RiskSeverity.CRITICAL);
    }

    @Test
    @DisplayName("일반 위험 항목은 분석 점수에 따라 정책이 판정한다")
    void ordinaryFindingsFollowAnalyzerScoreThresholds() {
        // given
        final AssessRiskCommand command = command("PASS", List.of(new AssessRiskCommand.FindingInput(
                RiskCategory.SECURITY, RiskSeverity.CRITICAL, "AI", "위험", "근거"
        )));
        final List<RiskScore> scores = List.of(new RiskScore(10), new RiskScore(70), new RiskScore(90));
        final List<RiskDecision> decisions = List.of(RiskDecision.PASS, RiskDecision.REVIEW, RiskDecision.BLOCK);

        // when & then
        for (int index = 0; index < scores.size(); index++) {
            final RiskScore score = scores.get(index);
            final RiskAnalyzerPort analyzer = request -> new Analysis(score, request.findings());
            final var result = new AssessRiskService(analyzer, policy, () -> 262144).assess(command);
            assertThat(result.decision()).isEqualTo(decisions.get(index));
            assertThat(result.score()).isEqualTo(score.value());
        }
    }

    @Test
    @DisplayName("변경 파일과 diff를 분석 입력에 보존한다")
    void preservesAllChangedFileTypesAndDiffWithoutChangingPolicy() {
        // given
        final AtomicReference<AnalysisRequest> received = new AtomicReference<>();
        final RiskAnalyzerPort analyzer = request -> {
            received.set(request);
            return new Analysis(new RiskScore(0), List.of());
        };
        final var files = List.of(
                new AssessRiskCommand.ChangedFile("added.java", "ADDED"),
                new AssessRiskCommand.ChangedFile("modified.java", "MODIFIED"),
                new AssessRiskCommand.ChangedFile("deleted.java", "DELETED"),
                new AssessRiskCommand.ChangedFile("renamed.java", "RENAMED")
        );
        final String diff = "diff --git a/deleted.java b/deleted.java\n+secret-marker";
        final var command = new AssessRiskCommand("1", "org/service", "abcdef1234567", null,
                report("PASS", List.of()), new SemgrepReport(List.of()), List.of(), files, diff);

        // when
        final var result = new AssessRiskService(analyzer, policy, () -> 262144).assess(command);

        // then
        assertThat(received.get().changedFiles()).containsExactlyElementsOf(files);
        assertThat(received.get().diff()).isEqualTo(diff);
        assertThat(result.decision()).isEqualTo(RiskDecision.PASS);
        assertThat(command.toString()).doesNotContain("secret-marker");
        assertThat(received.get().toString()).doesNotContain("secret-marker");
    }

    @Test
    @DisplayName("diff가 차단 정책에 영향을 주지 않는다")
    void diffDoesNotAffectDeterministicBlockSignals() {
        // given
        final RiskAnalyzerPort analyzer = request -> new Analysis(new RiskScore(0), List.of());
        final var service = new AssessRiskService(analyzer, policy, () -> 262144);
        final String diff = "diff --git a/file b/file\n+transaction delete secret-marker";
        final var conventionFailure = new AssessRiskCommand("1", "org/service", "abcdef1234567", null,
                report("FAIL", List.of()), new SemgrepReport(List.of()), List.of(), List.of(), diff);
        final var semgrepCritical = new AssessRiskCommand("1", "org/service", "abcdef1234567", null,
                report("PASS", List.of()), new SemgrepReport(List.of(
                new SemgrepFinding(RiskSeverity.CRITICAL, "rule", "file", 1, "issue")
        )), List.of(), List.of(), diff);

        // when & then
        assertThat(service.assess(conventionFailure).decision()).isEqualTo(RiskDecision.BLOCK);
        assertThat(service.assess(semgrepCritical).decision()).isEqualTo(RiskDecision.BLOCK);
    }

    private AssessRiskCommand command(
            final String reportStatus,
            final List<AssessRiskCommand.FindingInput> findings
    ) {
        return new AssessRiskCommand("1", "org/service", "abcdef1234567", null,
                report(reportStatus, List.of()), new SemgrepReport(List.of()), findings, List.of(), "");
    }

    private BuildConventionReport report(final String status, final List<AssessRiskCommand.Check> checks) {
        return new BuildConventionReport("1.0", "1.0.1", status, checks, new Coverage(null, null),
                new Mutation(false, null));
    }
}
