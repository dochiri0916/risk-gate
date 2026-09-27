package io.github.dochiri0916.riskgate.riskassessment.adapter.out.typesafe;

import io.github.dochiri0916.riskgate.riskassessment.application.port.out.SecurityRiskAssessmentPort;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
public final class TypeSafeSecurityRiskAssessmentAdapter implements SecurityRiskAssessmentPort {
    static final String MODEL = "jev-1.13.0";
    private static final String ENDPOINT = "https://api.typesafe.ai/v1/systemone";
    private static final String QUESTION_ID = "security_risk";

    private final HttpClient httpClient;
    private final JsonMapper objectMapper;
    private final String apiKey;
    private final String endpoint;
    private final Duration requestTimeout;

    @Autowired
    public TypeSafeSecurityRiskAssessmentAdapter(final JsonMapper objectMapper) {
        this(objectMapper, System.getenv("TYPESAFE_API_KEY"), ENDPOINT, Duration.ofSeconds(30));
    }

    TypeSafeSecurityRiskAssessmentAdapter(
            final JsonMapper objectMapper,
            final String apiKey,
            final String endpoint,
            final Duration requestTimeout
    ) {
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.endpoint = endpoint;
        this.requestTimeout = requestTimeout;
    }

    @Override
    public SecurityRiskAssessment assess(final SecurityRiskAssessmentRequest request) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new SecurityRiskAssessmentException(FailureKind.CONFIGURATION_ERROR);
        }

        final RequestBody requestBody = new RequestBody(
                Map.of("diff", request.diff(), "changed_files", request.changedFiles()),
                MODEL,
                Map.of(QUESTION_ID, new NoulQuestion(
                        "noul",
                        "Does this code change introduce a plausible security vulnerability? Evaluate only "
                                + "vulnerabilities introduced by the changed lines, considering the supplied "
                                + "diff and changed file paths.",
                        new NoulCriteria(
                                "A changed line plausibly introduces an exploitable weakness that could "
                                        + "compromise confidentiality, integrity, or availability.",
                                "The change does not plausibly introduce an exploitable security weakness."
                        )
                ))
        );

        final byte[] requestBytes;
        try {
            requestBytes = objectMapper.writeValueAsBytes(requestBody);
        } catch (final JacksonException exception) {
            throw new SecurityRiskAssessmentException(FailureKind.REQUEST_ERROR);
        }

        final HttpResponse<byte[]> response;
        try {
            final HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(requestTimeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(requestBytes))
                    .build();
            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
        } catch (final HttpTimeoutException exception) {
            throw new SecurityRiskAssessmentException(FailureKind.TIMEOUT);
        } catch (final IOException exception) {
            throw new SecurityRiskAssessmentException(FailureKind.CONNECTION_ERROR);
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SecurityRiskAssessmentException(FailureKind.CONNECTION_ERROR);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new SecurityRiskAssessmentException(failureFor(response.statusCode()));
        }
        return parseResponse(response.body());
    }

    private SecurityRiskAssessment parseResponse(final byte[] body) {
        try {
            final JevResponse response = objectMapper.readValue(body, JevResponse.class);
            if (response == null || response.model() == null || response.answers() == null
                    || response.usage() == null) {
                throw new SecurityRiskAssessmentException(FailureKind.MALFORMED_RESPONSE);
            }
            final NoulAnswer answer = response.answers().get(QUESTION_ID);
            if (answer == null || !"noul".equals(answer.type()) || answer.noul() == null
                    || !Double.isFinite(answer.noul()) || answer.noul() < 0 || answer.noul() > 1
                    || response.usage().inputTokens() == null || response.usage().inputTokens() < 0
                    || response.usage().outputTokens() == null || response.usage().outputTokens() < 0) {
                throw new SecurityRiskAssessmentException(FailureKind.MALFORMED_RESPONSE);
            }
            return new SecurityRiskAssessment(
                    response.model(), answer.noul(), response.usage().inputTokens(), response.usage().outputTokens()
            );
        } catch (final JacksonException exception) {
            throw new SecurityRiskAssessmentException(FailureKind.MALFORMED_RESPONSE);
        }
    }

    private static FailureKind failureFor(final int status) {
        return switch (status) {
            case 401 -> FailureKind.UNAUTHORIZED;
            case 403 -> FailureKind.FORBIDDEN;
            case 429 -> FailureKind.RATE_LIMITED;
            default -> status >= 500 ? FailureKind.SERVER_ERROR : FailureKind.HTTP_ERROR;
        };
    }

    private record RequestBody(Map<String, Object> state, String model, Map<String, NoulQuestion> questions) { }

    private record NoulQuestion(String type, String instructions, NoulCriteria criteria) { }

    private record NoulCriteria(
            @com.fasterxml.jackson.annotation.JsonProperty("true") String yes,
            @com.fasterxml.jackson.annotation.JsonProperty("false") String no
    ) { }

    private record JevResponse(String model, Map<String, NoulAnswer> answers, Usage usage) { }

    private record NoulAnswer(String type, Double noul) { }

    private record Usage(
            @com.fasterxml.jackson.annotation.JsonProperty("input_tokens") Integer inputTokens,
            @com.fasterxml.jackson.annotation.JsonProperty("output_tokens") Integer outputTokens
    ) { }
}
