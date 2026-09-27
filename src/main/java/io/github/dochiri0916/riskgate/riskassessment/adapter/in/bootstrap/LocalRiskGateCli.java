package io.github.dochiri0916.riskgate.riskassessment.adapter.in.bootstrap;

import tools.jackson.databind.ObjectMapper;
import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase;
import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase.AssessRiskCommand.FindingInput;
import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase.AssessRiskCommand;
import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase.AssessRiskResult;
import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase.AssessRiskCommand.BuildConventionReport;
import io.github.dochiri0916.riskgate.riskassessment.domain.exception.RiskAssessmentDomainException;
import io.github.dochiri0916.riskgate.riskassessment.application.port.out.SecurityRiskAssessmentPort.SecurityRiskAssessmentException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.io.PrintStream;
import java.util.HashMap;

public final class LocalRiskGateCli {
    private static final int PASS = 0;
    private static final int REVIEW = 2;
    private static final int BLOCK = 3;
    private static final int ERROR = 4;
    private final AssessRiskUseCase assessRiskUseCase;
    private final CliOutput output;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final boolean jevEnabled;

    public LocalRiskGateCli(final AssessRiskUseCase assessRiskUseCase) {
        this(assessRiskUseCase, Boolean.parseBoolean(System.getenv("TYPESAFE_JEV_ENABLED")));
    }

    LocalRiskGateCli(final AssessRiskUseCase assessRiskUseCase, final boolean jevEnabled) {
        this.assessRiskUseCase = assessRiskUseCase;
        this.output = new CliOutput();
        this.jevEnabled = jevEnabled;
    }

    LocalRiskGateCli(final AssessRiskUseCase assessRiskUseCase, final PrintStream stdout, final PrintStream stderr) {
        this(assessRiskUseCase, stdout, stderr, Boolean.parseBoolean(System.getenv("TYPESAFE_JEV_ENABLED")));
    }

    LocalRiskGateCli(
            final AssessRiskUseCase assessRiskUseCase,
            final PrintStream stdout,
            final PrintStream stderr,
            final boolean jevEnabled
    ) {
        this.assessRiskUseCase = assessRiskUseCase;
        this.output = new CliOutput(stdout, stderr);
        this.jevEnabled = jevEnabled;
    }

    public int run(final String... args) {
        try {
            return execute(args);
        } catch (LocalInputException exception) {
            this.output.error("Local Risk Gate error: " + exception.getMessage());
            return ERROR;
        } catch (SecurityRiskAssessmentException exception) {
            LocalJevOutput.writeFailure(objectMapper, this.output.stdout(), exception.failureKind());
            return ERROR;
        } catch (IOException | RiskAssessmentDomainException exception) {
            this.output.error("Local Risk Gate failed due to invalid input or execution error");
            return ERROR;
        } catch (java.nio.file.InvalidPathException exception) {
            this.output.error("Local Risk Gate error: invalid filesystem path");
            return ERROR;
        }
    }

    private int execute(final String... args) throws IOException {
        final Options options = Options.parse(args);
        final Path project = options.project().toRealPath();
        final String base = GitWorkingTree.resolveBase(project, options.base());
        final Path reportPath = options.report() == null
                ? project.resolve("build/reports/build-convention/report.json")
                : project.resolve(options.report()).normalize();
        if (!reportPath.startsWith(project) || !Files.isRegularFile(reportPath)) {
            throw new LocalInputException("Build Convention report is missing or outside the project root");
        }
        final BuildConventionReport report = LocalBuildConventionReportReader.read(reportPath);
        final GitWorkingTree.Snapshot snapshot = GitWorkingTree.snapshot(project, base);
        final String head = GitWorkingTree.head(project);
        final List<FindingInput> localFindings = new LocalChangeRiskSignalExtractor()
                .extract(snapshot.changedFiles(), snapshot.diff());
        final AssessRiskResult result = assessRiskUseCase.assess(new AssessRiskCommand(
                "1", project.toString(), head, null, report, null, localFindings,
                snapshot.changedFiles(), snapshot.diff(), jevEnabled
        ));
        final Map<String, Object> resultJson = new LinkedHashMap<>();
        resultJson.put("mode", "LOCAL");
        resultJson.put("decision", result.decision().name());
        resultJson.put("score", result.score());
        resultJson.put("reasonCodes", result.reasonCodes());
        resultJson.put("buildConvention", report.status());
        resultJson.put("semgrep", "NOT_RUN");
        resultJson.put("findings", result.findings().stream().map(finding -> Map.of(
                "category", finding.category().name(),
                "severity", finding.severity().name(),
                "code", finding.source(),
                "path", finding.evidence()
        )).toList());
        resultJson.put("jev", result.jevAssessment() == null ? Map.of("status", "NOT_RUN")
                : LocalJevOutput.assessment(result.jevAssessment()));
        objectMapper.writeValue(this.output.stdout(), resultJson);
        this.output.stdout().println();
        return switch (result.decision()) {
            case PASS -> PASS;
            case REVIEW -> REVIEW;
            case BLOCK -> BLOCK;
        };
    }

    private record Options(Path project, String base, Path report) {
        private static Options parse(final String... args) {
            if (args.length < 1 || !"local".equals(args[0])) {
                throw new LocalInputException(
                        "Usage: java -jar risk-gate.jar local --project <path> [--base <ref>] [--report <path>]");
            }
            final Map<String, String> values = new HashMap<>();
            final Iterator<String> arguments = List.of(args).iterator();
            arguments.next();
            while (arguments.hasNext()) {
                final String option = arguments.next();
                if (!arguments.hasNext()) {
                    throw new LocalInputException("Missing value for " + option);
                }
                final String value = arguments.next();
                switch (option) {
                    case "--project", "--base", "--report" -> values.put(option, value);
                    default -> throw new LocalInputException("Unknown option: " + option);
                }
            }
            if (!values.containsKey("--project")) {
                throw new LocalInputException("--project is required");
            }
            final Path report = values.containsKey("--report") ? Path.of(values.get("--report")) : null;
            return new Options(Path.of(values.get("--project")), values.get("--base"), report);
        }
    }

    static final class LocalInputException extends RuntimeException {
        LocalInputException(final String message) { super(message); }
    }
}
