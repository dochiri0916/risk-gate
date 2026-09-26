package io.github.dochiri0916.riskgate.riskassessment.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class RiskAssessmentControllerTests {
    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("검증 통과 리포트는 PASS 판정을 반환한다")
    void passWhenChecksAndFindingsAreClear() throws Exception {
        // given
        final String request = """
                {"reportVersion":"1","repository":"org/service","commitSha":"abcdef1234567",
                 "checks":[{"tool":"BUILD_CONVENTION","name":"check","status":"PASS"}],
                 "findings":[],"changedFiles":[]}
                """;

        // when
        final var result = mockMvc.perform(post("/api/v1/risk-assessments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request));

        // then
        result
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("PASS"))
                .andExpect(jsonPath("$.score").value(0))
                .andExpect(jsonPath("$.policyVersion").value("1"));
        assertThat(result.andReturn().getResponse().getContentAsString()).contains("\"decision\":\"PASS\"");
    }

    @Test
    @DisplayName("build-convention 실패는 위험 점수와 관계없이 BLOCK 한다")
    void blockWhenBuildConventionFails() throws Exception {
        // given
        final String request = """
                {"reportVersion":"1","repository":"org/service","commitSha":"abcdef1234567",
                 "checks":[{"tool":"BUILD_CONVENTION","name":"check","status":"FAIL"}],
                 "findings":[],"changedFiles":[]}
                """;

        // when
        final var result = mockMvc.perform(post("/api/v1/risk-assessments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request));

        // then
        result
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("BLOCK"))
                .andExpect(jsonPath("$.reasonCodes[0]").value("BUILD_CONVENTION_FAILED"));
        assertThat(result.andReturn().getResponse().getContentAsString()).contains("BUILD_CONVENTION_FAILED");
    }

    @Test
    @DisplayName("위험 점수가 70 이상 90 미만이면 REVIEW 한다")
    void reviewWhenScoreReachesReviewThreshold() throws Exception {
        // given
        final String request = """
                {"reportVersion":"1","repository":"org/service","commitSha":"abcdef1234567",
                 "checks":[{"tool":"BUILD_CONVENTION","name":"check","status":"PASS"}],
                 "findings":[{"category":"CONCURRENCY","severity":"HIGH","source":"AI",
                 "summary":"동시 갱신 위험","evidence":"재고 차감 구간"}],"changedFiles":[]}
                """;

        // when
        final var result = mockMvc.perform(post("/api/v1/risk-assessments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request));

        // then
        result
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("REVIEW"))
                .andExpect(jsonPath("$.score").value(70));
        assertThat(result.andReturn().getResponse().getContentAsString()).contains("\"decision\":\"REVIEW\"");
    }

    @Test
    @DisplayName("지원하지 않는 리포트 버전은 ProblemDetail 오류로 응답한다")
    void rejectUnsupportedReportVersion() throws Exception {
        // given
        final String request = """
                {"reportVersion":"2","repository":"org/service","commitSha":"abcdef1234567",
                 "checks":[{"tool":"BUILD_CONVENTION","name":"check","status":"PASS"}],
                 "findings":[],"changedFiles":[]}
                """;

        // when
        final var result = mockMvc.perform(post("/api/v1/risk-assessments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request));

        // then
        result
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RISK-ASSESSMENT-001"));
        assertThat(result.andReturn().getResponse().getContentAsString()).contains("RISK-ASSESSMENT-001");
    }

    @Test
    @DisplayName("critical Semgrep 결과는 BLOCK 한다")
    void blockWhenSemgrepReportsCriticalFinding() throws Exception {
        // given
        final String request = """
                {"reportVersion":"1","repository":"org/service","commitSha":"abcdef1234567",
                 "checks":[{"tool":"BUILD_CONVENTION","name":"check","status":"PASS"}],
                 "findings":[{"category":"SECURITY","severity":"CRITICAL","source":"SEMGREP",
                 "summary":"위험한 SQL 조립","evidence":"query construction"}],"changedFiles":[]}
                """;

        // when
        final var result = mockMvc.perform(post("/api/v1/risk-assessments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request));

        // then
        result
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("BLOCK"))
                .andExpect(jsonPath("$.reasonCodes[0]").value("SEMGREP_CRITICAL"));
        assertThat(result.andReturn().getResponse().getContentAsString()).contains("SEMGREP_CRITICAL");
    }

    @Test
    @DisplayName("낮은 위험 항목과 변경 파일도 입력 리포트에 보존한다")
    void preserveOptionalFindingEvidenceAndChangedFiles() throws Exception {
        // given
        final String request = """
                {"reportVersion":"1","repository":"org/service","commitSha":"abcdef1234567",
                 "checks":[{"tool":"BUILD_CONVENTION","name":"check","status":"PASS"}],
                 "findings":[{"category":"OPERABILITY","severity":"LOW","source":"AI",
                 "summary":"관측 지표 확인 필요"}],
                 "changedFiles":[{"path":"src/main/java/Inventory.java","changeType":"MODIFIED"}]}
                """;

        // when
        final var result = mockMvc.perform(post("/api/v1/risk-assessments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request));

        // then
        result
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(15))
                .andExpect(jsonPath("$.findings[0].evidence").value(""));
        assertThat(result.andReturn().getResponse().getContentAsString()).contains("\"score\":15");
    }

    @Test
    @DisplayName("AI 위험 점수가 90 이상이면 점수 정책으로 BLOCK 한다")
    void blockWhenAnalyzerScoreReachesBlockThreshold() throws Exception {
        // given
        final String request = """
                {"reportVersion":"1","repository":"org/service","commitSha":"abcdef1234567",
                 "checks":[{"tool":"BUILD_CONVENTION","name":"check","status":"PASS"}],
                 "findings":[{"category":"TRANSACTION","severity":"CRITICAL","source":"AI",
                 "summary":"트랜잭션 경계 위험"}],"changedFiles":[]}
                """;

        // when
        final var result = mockMvc.perform(post("/api/v1/risk-assessments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request));

        // then
        result
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("BLOCK"))
                .andExpect(jsonPath("$.reasonCodes[0]").value("RISK_SCORE_BLOCK_THRESHOLD"));
        assertThat(result.andReturn().getResponse().getContentAsString()).contains("RISK_SCORE_BLOCK_THRESHOLD");
    }

    @Test
    @DisplayName("필수 검사 목록이 없으면 요청을 거부한다")
    void rejectReportWithoutLists() throws Exception {
        // given
        final String request = """
                {"reportVersion":"1","repository":"org/service","commitSha":"abcdef1234567"}
                """;

        // when
        final var result = mockMvc.perform(post("/api/v1/risk-assessments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request));

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RISKGATE-400"));
        assertThat(result.andReturn().getResponse().getStatus()).isEqualTo(400);
    }
}
