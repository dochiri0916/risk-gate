package io.github.dochiri0916.riskgate.riskassessment.adapter.in.bootstrap;

import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase.AssessRiskCommand.SemgrepFinding;
import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase.AssessRiskCommand.SemgrepReport;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.RiskSeverity;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

final class CiSemgrepReportReader {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private CiSemgrepReportReader() { }

    static SemgrepReport read(final java.nio.file.Path path) throws IOException {
        final JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(path.toFile());
        } catch (final JacksonException exception) {
            throw new LocalRiskGateCli.LocalInputException("Semgrep report is not valid JSON");
        }
        final JsonNode results = root == null ? null : root.get("results");
        if (results == null || !results.isArray()) {
            throw new LocalRiskGateCli.LocalInputException("Semgrep report does not contain a results array");
        }
        final JsonNode errors = root.get("errors");
        if (errors != null && !errors.isNull() && !errors.isArray()) {
            throw new LocalRiskGateCli.LocalInputException("Semgrep report contains invalid errors");
        }
        if (errors != null && errors.isArray()) {
            for (JsonNode error : errors) {
                final String level = error.path("level").asText("").toLowerCase(Locale.ROOT);
                if (!List.of("warn", "warning", "info").contains(level)) {
                    throw new LocalRiskGateCli.LocalInputException("Semgrep report contains fatal analysis errors");
                }
            }
        }
        final List<SemgrepFinding> findings = new ArrayList<>();
        for (JsonNode item : results) {
            final String rule = requiredText(item, "check_id");
            final String findingPath = requiredText(item, "path");
            final int line = item.path("start").path("line").asInt(0);
            final JsonNode extra = item.path("extra");
            final String severity = requiredText(extra, "severity");
            final String message = requiredText(extra, "message");
            if (line < 1) {
                throw new LocalRiskGateCli.LocalInputException("Semgrep report contains an invalid finding");
            }
            findings.add(new SemgrepFinding(mapSeverity(severity), rule, findingPath, line, message));
        }
        return new SemgrepReport(findings);
    }

    private static String requiredText(final JsonNode node, final String field) {
        final JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new LocalRiskGateCli.LocalInputException("Semgrep report contains an invalid finding");
        }
        return value.asText();
    }

    private static RiskSeverity mapSeverity(final String severity) {
        return switch (severity) {
            case "INFO", "LOW", "EXPERIMENT", "INVENTORY" -> RiskSeverity.LOW;
            case "WARNING", "MEDIUM" -> RiskSeverity.MEDIUM;
            case "ERROR", "HIGH" -> RiskSeverity.HIGH;
            case "CRITICAL" -> RiskSeverity.CRITICAL;
            default -> throw new LocalRiskGateCli.LocalInputException("Semgrep report contains invalid severity");
        };
    }
}
