package io.github.dochiri0916.riskgate.riskassessment.adapter.out.typesafe;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import io.github.dochiri0916.riskgate.riskassessment.application.port.out.ChangeContext;
import io.github.dochiri0916.riskgate.riskassessment.application.port.out.ChangeContext.ChangedFile;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.JevAssessment;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.RiskProbability;
import io.github.dochiri0916.riskgate.riskassessment.application.port.out.SecurityRiskAssessmentPort.FailureKind;
import io.github.dochiri0916.riskgate.riskassessment.application.port.out.SecurityRiskAssessmentPort.SecurityRiskAssessmentException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TypeSafeSecurityRiskAssessmentAdapterTests {
    private static final JsonMapper OBJECT_MAPPER = JsonMapper.builder().build();
    private static final ChangeContext REQUEST = new ChangeContext(
            List.of(new ChangedFile("src/Example.java", "MODIFIED")), "+ added code", "PASS", List.of()
    );

    @Test
    @DisplayName("하나의 Jev 요청에 네 Noul 질문을 보내고 provider-neutral 결과로 변환한다")
    void mapsFourTypedNoulResponsesAndSendsCatalogQuestions() throws IOException {
        // given
        final AtomicReference<String> authHeader = new AtomicReference<>();
        final AtomicReference<JsonNode> requestBody = new AtomicReference<>();
        try (LocalTestServer server = new LocalTestServer(
                200, validResponse(), authHeader, requestBody, 0
        )) {
            final TypeSafeSecurityRiskAssessmentAdapter adapter = adapter(server.url(), "secret-for-test");

            // when
            final var result = adapter.assess(REQUEST);

            // then
            assertThat(result).isEqualTo(new JevAssessment(
                    new RiskProbability(0.73), new RiskProbability(0.22),
                    new RiskProbability(0.33), new RiskProbability(0.44)
            ));
            assertThat(authHeader.get()).isEqualTo("Bearer secret-for-test");
            assertThat(requestBody.get().path("model").asText()).isEqualTo("jev-1.13.0");
            assertThat(requestBody.get().path("questions").size()).isEqualTo(4);
            assertThat(requestBody.get().path("questions").has("security_risk")).isTrue();
            assertThat(requestBody.get().path("questions").has("authorization_risk")).isTrue();
            assertThat(requestBody.get().path("questions").has("data_integrity_risk")).isTrue();
            assertThat(requestBody.get().path("questions").has("breaking_change")).isTrue();
            assertThat(requestBody.get().path("questions").path("security_risk").path("type").asText())
                    .isEqualTo("noul");
            assertThat(requestBody.get().path("state").path("diff").asText()).isEqualTo("+ added code");
            assertThat(requestBody.get().path("state").path("build_convention_status").asText()).isEqualTo("PASS");
        }
    }

    @Test
    @DisplayName("인증 및 HTTP 실패 상태를 구분한다")
    void classifiesHttpFailures() {
        // given
        final List<Integer> statuses = List.of(401, 403, 429, 400, 503);

        // when
        final List<FailureKind> failures = statuses.stream()
                .map(TypeSafeSecurityRiskAssessmentAdapterTests::failureForStatus)
                .toList();

        // then
        assertThat(failures).containsExactly(
                FailureKind.UNAUTHORIZED, FailureKind.FORBIDDEN, FailureKind.RATE_LIMITED,
                FailureKind.HTTP_ERROR, FailureKind.SERVER_ERROR
        );
    }

    @Test
    @DisplayName("유효하지 않은 Jev 응답은 malformed response로 구분한다")
    void classifiesMalformedResponse() throws IOException {
        // given
        try (LocalTestServer server = new LocalTestServer(200, "{\"model\":\"jev-1.13.0\"}")) {
            final TypeSafeSecurityRiskAssessmentAdapter adapter = adapter(server.url(), "test-key");

            // when
            final FailureKind failure = failureKind(adapter);

            // then
            assertThat(failure).isEqualTo(FailureKind.MALFORMED_RESPONSE);
        }
    }

    @Test
    @DisplayName("API key가 없으면 설정 오류로 처리하고 외부 요청을 보내지 않는다")
    void rejectsMissingApiKeyWithoutSendingRequest() throws IOException {
        // given
        try (LocalTestServer server = new LocalTestServer(200, validResponse())) {
            final TypeSafeSecurityRiskAssessmentAdapter adapter = adapter(server.url(), " ");

            // when
            final FailureKind failure = failureKind(adapter);

            // then
            assertThat(failure).isEqualTo(FailureKind.CONFIGURATION_ERROR);
            assertThat(server.requestCounter().get()).isZero();

            final TypeSafeSecurityRiskAssessmentAdapter nullKeyAdapter = new TypeSafeSecurityRiskAssessmentAdapter(
                    OBJECT_MAPPER, null, server.url(), Duration.ofSeconds(2)
            );
            assertThat(failureKind(nullKeyAdapter)).isEqualTo(FailureKind.CONFIGURATION_ERROR);
        }
    }

    @Test
    @DisplayName("Noul 응답 값이 범위를 벗어나면 malformed response로 구분한다")
    void rejectsOutOfRangeNoulProbability() throws IOException {
        // given
            final String response = validResponse().replace("0.73", "1.2");
        try (LocalTestServer server = new LocalTestServer(200, response)) {
            final TypeSafeSecurityRiskAssessmentAdapter adapter = adapter(server.url(), "test-key");

            // when
            final FailureKind failure = failureKind(adapter);

            // then
            assertThat(failure).isEqualTo(FailureKind.MALFORMED_RESPONSE);
        }
    }

    @Test
    @DisplayName("요청 중 응답 제한 시간을 넘기면 timeout으로 구분한다")
    void classifiesTimeout() throws IOException {
        // given
        try (LocalTestServer server = new LocalTestServer(200, validResponse(), 300)) {
            final TypeSafeSecurityRiskAssessmentAdapter adapter = new TypeSafeSecurityRiskAssessmentAdapter(
                    OBJECT_MAPPER, "test-key", server.url(), Duration.ofMillis(50)
            );

            // when
            final FailureKind failure = failureKind(adapter);

            // then
            assertThat(failure).isEqualTo(FailureKind.TIMEOUT);
        }
    }

    @Test
    @DisplayName("연결할 수 없는 Jev endpoint는 연결 오류로 구분한다")
    void classifiesConnectionError() throws IOException {
        // given
        try (LocalTestServer server = new LocalTestServer(200, validResponse())) {
            final int port = server.port();
            server.close();
            final TypeSafeSecurityRiskAssessmentAdapter adapter = new TypeSafeSecurityRiskAssessmentAdapter(
                    OBJECT_MAPPER, "test-key", "http://"
                            + InetAddress.getLoopbackAddress().getHostAddress() + ":" + port,
                    Duration.ofMillis(100)
            );

            // when
            final FailureKind failure = failureKind(adapter);

            // then
            assertThat(failure).isEqualTo(FailureKind.CONNECTION_ERROR);
        }
    }

    @Test
    @DisplayName("질문 유형이 Noul이 아니면 malformed response로 구분한다")
    void rejectsNonNoulAnswerType() throws IOException {
        // given
        final String response = validResponse().replace("\"type\":\"noul\"", "\"type\":\"score\"");
        try (LocalTestServer server = new LocalTestServer(200, response)) {
            final TypeSafeSecurityRiskAssessmentAdapter adapter = adapter(server.url(), "test-key");

            // when
            final FailureKind failure = failureKind(adapter);

            // then
            assertThat(failure).isEqualTo(FailureKind.MALFORMED_RESPONSE);
        }
    }

    @Test
    @DisplayName("누락된 security_risk 답변은 malformed response로 구분한다")
    void rejectsMissingSecurityRiskAnswer() throws IOException {
        // given
        final String response = validResponse().replace("\"security_risk\":{", "\"other\":{");
        try (LocalTestServer server = new LocalTestServer(200, response)) {
            final TypeSafeSecurityRiskAssessmentAdapter adapter = adapter(server.url(), "test-key");

            // when
            final FailureKind failure = failureKind(adapter);

            // then
            assertThat(failure).isEqualTo(FailureKind.MALFORMED_RESPONSE);
        }
    }

    @Test
    @DisplayName("중복 answer key는 malformed response로 구분한다")
    void rejectsDuplicateAnswerKey() throws IOException {
        // given
        final String response = validResponse().replace("\"authorization_risk\":{",
                "\"security_risk\":{\"type\":\"noul\",\"noul\":0.1},\"authorization_risk\":{");
        try (LocalTestServer server = new LocalTestServer(200, response)) {
            final TypeSafeSecurityRiskAssessmentAdapter adapter = adapter(server.url(), "test-key");

            // when
            final FailureKind failure = failureKind(adapter);

            // then
            assertThat(failure).isEqualTo(FailureKind.MALFORMED_RESPONSE);
        }
    }

    @Test
    @DisplayName("필수 response 필드와 token 값이 유효하지 않으면 malformed response로 구분한다")
    void rejectsMissingAndInvalidTypedResponseFields() {
        // given
        final List<String> responseBodies = List.of(
                "{\"model\":null,\"answers\":{},\"usage\":{\"input_tokens\":1,\"output_tokens\":1}}",
                "{\"model\":\"jev-1.13.0\",\"answers\":null,\"usage\":{\"input_tokens\":1,\"output_tokens\":1}}",
                "{\"model\":\"jev-1.13.0\",\"answers\":{},\"usage\":null}",
                validResponse().replace("0.73", "null"),
                validResponse().replace("0.73", "-0.1"),
                validResponse().replace("\"input_tokens\":120", "\"input_tokens\":null"),
                validResponse().replace("\"input_tokens\":120", "\"input_tokens\":-1"),
                validResponse().replace("\"output_tokens\":14", "\"output_tokens\":null"),
                validResponse().replace("\"output_tokens\":14", "\"output_tokens\":-1")
        );

        // when
        final List<FailureKind> failures = responseBodies.stream()
                .map(TypeSafeSecurityRiskAssessmentAdapterTests::failureForResponse)
                .toList();

        // then
        assertThat(failures).containsOnly(FailureKind.MALFORMED_RESPONSE).hasSize(responseBodies.size());
    }

    private static FailureKind failureForStatus(final int status) {
        try (LocalTestServer server = new LocalTestServer(status, "{}")) {
            final TypeSafeSecurityRiskAssessmentAdapter adapter = adapter(server.url(), "test-key");
            return failureKind(adapter);
        } catch (final IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static FailureKind failureForResponse(final String responseBody) {
        try (LocalTestServer server = new LocalTestServer(200, responseBody)) {
            return failureKind(adapter(server.url(), "test-key"));
        } catch (final IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static FailureKind failureKind(final TypeSafeSecurityRiskAssessmentAdapter adapter) {
        try {
            adapter.assess(REQUEST);
            throw new AssertionError("Expected security risk assessment to fail");
        } catch (final SecurityRiskAssessmentException exception) {
            return exception.failureKind();
        }
    }

    private static TypeSafeSecurityRiskAssessmentAdapter adapter(final String endpoint, final String apiKey) {
        return new TypeSafeSecurityRiskAssessmentAdapter(
                OBJECT_MAPPER, apiKey, endpoint, Duration.ofSeconds(2)
        );
    }

    private static String validResponse() {
        return "{\"model\":\"jev-1.13.0\",\"answers\":{\"security_risk\":{" +
                "\"type\":\"noul\",\"noul\":0.73},\"authorization_risk\":{" +
                "\"type\":\"noul\",\"noul\":0.22},\"data_integrity_risk\":{" +
                "\"type\":\"noul\",\"noul\":0.33},\"breaking_change\":{" +
                "\"type\":\"noul\",\"noul\":0.44}},\"usage\":{" +
                "\"input_tokens\":120,\"output_tokens\":14}}";
    }

    private static final class LocalTestServer implements AutoCloseable {
        private final HttpServer server;
        private final AtomicReference<String> authHeader;
        private final AtomicReference<JsonNode> requestBody;
        private final AtomicInteger requestCount = new AtomicInteger();

        private final int responseDelayMillis;

        private LocalTestServer(final int status, final String responseBody) throws IOException {
            this(status, responseBody, 0);
        }

        private LocalTestServer(final int status, final String responseBody, final int responseDelayMillis)
                throws IOException {
            this(status, responseBody, new AtomicReference<>(), new AtomicReference<>(), responseDelayMillis);
        }

        private LocalTestServer(
                final int status,
                final String responseBody,
                final AtomicReference<String> authHeader,
                final AtomicReference<JsonNode> requestBody,
                final int responseDelayMillis
        ) throws IOException {
            this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            this.authHeader = authHeader;
            this.requestBody = requestBody;
            this.responseDelayMillis = responseDelayMillis;
            server.createContext("/v1/systemone", exchange -> {
                requestCount.incrementAndGet();
                this.authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
                this.requestBody.set(OBJECT_MAPPER.readTree(exchange.getRequestBody()));
                if (this.responseDelayMillis > 0) {
                    try {
                        Thread.sleep(this.responseDelayMillis);
                    } catch (final InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                final byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
                try {
                    exchange.sendResponseHeaders(status, body.length);
                    exchange.getResponseBody().write(body);
                } catch (final IOException ignored) {
                    // The test client may time out before the delayed response is written.
                }
                exchange.close();
            });
            server.start();
        }

        private String url() {
            return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort()
                    + "/v1/systemone";
        }

        private int port() {
            return server.getAddress().getPort();
        }

        private AtomicInteger requestCounter() {
            return requestCount;
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
