package io.github.dochiri0916.riskgate.riskassessment.adapter.in.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase.AssessRiskCommand.ChangedFile;
import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase.AssessRiskCommand.FindingInput;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.RiskSeverity;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LocalChangeRiskSignalExtractorTests {
    private final LocalChangeRiskSignalExtractor extractor = new LocalChangeRiskSignalExtractor();

    @Test
    @DisplayName("민감 영역과 빌드 및 배포 파일에 HIGH finding을 생성한다")
    void highRiskPathCategories() {
        // given
        final List<String> paths = List.of(
                "security/Filter.java",
                "src/main/java/auth/Login.java",
                "src/main/java/authentication/Session.java",
                "src/main/java/authorization/Access.java",
                "src/main/java/securityconfig/Filter.java",
                "src/main/java/security-config/Filter.java",
                "src/test/java/auth/LoginTest.java",
                ".github/workflows/ci.yml",
                "deploy/app.yaml",
                "src/infra/deploy/app.yaml",
                "deployment/app.yaml",
                "src/infra/deployment/app.yaml",
                "k8s/app.yaml",
                "src/infra/k8s/app.yaml",
                "kubernetes/app.yaml",
                "src/infra/kubernetes/app.yaml",
                "helm/app.yaml",
                "src/infra/helm/app.yaml",
                "terraform/main.tf",
                "src/infra/terraform/main.tf",
                "Dockerfile",
                "docker-compose.yaml",
                ".dockerignore",
                "docker-compose.yml",
                "build.gradle.kts",
                "build.gradle",
                "settings.gradle",
                "settings.gradle.kts",
                "gradle/plugin.gradle",
                "gradle/conventions.gradle.kts",
                "gradle/libs.versions.toml",
                "src/main/resources/db/migration/V3__update.sql",
                "src/main/resources/db/migrations/V3__update.sql",
                "migration/V3__update.sql",
                "migrations/V3__update.sql",
                "src/main/resources/db/changelog/change.xml",
                "db/changelog/change.xml",
                "src/main/resources/liquibase/change.xml",
                "liquibase/change.xml",
                "src/main/resources/flyway/V4.sql",
                "flyway/V4.sql",
                "src/main/java/OrderService.java"
        );

        // when
        final List<FindingInput> findings = extractor.extract(
                paths.stream().map(path -> new ChangedFile(path, "MODIFIED")).toList(),
                diff(paths, "change = true;"));

        // then
        assertThat(findings).extracting(FindingInput::source).containsExactly(
                "SECURITY_SENSITIVE_CHANGE",
                "SECURITY_SENSITIVE_CHANGE",
                "SECURITY_SENSITIVE_CHANGE",
                "SECURITY_SENSITIVE_CHANGE",
                "SECURITY_SENSITIVE_CHANGE",
                "SECURITY_SENSITIVE_CHANGE",
                "CI_CD_CHANGE",
                "DEPLOYMENT_CONFIGURATION_CHANGE",
                "DEPLOYMENT_CONFIGURATION_CHANGE",
                "DEPLOYMENT_CONFIGURATION_CHANGE",
                "DEPLOYMENT_CONFIGURATION_CHANGE",
                "DEPLOYMENT_CONFIGURATION_CHANGE",
                "DEPLOYMENT_CONFIGURATION_CHANGE",
                "DEPLOYMENT_CONFIGURATION_CHANGE",
                "DEPLOYMENT_CONFIGURATION_CHANGE",
                "DEPLOYMENT_CONFIGURATION_CHANGE",
                "DEPLOYMENT_CONFIGURATION_CHANGE",
                "DEPLOYMENT_CONFIGURATION_CHANGE",
                "DEPLOYMENT_CONFIGURATION_CHANGE",
                "CONTAINER_BUILD_CONFIGURATION_CHANGE",
                "CONTAINER_BUILD_CONFIGURATION_CHANGE",
                "CONTAINER_BUILD_CONFIGURATION_CHANGE",
                "CONTAINER_BUILD_CONFIGURATION_CHANGE",
                "BUILD_CONFIGURATION_CHANGE",
                "BUILD_CONFIGURATION_CHANGE",
                "BUILD_CONFIGURATION_CHANGE",
                "BUILD_CONFIGURATION_CHANGE",
                "BUILD_CONFIGURATION_CHANGE",
                "BUILD_CONFIGURATION_CHANGE",
                "BUILD_CONFIGURATION_CHANGE",
                "DATABASE_MIGRATION_CHANGE",
                "DATABASE_MIGRATION_CHANGE",
                "DATABASE_MIGRATION_CHANGE",
                "DATABASE_MIGRATION_CHANGE",
                "DATABASE_MIGRATION_CHANGE",
                "DATABASE_MIGRATION_CHANGE",
                "DATABASE_MIGRATION_CHANGE",
                "DATABASE_MIGRATION_CHANGE",
                "DATABASE_MIGRATION_CHANGE",
                "DATABASE_MIGRATION_CHANGE");
        assertThat(findings).allSatisfy(finding -> assertThat(finding.severity()).isEqualTo(RiskSeverity.HIGH));
    }

    @Test
    @DisplayName("added diff line의 secret만 감지하고 finding에 값은 넣지 않는다")
    void detectsOnlyAddedSecretMaterialAndRedactsIt() {
        // given
        final String secret = "FAKEKEYFAKEKEYFAKEKEYFAKEKEYFAKEKEYFAKEKEY";
        final List<ChangedFile> files = List.of(
                new ChangedFile("src/Config.java", "MODIFIED"),
                new ChangedFile("src/Key.java", "ADDED"));
        final String patch = """
                diff --git a/src/Config.java b/src/Config.java
                --- a/src/Config.java
                +++ b/src/Config.java
                -String password = "removed fake password";
                +String value = "ordinary";
                diff --git a/src/Key.java b/src/Key.java
                --- /dev/null
                +++ b/src/Key.java
                +String aws_secret_access_key = "{}";
                """.replace("{}", secret);

        // when
        final List<FindingInput> findings = extractor.extract(files, patch);

        // then
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).source()).isEqualTo("SECRET_MATERIAL_ADDED");
        assertThat(findings.get(0).severity()).isEqualTo(RiskSeverity.CRITICAL);
        assertThat(findings.get(0).evidence()).isEqualTo("src/Key.java").doesNotContain(secret);
    }

    @Test
    @DisplayName("실제 값이 추가된 credential과 파괴적 migration만 CRITICAL finding으로 만든다")
    void detectsUnquotedCredentialAndDestructiveMigration() {
        // given
        final String credentialKey = "api_" + "token";
        final String fixtureValue = "test_credential_value_123456";
        final List<ChangedFile> files = List.of(
                new ChangedFile("config/application.yml", "ADDED"),
                new ChangedFile("migration/V4.sql", "ADDED"),
                new ChangedFile("migration/V5.sql", "ADDED"));
        final String patch = """
                diff --git a/config/application.yml b/config/application.yml
                --- /dev/null
                +++ b/config/application.yml
                +{credential-key}: {secret-value}
                diff --git a/migration/V4.sql b/migration/V4.sql
                --- /dev/null
                +++ b/migration/V4.sql
                +TRUNCATE TABLE sessions;
                diff --git a/migration/V5.sql b/migration/V5.sql
                --- /dev/null
                +++ b/migration/V5.sql
                +-- DROP TABLE sessions;
                """.replace("{credential-key}", credentialKey).replace("{secret-value}", fixtureValue);

        // when
        final List<FindingInput> findings = extractor.extract(files, patch);

        // then
        assertThat(findings).extracting(FindingInput::source)
                .containsExactly("SECRET_MATERIAL_ADDED", "DESTRUCTIVE_MIGRATION", "DATABASE_MIGRATION_CHANGE");
        assertThat(findings.subList(0, 2)).extracting(FindingInput::severity)
                .containsOnly(RiskSeverity.CRITICAL);
        assertThat(findings.get(2).severity()).isEqualTo(RiskSeverity.HIGH);
        assertThat(findings.toString()).doesNotContain(fixtureValue);
    }

    @Test
    @DisplayName("민감한 변수명만으로 finding을 만들지 않는다")
    void doesNotFlagSecretVariableNamesWithoutLiteralValues() {
        // given
        final List<ChangedFile> files = List.of(new ChangedFile("src/Config.java", "MODIFIED"));
        final String patch = """
                --- a/src/Config.java
                +++ b/src/Config.java
                -String password = \"old-value\";
                +String password = System.getenv(\"APP_PASSWORD\");
                """;

        // when
        final List<FindingInput> findings = extractor.extract(files, patch);

        // then
        assertThat(findings).isEmpty();
    }

    @Test
    @DisplayName("unified diff의 deleted file 표식은 경로 finding을 만들지 않는다")
    void ignoresDeletedDiffFile() {
        // given
        final List<ChangedFile> files = List.of(new ChangedFile("src/deleted.gradle", "DELETED"));
        final String patch = "--- a/src/deleted.gradle\n+++ /dev/null\n-old = true;\n";

        // when
        final List<FindingInput> findings = extractor.extract(
                files, patch);

        // then
        assertThat(findings).isEmpty();
    }

    private static String diff(final List<String> paths, final String addedLine) {
        final StringBuilder diff = new StringBuilder();
        for (String path : paths) {
            diff.append("diff --git a/").append(path).append(" b/").append(path).append('\n')
                    .append("--- a/").append(path).append('\n')
                    .append("+++ b/").append(path).append('\n')
                    .append('+').append(addedLine).append('\n');
        }
        return diff.toString();
    }
}
