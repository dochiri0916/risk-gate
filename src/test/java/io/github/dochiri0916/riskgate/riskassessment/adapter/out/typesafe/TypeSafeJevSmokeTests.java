package io.github.dochiri0916.riskgate.riskassessment.adapter.out.typesafe;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dochiri0916.riskgate.riskassessment.application.port.out.SecurityRiskAssessmentPort.ChangedFile;
import io.github.dochiri0916.riskgate.riskassessment.application.port.out.SecurityRiskAssessmentPort.SecurityRiskAssessmentRequest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.json.JsonMapper;

class TypeSafeJevSmokeTests {
    @Test
    @DisplayName("명시적으로 활성화하면 Jev API와 실제 통신하고 typed Noul 응답을 받는다")
    @EnabledIfEnvironmentVariable(named = "TYPESAFE_SMOKE_TEST", matches = "true")
    void exchangesTypedSecurityRiskAssessmentWithJev() {
        // given
        final String apiKey = System.getenv("TYPESAFE_API_KEY");
        assertThat(apiKey).as("TYPESAFE_API_KEY 환경변수").isNotBlank();
        final TypeSafeSecurityRiskAssessmentAdapter adapter = new TypeSafeSecurityRiskAssessmentAdapter(
                JsonMapper.builder().build(), apiKey, "https://api.typesafe.ai/v1/systemone",
                java.time.Duration.ofSeconds(30)
        );
        final SecurityRiskAssessmentRequest request = new SecurityRiskAssessmentRequest(
                "diff --git a/src/Example.java b/src/Example.java\n"
                        + "+public String render(String input) { return input; }\n",
                List.of(new ChangedFile("src/Example.java", "MODIFIED"))
        );

        // when
        final var result = adapter.assess(request);

        // then
        assertThat(result.model()).isEqualTo("jev-1.13.0");
        assertThat(result.probability()).isBetween(0.0, 1.0);
        assertThat(result.inputTokens()).isPositive();
        assertThat(result.outputTokens()).isNotNegative();
    }
}
