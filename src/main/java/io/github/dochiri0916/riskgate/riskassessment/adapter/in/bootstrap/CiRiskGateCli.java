package io.github.dochiri0916.riskgate.riskassessment.adapter.in.bootstrap;

import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase;
import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase.AssessRiskCommand;
import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase.AssessRiskCommand.SemgrepReport;
import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase.AssessRiskResult;
import io.github.dochiri0916.riskgate.riskassessment.application.port.out.SecurityRiskAssessmentPort.SecurityRiskAssessmentException;
import io.github.dochiri0916.riskgate.riskassessment.domain.exception.RiskAssessmentDomainException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;

public final class CiRiskGateCli {
    private static final int PASS = 0;
    private static final int REVIEW = 2;
    private static final int BLOCK = 3;
    private static final int ERROR = 4;
    private final AssessRiskUseCase assessRiskUseCase;
    private final PrintStream stdout;
    private final PrintStream stderr;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CiRiskGateCli(final AssessRiskUseCase assessRiskUseCase) {
        this(assessRiskUseCase, System.out, System.err);
    }

    CiRiskGateCli(final AssessRiskUseCase assessRiskUseCase, final PrintStream stdout, final PrintStream stderr) {
        this.assessRiskUseCase = assessRiskUseCase;
        this.stdout = stdout;
        this.stderr = stderr;
    }

    public int run(final String... args) {
        try {
            return execute(args);
        } catch (IOException | LocalRiskGateCli.LocalInputException | RiskAssessmentDomainException
                | SecurityRiskAssessmentException | IllegalArgumentException exception) {
            this.stderr.println("CI Risk Gate failed due to invalid input or execution error: "
                    + exception.getClass().getSimpleName());
            return ERROR;
        }
    }

    private int execute(final String... args) throws IOException {
        final CiRiskGateOptions options = CiRiskGateOptions.parse(args);
        final Path project = options.project().toRealPath();
        final Path reportPath = insideProject(project, options.report());
        final Path semgrepPath = insideProject(project, options.semgrepReport());
        if (!Files.isRegularFile(reportPath) || !Files.isRegularFile(semgrepPath)) {
            throw new LocalRiskGateCli.LocalInputException("CI report file is missing");
        }
        if (options.semgrepExit() != 0) {
            throw new LocalRiskGateCli.LocalInputException("Semgrep execution failed");
        }
        final var buildReport = LocalBuildConventionReportReader.read(reportPath);
        if (options.buildExit() == 0 != "PASS".equals(buildReport.status())) {
            throw new LocalRiskGateCli.LocalInputException(
                    "Build Convention exit code and report status are inconsistent"
            );
        }
        final SemgrepReport semgrepReport = CiSemgrepReportReader.read(semgrepPath);
        final GitWorkingTree.Snapshot snapshot = GitWorkingTree.snapshot(project,
                GitWorkingTree.resolveBase(project, options.base()));
        final AssessRiskResult result = assessRiskUseCase.assess(new AssessRiskCommand(
                "1", options.repository(), options.headSha(), options.pullRequestNumber(), buildReport, semgrepReport,
                List.of(), snapshot.changedFiles(), snapshot.diff(), false
        ));
        final Map<String, Object> resultJson = new LinkedHashMap<>();
        resultJson.put("mode", "CI");
        resultJson.put("decision", result.decision().name());
        resultJson.put("score", result.score());
        resultJson.put("reasonCodes", result.reasonCodes());
        resultJson.put("policyVersion", result.policyVersion());
        objectMapper.writeValue(this.stdout, resultJson);
        this.stdout.println();
        return switch (result.decision()) {
            case PASS -> PASS;
            case REVIEW -> REVIEW;
            case BLOCK -> BLOCK;
        };
    }

    private static Path insideProject(final Path project, final Path path) throws IOException {
        final Path resolved = (path.isAbsolute() ? path : project.resolve(path)).toRealPath();
        if (!resolved.startsWith(project)) {
            throw new LocalRiskGateCli.LocalInputException("Report path is outside the project root");
        }
        return resolved;
    }

}
