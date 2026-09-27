package io.github.dochiri0916.riskgate.riskassessment.adapter.in.bootstrap;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

record CiRiskGateOptions(Path project, String base, Path report, Path semgrepReport, String repository,
                         String headSha, int pullRequestNumber, int buildExit, int semgrepExit) {
    private static final List<String> OPTIONS = List.of("--project", "--base", "--report", "--semgrep-report",
            "--repository", "--head-sha", "--pr-number", "--build-exit", "--semgrep-exit");

    static CiRiskGateOptions parse(final String... args) {
        if (args.length < 1 || !"ci".equals(args[0])) {
            throw new LocalRiskGateCli.LocalInputException("Usage: java -jar risk-gate.jar ci --project <path> ...");
        }
        final Map<String, String> values = new HashMap<>();
        final Iterator<String> arguments = List.of(args).iterator();
        arguments.next();
        while (arguments.hasNext()) {
            final String option = arguments.next();
            if (!arguments.hasNext()) {
                throw new LocalRiskGateCli.LocalInputException("Missing value for " + option);
            }
            final String value = arguments.next();
            if (!OPTIONS.contains(option)) {
                throw new LocalRiskGateCli.LocalInputException("Unknown option: " + option);
            }
            values.put(option, value);
        }
        try {
            return new CiRiskGateOptions(Path.of(required(values, "--project")), required(values, "--base"),
                    Path.of(required(values, "--report")), Path.of(required(values, "--semgrep-report")),
                    required(values, "--repository"), required(values, "--head-sha"),
                    Integer.parseInt(required(values, "--pr-number")),
                    Integer.parseInt(required(values, "--build-exit")),
                    Integer.parseInt(required(values, "--semgrep-exit")));
        } catch (NumberFormatException exception) {
            throw new LocalRiskGateCli.LocalInputException("CI numeric input is invalid");
        }
    }

    private static String required(final Map<String, String> values, final String key) {
        final String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new LocalRiskGateCli.LocalInputException(key + " is required");
        }
        return value;
    }
}
