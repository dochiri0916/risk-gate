package io.github.dochiri0916.riskgate.riskassessment.adapter.out.typesafe;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dochiri0916.riskgate.riskassessment.application.port.out.ChangeContext;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.json.JsonMapper;

class TypeSafeJevSmokeTests {
    @Test
    @DisplayName("명시적으로 활성화하면 네 질문을 한 Jev 요청으로 보내 typed assessment를 받는다")
    @EnabledIfEnvironmentVariable(named = "TYPESAFE_SMOKE_TEST", matches = "true")
    void exchangesTypedSecurityRiskAssessmentWithJev() {
        // given
        final String apiKey = System.getenv("TYPESAFE_API_KEY");
        assertThat(apiKey).as("TYPESAFE_API_KEY 환경변수").isNotBlank();
        final TypeSafeSecurityRiskAssessmentAdapter adapter = new TypeSafeSecurityRiskAssessmentAdapter(
                JsonMapper.builder().build(), apiKey, "https://api.typesafe.ai/v1/systemone",
                java.time.Duration.ofSeconds(30)
        );
        final ChangeContext request = new ChangeContext(
                List.of(new ChangeContext.ChangedFile("src/Example.java", "MODIFIED")),
                "diff --git a/src/Example.java b/src/Example.java\n"
                        + "+public String render(String input) { return input; }\n",
                "PASS", List.of()
        );

        // when
        final var result = adapter.assess(request);

        // then
        assertThat(result.securityRisk().value()).isBetween(0.0, 1.0);
        assertThat(result.authorizationRisk().value()).isBetween(0.0, 1.0);
        assertThat(result.dataIntegrityRisk().value()).isBetween(0.0, 1.0);
        assertThat(result.breakingChangeRisk().value()).isBetween(0.0, 1.0);
    }
}
