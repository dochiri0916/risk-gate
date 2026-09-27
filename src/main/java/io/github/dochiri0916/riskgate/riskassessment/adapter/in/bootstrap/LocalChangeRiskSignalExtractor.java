package io.github.dochiri0916.riskgate.riskassessment.adapter.in.bootstrap;

import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase.AssessRiskCommand.ChangedFile;
import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase.AssessRiskCommand.FindingInput;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.RiskCategory;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.RiskSeverity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

final class LocalChangeRiskSignalExtractor {
    private static final Pattern PRIVATE_KEY = Pattern.compile(
            "-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----");
    private static final Pattern AWS_SECRET = Pattern.compile(
            "(?i)(?:aws_secret_access_key|secret_access_key)\\s*[:=]\\s*['\\\"]?[A-Za-z0-9/+=]{32,}");
    private static final Pattern SECRET_LITERAL = Pattern.compile(
            "(?i)\\b(?:password|passwd|token|secret|api[_-]?(?:key|token)|client[_-]?secret)"
                    + "\\b\\s*[:=]\\s*(?:['\\\"][^'\\\"]{4,}['\\\"]"
                    + "|[A-Za-z0-9_./+=-]{8,}(?=\\s*(?:[,;#]|$)))");
    private static final Pattern DESTRUCTIVE_SQL = Pattern.compile(
            "(?i)\\b(?:DROP\\s+(?:DATABASE|TABLE)|TRUNCATE\\s+TABLE)\\b");

    List<FindingInput> extract(final List<ChangedFile> changedFiles, final String diff) {
        final Map<String, List<String>> addedLinesByPath = addedLinesByPath(diff);
        final List<FindingInput> findings = new ArrayList<>();
        for (ChangedFile changedFile : changedFiles) {
            final String path = changedFile.path();
            final List<String> addedLines = addedLinesByPath.getOrDefault(path, List.of());
            final boolean secretAdded = addedLines.stream().anyMatch(LocalChangeRiskSignalExtractor::hasSecretMaterial);
            final boolean destructiveMigration = isMigration(path) && addedLines.stream()
                    .anyMatch(LocalChangeRiskSignalExtractor::hasDestructiveSqlOperation);
            if (secretAdded) {
                findings.add(finding(RiskCategory.SECURITY, RiskSeverity.CRITICAL, "SECRET_MATERIAL_ADDED", path));
            } else if (destructiveMigration) {
                findings.add(finding(
                        RiskCategory.DATA_INTEGRITY, RiskSeverity.CRITICAL, "DESTRUCTIVE_MIGRATION", path));
            } else {
                final FindingRule rule = highRiskRule(path);
                if (rule != null) {
                    findings.add(finding(rule.category(), RiskSeverity.HIGH, rule.code(), path));
                }
            }
        }
        return List.copyOf(findings);
    }

    private static FindingInput finding(
            final RiskCategory category,
            final RiskSeverity severity,
            final String code,
            final String path
    ) {
        return new FindingInput(category, severity, code, code, path);
    }

    private static boolean hasSecretMaterial(final String line) {
        return PRIVATE_KEY.matcher(line).find() || AWS_SECRET.matcher(line).find()
                || SECRET_LITERAL.matcher(line).find();
    }

    private static boolean hasDestructiveSqlOperation(final String line) {
        final int commentStart = line.indexOf("--");
        final String sql = commentStart < 0 ? line : line.substring(0, commentStart);
        return DESTRUCTIVE_SQL.matcher(sql).find();
    }

    private static FindingRule highRiskRule(final String path) {
        final String normalized = path.toLowerCase(Locale.ROOT);
        if (isProductionSecurityCode(normalized)) {
            return new FindingRule(RiskCategory.SECURITY, "SECURITY_SENSITIVE_CHANGE");
        }
        if (normalized.startsWith(".github/workflows/")) {
            return new FindingRule(RiskCategory.OPERABILITY, "CI_CD_CHANGE");
        }
        if (isDeploymentConfiguration(normalized)) {
            return new FindingRule(RiskCategory.OPERABILITY, "DEPLOYMENT_CONFIGURATION_CHANGE");
        }
        if (isContainerConfiguration(normalized)) {
            return new FindingRule(RiskCategory.OPERABILITY, "CONTAINER_BUILD_CONFIGURATION_CHANGE");
        }
        if (isGradleConfiguration(normalized)) {
            return new FindingRule(RiskCategory.OPERABILITY, "BUILD_CONFIGURATION_CHANGE");
        }
        if (isMigration(path)) {
            return new FindingRule(RiskCategory.DATA_INTEGRITY, "DATABASE_MIGRATION_CHANGE");
        }
        return null;
    }

    private static boolean isProductionSecurityCode(final String path) {
        if (path.contains("/test/") || path.startsWith("test/") || path.contains("/tests/")) {
            return false;
        }
        return path.contains("/security/") || path.startsWith("security/")
                || path.contains("/authentication/") || path.contains("/authorization/")
                || path.contains("/auth/") || path.startsWith("auth/")
                || path.contains("securityconfig") || path.contains("security-config");
    }

    private static boolean isDeploymentConfiguration(final String path) {
        return path.startsWith("deploy/") || path.contains("/deploy/")
                || path.startsWith("deployment/") || path.contains("/deployment/")
                || path.startsWith("k8s/") || path.contains("/k8s/")
                || path.startsWith("kubernetes/") || path.contains("/kubernetes/")
                || path.startsWith("helm/") || path.contains("/helm/")
                || path.startsWith("terraform/") || path.contains("/terraform/");
    }

    private static boolean isContainerConfiguration(final String path) {
        final String name = path.substring(path.lastIndexOf('/') + 1);
        return name.startsWith("dockerfile") || "docker-compose.yml".equals(name)
                || "docker-compose.yaml".equals(name) || ".dockerignore".equals(name);
    }

    private static boolean isGradleConfiguration(final String path) {
        return "build.gradle".equals(path) || "build.gradle.kts".equals(path)
                || "settings.gradle".equals(path) || "settings.gradle.kts".equals(path)
                || path.startsWith("gradle/") && (path.endsWith(".gradle") || path.endsWith(".gradle.kts")
                        || path.endsWith(".toml"));
    }

    private static boolean isMigration(final String path) {
        final String normalized = path.toLowerCase(Locale.ROOT);
        return normalized.contains("/migration/") || normalized.startsWith("migration/")
                || normalized.contains("/migrations/") || normalized.startsWith("migrations/")
                || normalized.contains("/db/changelog/") || normalized.startsWith("db/changelog/")
                || normalized.contains("/flyway/") || normalized.startsWith("flyway/")
                || normalized.contains("/liquibase/") || normalized.startsWith("liquibase/");
    }

    private static Map<String, List<String>> addedLinesByPath(final String diff) {
        final Map<String, List<String>> result = new LinkedHashMap<>();
        String currentPath = null;
        for (String line : diff.split("\\R", -1)) {
            if (line.startsWith("+++ ")) {
                final String file = line.substring(4);
                currentPath = "/dev/null".equals(file) ? null : stripGitPrefix(file);
                if (currentPath != null) {
                    result.computeIfAbsent(currentPath, ignored -> new ArrayList<>());
                }
            } else if (currentPath != null && line.startsWith("+") && !line.startsWith("+++")) {
                result.get(currentPath).add(line.substring(1));
            }
        }
        return result;
    }

    private static String stripGitPrefix(final String path) {
        if (path.startsWith("b/")) {
            return path.substring(2);
        }
        return path;
    }

    private record FindingRule(RiskCategory category, String code) { }
}
