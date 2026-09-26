package io.github.dochiri0916.riskgate.riskassessment.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase;
import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase.AssessRiskCommand;
import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase.AssessRiskResult;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.RiskDecision;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RiskAssessmentController.class)
class BuildConventionReportMappingTests {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AssessRiskUseCase useCase;

    @Test
    @DisplayName("실제 Build Convention 실패 리포트의 개별 상태와 지표를 Command에 보존한다")
    void mapsReportChecksAndMetricsToCommand() throws Exception {
        // given
        final String report;
        try (var input = getClass().getResourceAsStream("/build-convention/pass-report.json")) {
            report = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        final String failureReport = report.replace("\"status\": \"PASS\"", "\"status\": \"FAIL\"")
                .replace("\"architecture\": \"PASS\"", "\"architecture\": \"FAIL\"");
        final String request = """
                {"reportVersion":"1","repository":"org/service","commitSha":"abcdef1234567",
                 "buildConventionReport":%s,"semgrepReport":{"results":[]},"findings":[],"changedFiles":[]}
                """.replace("%s", failureReport);
        when(useCase.assess(any())).thenReturn(new AssessRiskResult(
                UUID.randomUUID(), "org/service", "abcdef1234567", RiskDecision.BLOCK, 0,
                List.of("BUILD_CONVENTION_FAILED"), List.of(), "1"
        ));

        // when
        final var result = mockMvc.perform(post("/api/v1/risk-assessments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request));

        // then
        result.andExpect(status().isOk());
        final ArgumentCaptor<AssessRiskCommand> captor = ArgumentCaptor.forClass(AssessRiskCommand.class);
        verify(useCase).assess(captor.capture());
        final var mapped = captor.getValue().buildConventionReport();
        assertThat(mapped.schemaVersion()).isEqualTo("1.0");
        assertThat(mapped.pluginVersion()).isEqualTo("1.0.1");
        assertThat(mapped.status()).isEqualTo("FAIL");
        assertThat(mapped.checks()).contains(
                new AssessRiskCommand.Check("BUILD_CONVENTION", "architecture", "FAIL"),
                new AssessRiskCommand.Check("BUILD_CONVENTION", "mutationGate", "NOT_APPLIED")
        );
        assertThat(mapped.coverage().line()).isEqualByComparingTo(new BigDecimal("0.75"));
        assertThat(mapped.coverage().branch()).isEqualByComparingTo(new BigDecimal("0.5"));
        assertThat(mapped.mutation().enabled()).isFalse();
        assertThat(mapped.mutation().score()).isNull();
    }
}
