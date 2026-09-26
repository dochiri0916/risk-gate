package io.github.dochiri0916.riskgate.riskassessment.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
                 "buildConventionReport":%s,"semgrepReport":{"results":[]},
                 "findings":[],"changedFiles":[]}
                """.replace("%s", passReport());

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
                 "buildConventionReport":%s,"semgrepReport":{"results":[]},
                 "findings":[],"changedFiles":[]}
                """.replace("%s", failReport());

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
                 "buildConventionReport":%s,"semgrepReport":{"results":[]},
                 "findings":[{"category":"CONCURRENCY","severity":"HIGH","source":"AI",
                 "summary":"동시 갱신 위험","evidence":"재고 차감 구간"}],"changedFiles":[]}
                """.replace("%s", passReport());

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
                 "buildConventionReport":%s,"semgrepReport":{"results":[]},
                 "findings":[],"changedFiles":[]}
                """.replace("%s", passReport());

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
                 "buildConventionReport":%s,"semgrepReport":{"results":[]},
                 "semgrepReport":{"results":[{"check_id":"security.sql-injection","path":"src/Order.java",
                 "start":{"line":12,"col":1},"end":{"line":12,"col":9},
                 "extra":{"severity":"CRITICAL","message":"위험한 SQL 조립"}}]},
                 "findings":[],"changedFiles":[]}
                """.replace("%s", passReport());

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
    @DisplayName("실제 형식 Semgrep 결과가 차단과 근거로 전달된다")
    void semgrepFixtureBlocksAndPreservesFinding() throws Exception {
        // given
        final String fixture;
        try (var input = getClass().getResourceAsStream("/semgrep/critical-report.json")) {
            fixture = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        final String request = requestWithReport(passReport())
                .replace("\"semgrepReport\":{\"results\":[]}", "\"semgrepReport\":" + fixture);

        // when & then
        final var result = mockMvc.perform(post("/api/v1/risk-assessments")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("BLOCK"))
                .andExpect(jsonPath("$.reasonCodes[0]").value("SEMGREP_CRITICAL"))
                .andExpect(jsonPath("$.findings[0].source").value("SEMGREP"))
                .andExpect(jsonPath("$.findings[0].evidence").value("security.sql-injection at src/Order.java:12"));
        assertThat(result.andReturn().getResponse().getContentAsString()).contains("SEMGREP_CRITICAL");
    }

    @Test
    @DisplayName("잘못된 Semgrep 리포트는 요청 오류로 거부한다")
    void invalidSemgrepReportIsRejected() throws Exception {
        // given
        final String valid = requestWithReport(passReport());
        final String[] reports = {"null", "{}", "{\"results\":null}",
                "{\"results\":[{}]}",
                "{\"results\":[{\"check_id\":\"rule\",\"path\":\"file\","
                        + "\"start\":{\"line\":1,\"col\":1},\"end\":{\"line\":1,\"col\":2},"
                        + "\"extra\":{\"severity\":\"UNKNOWN\",\"message\":\"message\"}}]}"};
        // when & then
        for (final String report : reports) {
            final var result = mockMvc.perform(post("/api/v1/risk-assessments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(valid.replace("\"semgrepReport\":{\"results\":[]}",
                                    "\"semgrepReport\":" + report)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("RISKGATE-400"));
            assertThat(result.andReturn().getResponse().getContentAsString()).contains("RISKGATE-400");
        }
    }

    @Test
    @DisplayName("Semgrep 심각도는 정해진 Risk Severity로 변환된다")
    void mapsSemgrepSeverities() throws Exception {
        // given
        final String request = requestWithReport(passReport());
        final String[] severities = {"INFO", "LOW", "EXPERIMENT", "INVENTORY", "WARNING", "MEDIUM",
                "ERROR", "HIGH", "CRITICAL"};
        final String[] expected = {"LOW", "LOW", "LOW", "LOW", "MEDIUM", "MEDIUM",
                "HIGH", "HIGH", "CRITICAL"};

        // when & then
        for (int index = 0; index < severities.length; index++) {
            final String report = "{\"results\":[{\"check_id\":\"rule\",\"path\":\"file\","
                    + "\"start\":{\"line\":1,\"col\":1},\"end\":{\"line\":1,\"col\":2},"
                    + "\"extra\":{\"severity\":\"" + severities[index] + "\",\"message\":\"message\"}}]}";
            final var result = mockMvc.perform(post("/api/v1/risk-assessments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request.replace("\"semgrepReport\":{\"results\":[]}",
                                    "\"semgrepReport\":" + report)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.findings[0].severity").value(expected[index]));
            assertThat(result.andReturn().getResponse().getContentAsString()).contains("\"source\":\"SEMGREP\"");
        }
    }

    @Test
    @DisplayName("낮은 위험 항목과 변경 파일도 입력 리포트에 보존한다")
    void preserveOptionalFindingEvidenceAndChangedFiles() throws Exception {
        // given
        final String request = """
                {"reportVersion":"1","repository":"org/service","commitSha":"abcdef1234567",
                 "buildConventionReport":%s,"semgrepReport":{"results":[]},
                 "findings":[{"category":"OPERABILITY","severity":"LOW","source":"AI",
                 "summary":"관측 지표 확인 필요"}],
                 "changedFiles":[{"path":"src/main/java/Inventory.java","changeType":"MODIFIED"}]}
                """.replace("%s", passReport());

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
                 "buildConventionReport":%s,"semgrepReport":{"results":[]},
                 "findings":[{"category":"TRANSACTION","severity":"CRITICAL","source":"AI",
                 "summary":"트랜잭션 경계 위험"}],"changedFiles":[]}
                """.replace("%s", passReport());

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

    @Test
    @DisplayName("잘못된 위험 분류는 요청 오류로 응답한다")
    void rejectInvalidRiskCategory() throws Exception {
        // given
        final String request = """
                {"reportVersion":"1","repository":"org/service","commitSha":"abcdef1234567",
                 "buildConventionReport":%s,"semgrepReport":{"results":[]},
                 "findings":[{"category":"UNKNOWN","severity":"LOW","source":"AI",
                 "summary":"위험"}],"changedFiles":[]}
                """.replace("%s", passReport());

        // when
        final var result = mockMvc.perform(post("/api/v1/risk-assessments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request));

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RISKGATE-400"));
        assertThat(result.andReturn().getResponse().getContentAsString()).contains("RISKGATE-400");
    }

    @Test
    @DisplayName("잘못된 위험 심각도는 요청 오류로 응답한다")
    void rejectInvalidRiskSeverity() throws Exception {
        // given
        final String request = """
                {"reportVersion":"1","repository":"org/service","commitSha":"abcdef1234567",
                 "buildConventionReport":%s,"semgrepReport":{"results":[]},
                 "findings":[{"category":"SECURITY","severity":"UNKNOWN","source":"AI",
                 "summary":"위험"}],"changedFiles":[]}
                """.replace("%s", passReport());

        // when
        final var result = mockMvc.perform(post("/api/v1/risk-assessments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request));

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RISKGATE-400"));
        assertThat(result.andReturn().getResponse().getContentAsString()).contains("RISKGATE-400");
    }

    @Test
    @DisplayName("Build Convention 플러그인 버전이 달라도 지원 스키마는 처리한다")
    void acceptSupportedSchemaWithDifferentPluginVersion() throws Exception {
        // given
        final String report = passReport().replace("\"pluginVersion\": \"1.0.1\"",
                "\"pluginVersion\": \"2.0.0\"");

        // when
        final var result = mockMvc.perform(post("/api/v1/risk-assessments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestWithReport(report)));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("PASS"))
                .andExpect(jsonPath("$.score").value(0));
        assertThat(result.andReturn().getResponse().getContentAsString()).contains("\"decision\":\"PASS\"");
    }

    @Test
    @DisplayName("지원하지 않는 Build Convention 스키마는 명확한 요청 오류로 응답한다")
    void rejectUnsupportedBuildConventionSchema() throws Exception {
        // given
        final String report = passReport().replace("\"schemaVersion\": \"1.0\"",
                "\"schemaVersion\": \"2.0\"");

        // when
        final var result = mockMvc.perform(post("/api/v1/risk-assessments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestWithReport(report)));

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RISK-ASSESSMENT-005"))
                .andExpect(jsonPath("$.schemaVersion").value("2.0"));
        assertThat(result.andReturn().getResponse().getContentAsString()).contains("RISK-ASSESSMENT-005");
    }

    @Test
    @DisplayName("누락되거나 잘못된 Build Convention 리포트는 요청 오류로 처리한다")
    void rejectMissingOrMalformedBuildConventionReport() throws Exception {
        // given
        final String report = passReport();
        final String[] invalidRequests = {
                requestWithReport("null"),
                requestWithReport("null").replace("\"buildConventionReport\":null,", ""),
                requestWithReport(report.replace("\"schemaVersion\": \"1.0\",", "")),
                requestWithReport(report.replace("\"pluginVersion\": \"1.0.1\",", "")),
                requestWithReport(report.replace("\"status\": \"PASS\",", "")),
                requestWithReport(report.replace("\"status\": \"PASS\"", "\"status\": \"UNKNOWN\"")),
                requestWithReport(report.replace("\"architecture\": \"PASS\"",
                        "\"architecture\": \"UNKNOWN\"")),
                requestWithReport(report.replace("\"architecture\": \"PASS\"",
                        "\"architecture\": null")),
                requestWithReport(report.replace("\"checks\": {", "\"checks\": [")),
                requestWithReport(report.replace("\"checks\": {", "\"checks\": null, \"unused\": {")),
                requestWithReport(report.replace("\"coverage\": {", "\"coverage\": null, \"unused\": {")),
                requestWithReport(report.replace("\"mutation\": {", "\"mutation\": null, \"unused\": {"))
        };

        // when & then
        for (final String invalidRequest : invalidRequests) {
            final var result = mockMvc.perform(post("/api/v1/risk-assessments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(invalidRequest));
            result.andExpect(status().isBadRequest());
            assertThat(result.andReturn().getResponse().getContentAsString()).contains("RISKGATE-400");
        }
    }

    @Test
    @DisplayName("잘못된 변경 유형을 거부한다")
    void rejectsInvalidChangeType() throws Exception {
        // given
        final String request = requestWithReport(passReport()).replace(
                "\"changedFiles\":[]", "\"changedFiles\":[{\"path\":\"file\",\"changeType\":\"COPIED\"}]");
        // when
        final var response = mockMvc.perform(post("/api/v1/risk-assessments")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RISKGATE-400"));
        // then
        assertThat(response.andReturn().getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("diff 생략과 빈 문자열을 허용한다")
    void acceptsAbsentAndEmptyDiff() throws Exception {
        // given
        final String request = requestWithReport(passReport());
        // when
        final var absent = mockMvc.perform(post("/api/v1/risk-assessments")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk());
        final var empty = mockMvc.perform(post("/api/v1/risk-assessments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.replace("\"changedFiles\":[]", "\"changedFiles\":[],\"diff\":\"\"")))
                .andExpect(status().isOk());
        // then
        assertThat(absent.andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(empty.andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("초과 diff를 노출 없이 거부한다")
    void rejectsOversizedDiffWithoutExposingIt() throws Exception {
        // given
        final String marker = "secret-marker";
        final String diff = marker + "x".repeat(262144);
        final String request = requestWithReport(passReport()).replace(
                "\"changedFiles\":[]", "\"changedFiles\":[],\"diff\":\"" + diff + "\"");
        // when
        final var response = mockMvc.perform(post("/api/v1/risk-assessments")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RISK-ASSESSMENT-006"))
                .andReturn().getResponse().getContentAsString();
        // then
        assertThat(response).doesNotContain(marker);
    }

    private String requestWithReport(final String report) {
        return """
                {"reportVersion":"1","repository":"org/service","commitSha":"abcdef1234567",
                 "buildConventionReport":%s,"semgrepReport":{"results":[]},"findings":[],"changedFiles":[]}
                """.replace("%s", report);
    }

    private String passReport() throws IOException {
        try (var input = getClass().getResourceAsStream("/build-convention/pass-report.json")) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String failReport() throws IOException {
        return passReport().replace("\"status\": \"PASS\"", "\"status\": \"FAIL\"")
                .replace("\"architecture\": \"PASS\"", "\"architecture\": \"FAIL\"");
    }
}
