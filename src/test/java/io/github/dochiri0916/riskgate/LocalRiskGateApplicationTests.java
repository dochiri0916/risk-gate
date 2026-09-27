package io.github.dochiri0916.riskgate;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalRiskGateApplicationTests {
    @TempDir
    Path project;

    @Test
    @DisplayName("local 명령은 서버 없이 CLI를 실행한다")
    void startsLocalRiskGateWithoutHttpServer() throws IOException, InterruptedException {
        // given
        initializeProject();
        final String[] args = {
            "local", "--project", project.toString(), "--base", git("rev-parse", "HEAD")
        };
        final AtomicInteger exitCode = new AtomicInteger(-1);

        // when & then
        LocalRiskGateApplication.launch(args, exitCode::set);
        assertThat(exitCode).hasValue(0);
    }

    @Test
    @DisplayName("일반 명령은 HTTP 서버 시작 경로로 전달한다")
    void dispatchesNonLocalCommandToServer() {
        // given
        final String[] args = {"--server.port=8081"};
        final List<String> serverArgs = new ArrayList<>();

        // when
        final int exitCode = LocalRiskGateApplication.dispatch(args, ignored -> 3, ignored -> 4,
                forwarded -> serverArgs.addAll(List.of(forwarded)));

        // then
        assertThat(exitCode).isZero();
        assertThat(serverArgs).containsExactly(args);
    }

    @Test
    @DisplayName("local 명령 판별은 비어 있는 인자도 처리한다")
    void identifiesLocalCommand() {
        // given
        final String[] localArgs = {"local"};
        final String[] noArgs = {};
        final String[] otherArgs = {"--help"};

        // when & then
        assertThat(LocalRiskGateApplication.isLocalCommand(localArgs)).isTrue();
        assertThat(LocalRiskGateApplication.isLocalCommand(noArgs)).isFalse();
        assertThat(LocalRiskGateApplication.isLocalCommand(otherArgs)).isFalse();
    }

    @Test
    @DisplayName("ci 명령은 one-shot CLI 경로로 분기한다")
    void identifiesCiCommand() {
        // given
        final String[] ciArgs = {"ci"};

        // when & then
        assertThat(LocalRiskGateApplication.isCiCommand(ciArgs)).isTrue();
        assertThat(LocalRiskGateApplication.isCiCommand("local")).isFalse();
    }

    private void initializeProject() throws IOException, InterruptedException {
        git("init", "-q");
        git("config", "user.email", "test@example.invalid");
        git("config", "user.name", "Risk Gate Test");
        final Path report = project.resolve("build/reports/build-convention/report.json");
        final Path reportParent = report.getParent();
        if (reportParent == null) {
            throw new IOException("Report path has no parent directory");
        }
        Files.createDirectories(reportParent);
        Files.copy(Path.of("src/test/resources/build-convention/pass-report.json"), report);
        git("add", "build/reports/build-convention/report.json");
        git("commit", "-qm", "base");
    }

    private String git(final String... args) throws IOException, InterruptedException {
        final List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        final Process process = new ProcessBuilder(command).directory(project.toFile())
                .redirectErrorStream(true).start();
        final String output = new String(process.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        if (process.waitFor() != 0) {
            throw new IllegalStateException(output);
        }
        return output.trim();
    }
}
