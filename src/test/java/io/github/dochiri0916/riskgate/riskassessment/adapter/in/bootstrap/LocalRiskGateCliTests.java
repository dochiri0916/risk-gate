package io.github.dochiri0916.riskgate.riskassessment.adapter.in.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.ObjectMapper;
import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase;
import io.github.dochiri0916.riskgate.riskassessment.application.service.AssessRiskService;
import io.github.dochiri0916.riskgate.riskassessment.adapter.out.analysis.DeterministicRiskAnalyzerAdapter;
import io.github.dochiri0916.riskgate.riskassessment.application.port.out.SecurityRiskAssessmentPort.FailureKind;
import io.github.dochiri0916.riskgate.riskassessment.application.port.out.SecurityRiskAssessmentPort.SecurityRiskAssessmentException;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.RiskPolicyService;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.RiskScore;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.JevAssessment;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.RiskProbability;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalRiskGateCliTests {
    @TempDir
    Path project;
    private static final PrintStream ORIGINAL_OUT = System.out;
    private static final PrintStream ORIGINAL_ERR = System.err;
    private ByteArrayOutputStream stdout;
    private ByteArrayOutputStream stderr;
    private LocalRiskGateCli cli;

    @BeforeEach
    void setUp() throws IOException, InterruptedException {
        stdout = new ByteArrayOutputStream();
        stderr = new ByteArrayOutputStream();
        System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
        cli = new LocalRiskGateCli(service(), new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8));
        git("init", "-q");
        git("config", "user.email", "test@example.invalid");
        git("config", "user.name", "Risk Gate Test");
        write("base.txt", "base\n");
        write("src/Main.java", "class Main { int value() { return 1; } }\n");
        git("add", "base.txt");
        git("add", "src/Main.java");
        git("commit", "-qm", "base");
        write("build/reports/build-convention/report.json", Files.readString(
                Path.of("src/test/resources/build-convention/pass-report.json")));
    }

    @AfterEach
    void restoreStreams() {
        System.setOut(ORIGINAL_OUT);
        System.setErr(ORIGINAL_ERR);
    }

    @Test
    @DisplayName("일반 변경은 PASS이며 Semgrep은 미실행으로 표시한다")
    void passReportAndOrdinaryWorkingTreeChangePassAndMarkSemgrepNotRun() throws IOException {
        // given
        write("src/Main.java", "class Main { int value() { return 2; } }\n");

        // when & then
        final int exit = cli.run(args());

        assertThat(exit).isZero();
        final var result = new ObjectMapper().readTree(stdout.toString(StandardCharsets.UTF_8));
        assertThat(result.path("decision").asText()).isEqualTo("PASS");
        assertThat(result.path("score").asInt()).isZero();
        assertThat(result.path("findings")).isEmpty();
        assertThat(result.path("semgrep").asText()).isEqualTo("NOT_RUN");
        assertThat(result.path("semgrep").asText()).isNotEqualTo("PASS");
        assertThat(result.path("jev").path("status").asText()).isEqualTo("NOT_RUN");
        assertThat(stderr.size()).isZero();
    }

    @Test
    @DisplayName("Jev 활성 상태의 provider 오류는 PASS가 아닌 ERROR JSON과 exit code를 반환한다")
    void jevProviderFailureReturnsStructuredErrorWithoutPassing() throws IOException {
        // given
        cli = new LocalRiskGateCli(service(), new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8), true);
        write("src/Main.java", "class Main { int value() { return 2; } }\n");

        // when
        final int exit = cli.run(args());

        // then
        assertThat(exit).isEqualTo(4);
        final var result = new ObjectMapper().readTree(stdout.toString(StandardCharsets.UTF_8));
        assertThat(result.path("decision").asText()).isEqualTo("ERROR");
        assertThat(result.path("decision").asText()).isNotEqualTo("PASS");
        assertThat(result.path("reasonCodes").get(0).asText()).isEqualTo("JEV_ANALYSIS_FAILED");
        assertThat(result.path("jev").path("status").asText()).isEqualTo("ERROR");
        assertThat(result.path("jev").path("failureKind").asText()).isEqualTo("CONFIGURATION_ERROR");
    }

    @Test
    @DisplayName("Jev 분석 성공 시 확률과 semantic REVIEW 사유를 JSON으로 반환한다")
    void enabledJevReturnsStructuredProbabilities() throws IOException {
        // given
        final JevAssessment assessment = new JevAssessment(
                new RiskProbability(0.12), new RiskProbability(0.91),
                new RiskProbability(0.2), new RiskProbability(0.18));
        final var jevService = new AssessRiskService(new DeterministicRiskAnalyzerAdapter(),
                new RiskPolicyService(new RiskScore(90), new RiskScore(70)), () -> Integer.MAX_VALUE,
                context -> assessment);
        cli = new LocalRiskGateCli(jevService, new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8), true);
        write("src/Main.java", "class Main { int value() { return 2; } }\n");

        // when
        final int exit = cli.run(args());

        // then
        assertThat(exit).isEqualTo(2);
        final var result = new ObjectMapper().readTree(stdout.toString(StandardCharsets.UTF_8));
        assertThat(result.path("decision").asText()).isEqualTo("REVIEW");
        assertThat(result.path("reasonCodes").get(0).asText()).isEqualTo("HIGH_AUTHORIZATION_RISK");
        assertThat(result.path("jev").path("status").asText()).isEqualTo("PASS");
        assertThat(result.path("jev").path("authorizationRisk").asDouble()).isEqualTo(0.91);
    }

    @Test
    @DisplayName("추가된 secret literal은 BLOCK하고 값은 결과에서 숨긴다")
    void addedSecretLiteralBlocksWithoutExposingValue() throws IOException {
        // given
        final String fakeSecret = "fake-test-token-123456";
        write("src/main/java/Example.java", "class Example { String token = \"" + fakeSecret + "\"; }\n");

        // when & then
        assertThat(cli.run(args())).isEqualTo(3);
        final String json = stdout.toString(StandardCharsets.UTF_8);
        final var result = new ObjectMapper().readTree(json);
        assertThat(result.path("decision").asText()).isEqualTo("BLOCK");
        assertThat(result.path("score").asInt()).isEqualTo(90);
        assertThat(result.path("findings").get(0).path("code").asText())
                .isEqualTo("SECRET_MATERIAL_ADDED");
        assertThat(json).doesNotContain(fakeSecret);
    }

    @Test
    @DisplayName("migration에 추가된 파괴적 SQL은 BLOCK한다")
    void destructiveMigrationBlocks() throws IOException {
        // given
        write("src/main/resources/db/migration/V2__cleanup.sql", "DROP TABLE accounts;\n");

        // when & then
        assertThat(cli.run(args())).isEqualTo(3);
        final var result = new ObjectMapper().readTree(stdout.toString(StandardCharsets.UTF_8));
        assertThat(result.path("decision").asText()).isEqualTo("BLOCK");
        assertThat(result.path("findings").get(0).path("code").asText())
                .isEqualTo("DESTRUCTIVE_MIGRATION");
    }

    @Test
    @DisplayName("Build Convention FAIL은 BLOCK한다")
    void buildConventionFailBlocks() throws IOException {
        // given
        final Path report = project.resolve("build/reports/build-convention/report.json");
        Files.writeString(report, Files.readString(report).replace("\"status\": \"PASS\"", "\"status\": \"FAIL\""));

        // when & then
        assertThat(cli.run(args())).isEqualTo(3);
        assertThat(new ObjectMapper().readTree(stdout.toString(StandardCharsets.UTF_8)).path("decision").asText())
                .isEqualTo("BLOCK");
    }

    @Test
    @DisplayName("기준 ref가 없으면 오류로 종료한다")
    void noUsableBaseFailsWithoutPass() {
        // given
        final String[] arguments = {"local", "--project", project.toString(), "--base", "missing-ref"};

        // when & then
        assertThat(cli.run(arguments))
                .isEqualTo(4);
        assertThat(stdout.size()).isZero();
        assertThat(stderr.toString(StandardCharsets.UTF_8)).contains("Base ref does not resolve");
    }

    @Test
    @DisplayName("Build Convention report가 없으면 오류로 종료한다")
    void missingReportFailsWithoutPass() throws IOException {
        // given
        Files.delete(project.resolve("build/reports/build-convention/report.json"));

        // when & then
        assertThat(cli.run(args())).isEqualTo(4);
        assertThat(stdout.size()).isZero();
    }

    @Test
    @DisplayName("staged와 untracked Java 변경을 diff에 포함한다")
    void stagedJavaAndUntrackedJavaChangesAreCollected() throws IOException, InterruptedException {
        // given
        write("src/Staged.java", "class Staged {}\n");
        git("add", "src/Staged.java");
        write("src/NewFile.java", "class NewFile {}\n");

        // when
        final GitWorkingTree.Snapshot snapshot = GitWorkingTree.snapshot(project, GitWorkingTree.head(project));

        // then
        assertThat(snapshot.changedFiles()).contains(
                new AssessRiskUseCase.AssessRiskCommand.ChangedFile("src/Staged.java", "ADDED"),
                new AssessRiskUseCase.AssessRiskCommand.ChangedFile("src/NewFile.java", "ADDED"));
        assertThat(snapshot.diff()).contains("src/Staged.java", "src/NewFile.java", "class Staged", "class NewFile");
    }

    @Test
    @DisplayName("rename과 delete changeType을 정확히 기록한다")
    void renameAndDeleteAreReportedAccurately() throws IOException, InterruptedException {
        // given
        write("old.java", "class Old {}\n");
        write("deleted.java", "class Deleted {}\n");
        git("add", "old.java", "deleted.java");
        git("commit", "-qm", "add files");
        git("mv", "old.java", "new.java");
        Files.delete(project.resolve("deleted.java"));

        // when
        final GitWorkingTree.Snapshot snapshot = GitWorkingTree.snapshot(project, GitWorkingTree.head(project));

        // then
        assertThat(snapshot.changedFiles()).contains(
                new AssessRiskUseCase.AssessRiskCommand.ChangedFile("new.java", "RENAMED"),
                new AssessRiskUseCase.AssessRiskCommand.ChangedFile("deleted.java", "DELETED"));
    }

    @Test
    @DisplayName("REVIEW 결정은 exit code 2를 반환한다")
    void reviewDecisionUsesReviewExitCode() throws IOException {
        // given
        write("build.gradle", "plugins { id 'java' }\n");

        // when & then
        assertThat(cli.run(args())).isEqualTo(2);
        final var result = new ObjectMapper().readTree(stdout.toString(StandardCharsets.UTF_8));
        assertThat(result.path("decision").asText()).isEqualTo("REVIEW");
        assertThat(result.path("score").asInt()).isEqualTo(70);
        assertThat(result.path("findings").get(0).path("code").asText())
                .isEqualTo("BUILD_CONFIGURATION_CHANGE");
        assertThat(result.path("reasonCodes").get(0).asText()).isEqualTo("RISK_SCORE_REVIEW_THRESHOLD");
        assertThat(result.path("semgrep").asText()).isEqualTo("NOT_RUN");
    }

    @Test
    @DisplayName("기준 ref를 찾지 못하면 자동 선택을 실패 처리한다")
    void missingAutomaticBaseFails() {
        // given
        final String[] arguments = {"local", "--project", project.toString()};

        // when & then
        assertThat(cli.run(arguments)).isEqualTo(4);
        assertThat(stdout.size()).isZero();
    }

    @Test
    @DisplayName("origin main을 기본 기준 ref로 사용한다")
    void originMainIsDefaultBase() throws IOException, InterruptedException {
        // given
        git("update-ref", "refs/remotes/origin/main", GitWorkingTree.head(project));

        // when
        final String base = GitWorkingTree.resolveBase(project, null);
        final String blankBase = GitWorkingTree.resolveBase(project, " ");

        // then
        assertThat(base).isEqualTo(GitWorkingTree.head(project));
        assertThat(blankBase).isEqualTo(base);
    }

    @Test
    @DisplayName("remote default branch를 기본 기준 ref로 사용한다")
    void remoteDefaultBranchIsFallbackBase() throws IOException, InterruptedException {
        // given
        git("remote", "add", "upstream", project.toString());
        git("update-ref", "refs/remotes/upstream/trunk", GitWorkingTree.head(project));
        git("symbolic-ref", "refs/remotes/upstream/HEAD", "refs/remotes/upstream/trunk");

        // when
        final String base = GitWorkingTree.resolveBase(project, null);

        // then
        assertThat(base).isEqualTo(GitWorkingTree.head(project));
    }

    @Test
    @DisplayName("유효하지 않은 Build Convention JSON은 오류로 종료한다")
    void invalidBuildConventionJsonFails() throws IOException {
        // given
        Files.writeString(project.resolve("build/reports/build-convention/report.json"), "{");

        // when & then
        assertThat(cli.run(args())).isEqualTo(4);
        assertThat(stdout.size()).isZero();
    }

    @Test
    @DisplayName("명시적 report 경로를 사용하고 잘못된 report 값을 거부한다")
    void explicitReportPathAndInvalidReportValues() throws IOException {
        // given
        final Path report = project.resolve("custom-report.json");
        final String validReport = Files.readString(
                Path.of("src/test/resources/build-convention/pass-report.json"));
        Files.writeString(report, validReport);
        final String[] customReportArgs = {
            "local", "--project", project.toString(), "--base", GitWorkingTree.head(project),
            "--report", "custom-report.json"
        };

        // when & then
        assertThat(cli.run(customReportArgs)).isZero();
        Files.writeString(report, validReport.replace("\"PASS\"", "\"INVALID\""));
        assertThat(cli.run(customReportArgs)).isEqualTo(4);
        Files.writeString(report, validReport.replace("\"architecture\": \"PASS\"", "\"architecture\": 1"));
        assertThat(cli.run(customReportArgs)).isEqualTo(4);
        Files.writeString(report, validReport.replace("\"checks\": {", "\"checks\": []"));
        assertThat(cli.run(customReportArgs)).isEqualTo(4);
        Files.writeString(report, validReport.replace("\"coverage\": {", "\"coverage\": []"));
        assertThat(cli.run(customReportArgs)).isEqualTo(4);
        Files.writeString(report, validReport.replace("\"enabled\": false", "\"enabled\": \"false\""));
        assertThat(cli.run(customReportArgs)).isEqualTo(4);
        Files.writeString(report, validReport.replace("\"pluginVersion\": \"1.0.1\"", "\"pluginVersion\": \" \""));
        assertThat(cli.run(customReportArgs)).isEqualTo(4);
        Files.writeString(report, validReport.replace("\"status\": \"PASS\"", "\"status\": \"INVALID\""));
        assertThat(cli.run(customReportArgs)).isEqualTo(4);
        Files.writeString(report, validReport.replace("0.75", "1.25"));
        assertThat(cli.run(customReportArgs)).isEqualTo(4);
        Files.writeString(report, validReport.replace("0.75", "-0.25"));
        assertThat(cli.run(customReportArgs)).isEqualTo(4);
        Files.writeString(report, validReport.replace("0.75", "\"invalid\""));
        assertThat(cli.run(customReportArgs)).isEqualTo(4);
        Files.writeString(report, validReport.replace("\"checks\": {", "\"checks\": {}"));
        assertThat(cli.run(customReportArgs)).isEqualTo(4);
        Files.writeString(report, "null");
        assertThat(cli.run(customReportArgs)).isEqualTo(4);
        Files.writeString(report, "{}");
        assertThat(cli.run(customReportArgs)).isEqualTo(4);
        assertThat(stdout.toString(StandardCharsets.UTF_8)).contains("\"decision\":\"PASS\"");
    }

    @Test
    @DisplayName("프로젝트 밖의 report 경로는 거부한다")
    void reportOutsideProjectFails() throws IOException {
        // given
        final String[] arguments = {
            "local", "--project", project.toString(), "--base", GitWorkingTree.head(project),
            "--report", "../report.json"
        };

        // when & then
        assertThat(cli.run(arguments)).isEqualTo(4);
        assertThat(stdout.size()).isZero();
    }

    @Test
    @DisplayName("잘못된 CLI 인자는 모두 오류로 종료한다")
    void malformedArgumentsFail() {
        // given
        final String[] noCommand = {};
        final String[] wrongCommand = {"assessment"};
        final String[] unknownOption = {"local", "--unknown", "value"};
        final String[] missingValue = {"local", "--project"};
        final String[] projectMissing = {"local", "--base", "HEAD"};

        // when & then
        assertThat(cli.run(noCommand)).isEqualTo(4);
        assertThat(cli.run(wrongCommand)).isEqualTo(4);
        assertThat(cli.run(unknownOption)).isEqualTo(4);
        assertThat(cli.run(missingValue)).isEqualTo(4);
        assertThat(cli.run(projectMissing)).isEqualTo(4);
        assertThat(stdout.size()).isZero();
    }

    @Test
    @DisplayName("diff 제한 초과는 오류로 종료한다")
    void oversizedDiffFails() throws IOException {
        // given
        write("src/Main.java", "class Main { int value() { return 2; } }\n");
        final var limitedService = new AssessRiskService(new DeterministicRiskAnalyzerAdapter(),
                new RiskPolicyService(new RiskScore(90), new RiskScore(70)), () -> 1,
                context -> { throw new AssertionError("Jev is disabled by default"); });
        cli = new LocalRiskGateCli(limitedService, new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8));

        // when & then
        assertThat(cli.run(args())).isEqualTo(4);
        assertThat(stdout.size()).isZero();
        assertThat(stderr.toString(StandardCharsets.UTF_8)).doesNotContain("class Main");
    }

    private AssessRiskUseCase service() {
        return new AssessRiskService(new DeterministicRiskAnalyzerAdapter(),
                new RiskPolicyService(new RiskScore(90), new RiskScore(70)), () -> Integer.MAX_VALUE,
                context -> {
                    throw new SecurityRiskAssessmentException(FailureKind.CONFIGURATION_ERROR);
                });
    }

    private String[] args() throws IOException {
        return new String[] {
            "local", "--project", project.toRealPath().toString(), "--base", GitWorkingTree.head(project)
        };
    }

    private void write(final String relative, final String content) throws IOException {
        final Path path = project.resolve(relative);
        final Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, content);
    }

    private String git(final String... args) throws IOException, InterruptedException {
        final var command = new java.util.ArrayList<String>();
        command.add("git");
        command.addAll(List.of(args));
        final Process process = new ProcessBuilder(command)
                .directory(project.toFile())
                .redirectErrorStream(true)
                .start();
        final String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) {
            throw new IllegalStateException(output);
        }
        return output.trim();
    }

}
