package io.github.dochiri0916.riskgate.riskassessment.adapter.in.bootstrap;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.JacksonException;
import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase;
import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase.AssessRiskCommand.FindingInput;
import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase.AssessRiskCommand;
import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase.AssessRiskResult;
import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase.AssessRiskCommand.BuildConventionReport;
import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase.AssessRiskCommand.Check;
import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase.AssessRiskCommand.Coverage;
import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase.AssessRiskCommand.Mutation;
import io.github.dochiri0916.riskgate.riskassessment.domain.exception.RiskAssessmentDomainException;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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

    public LocalRiskGateCli(final AssessRiskUseCase assessRiskUseCase) {
        this.assessRiskUseCase = assessRiskUseCase;
        this.output = new CliOutput();
    }

    LocalRiskGateCli(final AssessRiskUseCase assessRiskUseCase, final PrintStream stdout, final PrintStream stderr) {
        this.assessRiskUseCase = assessRiskUseCase;
        this.output = new CliOutput(stdout, stderr);
    }

    public int run(final String... args) {
        try {
            return execute(args);
        } catch (LocalInputException exception) {
            this.output.error("Local Risk Gate error: " + exception.getMessage());
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
        final BuildConventionReport report = readReport(reportPath);
        final GitWorkingTree.Snapshot snapshot = GitWorkingTree.snapshot(project, base);
        final String head = GitWorkingTree.head(project);
        final List<FindingInput> localFindings = new LocalChangeRiskSignalExtractor()
                .extract(snapshot.changedFiles(), snapshot.diff());
        final AssessRiskResult result = assessRiskUseCase.assess(new AssessRiskCommand(
                "1", project.toString(), head, null, report, null, localFindings,
                snapshot.changedFiles(), snapshot.diff()
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
        objectMapper.writeValue(this.output.stdout(), resultJson);
        this.output.stdout().println();
        return switch (result.decision()) {
            case PASS -> PASS;
            case REVIEW -> REVIEW;
            case BLOCK -> BLOCK;
        };
    }

    private BuildConventionReport readReport(final Path path) throws IOException {
        final JsonNode root;
        try {
            root = objectMapper.readTree(path.toFile());
        } catch (JacksonException exception) {
            throw new LocalInputException("Build Convention report is not valid JSON");
        }
        if (root == null || !root.isObject()) {
            throw new LocalInputException("Build Convention report must be a JSON object");
        }
        final String schemaVersion = requiredText(root, "schemaVersion");
        final String pluginVersion = requiredText(root, "pluginVersion");
        final String status = requiredText(root, "status");
        final JsonNode checksNode = root.path("checks");
        if (!checksNode.isObject() || checksNode.isEmpty()) {
            throw invalidReport();
        }
        final Map<String, Check> checks = new LinkedHashMap<>();
        checksNode.forEachEntry((name, value) -> {
            if (!value.isTextual() || !List.of("PASS", "FAIL", "SKIPPED", "NOT_APPLIED")
                    .contains(value.asText())) {
                throw invalidReport();
            }
            checks.put(name, new Check("BUILD_CONVENTION", name, value.asText()));
        });
        final JsonNode coverageNode = root.path("coverage");
        final JsonNode mutationNode = root.path("mutation");
        if (!coverageNode.isObject() || !mutationNode.isObject()
                || !mutationNode.path("enabled").isBoolean()) {
            throw invalidReport();
        }
        validateFraction(coverageNode.path("line"));
        validateFraction(coverageNode.path("branch"));
        validateFraction(mutationNode.path("score"));
        final Coverage coverage = new Coverage(
                decimal(coverageNode.path("line")), decimal(coverageNode.path("branch")));
        final Mutation mutation = new Mutation(
                mutationNode.path("enabled").asBoolean(), decimal(mutationNode.path("score")));
        if (!List.of("PASS", "FAIL").contains(status)) {
            throw invalidReport();
        }
        return new BuildConventionReport(
            schemaVersion, pluginVersion, status, new ArrayList<>(checks.values()), coverage, mutation);
    }

    private LocalInputException invalidReport() {
        return new LocalInputException("Build Convention report is missing required fields or has invalid values");
    }

    private BigDecimal decimal(final JsonNode node) {
        return node == null || node.isNull() ? null : node.decimalValue();
    }

    private void validateFraction(final JsonNode node) {
        if (node == null || node.isNull()) {
            return;
        }
        if (!node.isNumber() || node.decimalValue().compareTo(BigDecimal.ZERO) < 0
                || node.decimalValue().compareTo(BigDecimal.ONE) > 0) {
            throw invalidReport();
        }
    }

    private String requiredText(final JsonNode node, final String field) {
        final JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw invalidReport();
        }
        return value.asText();
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
