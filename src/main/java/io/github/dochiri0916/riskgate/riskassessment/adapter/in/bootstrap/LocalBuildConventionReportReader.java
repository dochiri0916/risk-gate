package io.github.dochiri0916.riskgate.riskassessment.adapter.in.bootstrap;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.JacksonException;
import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase.AssessRiskCommand.BuildConventionReport;
import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase.AssessRiskCommand.Check;
import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase.AssessRiskCommand.Coverage;
import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase.AssessRiskCommand.Mutation;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class LocalBuildConventionReportReader {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private LocalBuildConventionReportReader() { }

    static BuildConventionReport read(final Path path) throws java.io.IOException {
        final JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(path.toFile());
        } catch (final JacksonException exception) {
            throw new LocalRiskGateCli.LocalInputException("Build Convention report is not valid JSON");
        }
        if (root == null || !root.isObject()) {
            throw new LocalRiskGateCli.LocalInputException("Build Convention report must be a JSON object");
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
        final Coverage coverage = new Coverage(decimal(coverageNode.path("line")),
                decimal(coverageNode.path("branch")));
        final Mutation mutation = new Mutation(mutationNode.path("enabled").asBoolean(),
                decimal(mutationNode.path("score")));
        if (!List.of("PASS", "FAIL").contains(status)) {
            throw invalidReport();
        }
        return new BuildConventionReport(schemaVersion, pluginVersion, status, new ArrayList<>(checks.values()),
                coverage, mutation);
    }

    private static LocalRiskGateCli.LocalInputException invalidReport() {
        return new LocalRiskGateCli.LocalInputException(
                "Build Convention report is missing required fields or has invalid values");
    }

    private static BigDecimal decimal(final JsonNode node) {
        return node == null || node.isNull() ? null : node.decimalValue();
    }

    private static void validateFraction(final JsonNode node) {
        if (node == null || node.isNull()) {
            return;
        }
        if (!node.isNumber() || node.decimalValue().compareTo(BigDecimal.ZERO) < 0
                || node.decimalValue().compareTo(BigDecimal.ONE) > 0) {
            throw invalidReport();
        }
    }

    private static String requiredText(final JsonNode node, final String field) {
        final JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw invalidReport();
        }
        return value.asText();
    }
}
