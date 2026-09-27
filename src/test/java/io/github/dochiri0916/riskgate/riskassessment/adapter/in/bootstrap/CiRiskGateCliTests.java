package io.github.dochiri0916.riskgate.riskassessment.adapter.in.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase;
import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase.AssessRiskCommand;
import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase.AssessRiskResult;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.RiskDecision;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CiRiskGateCliTests {
    @TempDir
    Path project;

    private Path buildReport;
    private Path semgrepReport;
    private String base;

    @BeforeEach
    void initializeProject() throws IOException, InterruptedException {
        git("init", "-q");
        git("config", "user.email", "test@example.invalid");
        git("config", "user.name", "Risk Gate Test");
        buildReport = project.resolve("build/reports/build-convention/report.json");
        semgrepReport = project.resolve("build/reports/semgrep/report.json");
        Files.createDirectories(project.resolve("build/reports/build-convention"));
        Files.createDirectories(project.resolve("build/reports/semgrep"));
        Files.copy(Path.of("src/test/resources/build-convention/pass-report.json"), buildReport);
        Files.writeString(semgrepReport, "{\"results\": []}", StandardCharsets.UTF_8);
        git("add", ".");
        git("commit", "-qm", "base");
        base = git("rev-parse", "HEAD");
    }

    @Test
    @DisplayName("one-shot CI는 PASS REVIEW BLOCK exit code와 JSON을 반환한다")
    void writesDecisionAndReturnsProtocolExitCode() {
        // given
        for (final RiskDecision decision : RiskDecision.values()) {
            final AssessRiskUseCase useCase = command -> result(command, decision);
            final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            final CiRiskGateCli cli = new CiRiskGateCli(useCase,
                    new PrintStream(stdout, true, StandardCharsets.UTF_8),
                    new PrintStream(stderr, true, StandardCharsets.UTF_8));

            // when
            final String[] command = args("0", "0");
            command[6] = "build/reports/build-convention/report.json";
            command[8] = "build/reports/semgrep/report.json";
            final int exitCode = cli.run(command);

            // then
            assertThat(exitCode).as(stderr.toString(StandardCharsets.UTF_8)).isEqualTo(switch (decision) {
                case PASS -> 0;
                case REVIEW -> 2;
                case BLOCK -> 3;
            });
            assertThat(stdout.toString(StandardCharsets.UTF_8)).contains("\"decision\":\"" + decision + "\"")
                    .contains("\"reasonCodes\":[\"TEST_REASON\"]").contains("\"score\":0");
        }
    }

    @Test
    @DisplayName("Build Convention FAIL와 Semgrep CRITICAL 입력을 use case로 전달한다")
    void preservesDeterministicCiSignals() throws IOException {
        // given
        final String report = Files.readString(buildReport, StandardCharsets.UTF_8)
                .replace("\"status\": \"PASS\"", "\"status\": \"FAIL\"")
                .replace("\"architecture\": \"PASS\"", "\"architecture\": \"FAIL\"");
        Files.writeString(buildReport, report, StandardCharsets.UTF_8);
        Files.writeString(semgrepReport, """
                {"results":[{"check_id":"security.rule","path":"src/App.java","start":{"line":12},
                "extra":{"severity":"CRITICAL","message":"sensitive finding"}}]}
                """, StandardCharsets.UTF_8);
        final AtomicReference<AssessRiskCommand> received = new AtomicReference<>();
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final CiRiskGateCli cli = new CiRiskGateCli(command -> {
            received.set(command);
            return result(command, RiskDecision.BLOCK);
        }, new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8));

        // when
        final int exitCode = cli.run(args("1", "0"));

        // then
        assertThat(exitCode).as(stderr.toString(StandardCharsets.UTF_8)).isEqualTo(3);
        assertThat(received.get().buildConventionReport().status()).isEqualTo("FAIL");
        assertThat(received.get().semgrepReport().results()).singleElement()
                .satisfies(finding -> assertThat(finding.severity().name()).isEqualTo("CRITICAL"));
        assertThat(received.get().repository()).isEqualTo("owner/repository");
        assertThat(received.get().commitSha()).isEqualTo("abcdef0123456789");
        assertThat(received.get().pullRequestNumber()).isEqualTo(7);
    }

    @Test
    @DisplayName("잘못된 실행 입력은 ERROR exit code를 반환한다")
    void invalidInputReturnsExecutionError() {
        // given
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final CiRiskGateCli cli = new CiRiskGateCli(command -> result(command, RiskDecision.PASS),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8));

        // when
        final int exitCode = cli.run("ci", "--unknown", "value");

        // then
        assertThat(exitCode).isEqualTo(4);
        assertThat(stderr.toString(StandardCharsets.UTF_8)).contains("CI Risk Gate failed");
    }

    @Test
    @DisplayName("Semgrep severity와 non-fatal warning을 변환한다")
    void mapsSemgrepSeveritiesAndWarnings() throws IOException {
        // given
        Files.writeString(semgrepReport, """
                {"results":[
                {"check_id":"r1","path":"A.java","start":{"line":1},"extra":{"severity":"INFO","message":"i"}},
                {"check_id":"r2","path":"B.java","start":{"line":2},"extra":{"severity":"WARNING","message":"w"}},
                {"check_id":"r3","path":"C.java","start":{"line":3},"extra":{"severity":"ERROR","message":"e"}},
                {"check_id":"r4","path":"D.java","start":{"line":4},"extra":{"severity":"LOW","message":"l"}}],
                "errors":[{"level":"warning"},{"level":"info"}]}
                """, StandardCharsets.UTF_8);
        final AtomicReference<AssessRiskCommand> received = new AtomicReference<>();
        final CiRiskGateCli cli = new CiRiskGateCli(command -> {
            received.set(command);
            return result(command, RiskDecision.PASS);
        }, new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));

        // when
        final int exitCode = cli.run(args("0", "0"));

        // then
        assertThat(exitCode).isZero();
        assertThat(received.get().semgrepReport().results()).extracting(finding -> finding.severity().name())
                .containsExactly("LOW", "MEDIUM", "HIGH", "LOW");
    }

    @Test
    @DisplayName("잘못된 Semgrep 결과와 모든 CI option parse 오류는 ERROR다")
    void invalidSemgrepAndOptionsReturnExecutionError() throws IOException {
        // given
        final CiRiskGateCli cli = new CiRiskGateCli(command -> result(command, RiskDecision.PASS),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
        final String[] invalidNumber = args("0", "0");
        invalidNumber[14] = "not-a-number";

        // when & then
        assertThat(cli.run()).isEqualTo(4);
        assertThat(cli.run("ci", "--project")).isEqualTo(4);
        assertThat(cli.run("ci", "--unknown", "value")).isEqualTo(4);
        assertThat(cli.run("ci")).isEqualTo(4);
        assertThat(cli.run("local")).isEqualTo(4);
        assertThat(cli.run("ci", "--project", "")).isEqualTo(4);
        assertThat(cli.run(invalidNumber)).isEqualTo(4);
        final String[] outsideProject = args("0", "0");
        outsideProject[6] = Path.of("settings.gradle").toAbsolutePath().toString();
        assertThat(cli.run(outsideProject)).isEqualTo(4);
        final String[] directoryReport = args("0", "0");
        directoryReport[8] = project.toString();
        assertThat(cli.run(directoryReport)).isEqualTo(4);

        Files.writeString(semgrepReport, "{\"results\":[],\"errors\":[{\"level\":\"error\"}]}",
                StandardCharsets.UTF_8);
        assertThat(cli.run(args("0", "0"))).isEqualTo(4);
        Files.writeString(semgrepReport, "{\"results\":[]}", StandardCharsets.UTF_8);
        assertThat(cli.run(args("0", "1"))).isEqualTo(4);
        assertThat(cli.run(args("1", "0"))).isEqualTo(4);
        Files.writeString(semgrepReport, "{\"results\":[],\"errors\":\"invalid\"}", StandardCharsets.UTF_8);
        assertThat(cli.run(args("0", "0"))).isEqualTo(4);
        Files.writeString(semgrepReport, "{\"results\":[{}]}", StandardCharsets.UTF_8);
        assertThat(cli.run(args("0", "0"))).isEqualTo(4);
        Files.writeString(semgrepReport, "null", StandardCharsets.UTF_8);
        assertThat(cli.run(args("0", "0"))).isEqualTo(4);
        Files.writeString(semgrepReport, "{\"results\":[{\"check_id\":3}]}", StandardCharsets.UTF_8);
        assertThat(cli.run(args("0", "0"))).isEqualTo(4);
        Files.writeString(semgrepReport, """
                {"results":[{"check_id":"r","path":"A.java","start":{"line":0},
                "extra":{"severity":"INFO","message":"message"}}]}
                """, StandardCharsets.UTF_8);
        assertThat(cli.run(args("0", "0"))).isEqualTo(4);
        Files.writeString(semgrepReport, """
                {"results":[{"check_id":"r","path":"A.java","start":{"line":1},
                "extra":{"severity":"UNKNOWN","message":"message"}}]}
                """, StandardCharsets.UTF_8);
        assertThat(cli.run(args("0", "0"))).isEqualTo(4);
    }

    private String[] args(final String buildExit, final String semgrepExit) {
        return new String[] {"ci", "--project", project.toString(), "--base", base, "--report", buildReport.toString(),
            "--semgrep-report", semgrepReport.toString(), "--repository", "owner/repository", "--head-sha",
            "abcdef0123456789", "--pr-number", "7", "--build-exit", buildExit, "--semgrep-exit", semgrepExit};
    }

    private static AssessRiskResult result(final AssessRiskCommand command, final RiskDecision decision) {
        return new AssessRiskResult(UUID.randomUUID(), command.repository(), command.commitSha(), decision, 0,
                List.of("TEST_REASON"), List.of(), "1");
    }

    private String git(final String... args) throws IOException, InterruptedException {
        final List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        final Process process = new ProcessBuilder(command).directory(project.toFile())
                .redirectErrorStream(true).start();
        final String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) {
            throw new IllegalStateException(output);
        }
        return output.trim();
    }
}
