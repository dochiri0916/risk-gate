package io.github.dochiri0916.riskgate.riskassessment.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessSecurityRiskUseCase.AssessSecurityRiskCommand;
import io.github.dochiri0916.riskgate.riskassessment.application.port.out.SecurityRiskAssessmentPort;
import io.github.dochiri0916.riskgate.riskassessment.application.port.out.SecurityRiskAssessmentPort.ChangedFile;
import io.github.dochiri0916.riskgate.riskassessment.application.port.out.SecurityRiskAssessmentPort.SecurityRiskAssessment;
import io.github.dochiri0916.riskgate.riskassessment.application.port.out.SecurityRiskAssessmentPort.SecurityRiskAssessmentRequest;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AssessSecurityRiskServiceTests {
    @Test
    @DisplayName("보안 위험 요청을 Jev Port에 전달하고 typed 결과를 반환한다")
    void delegatesRequestAndMapsResult() {
        // given
        final AtomicReference<SecurityRiskAssessmentRequest> received = new AtomicReference<>();
        final SecurityRiskAssessmentPort port = request -> {
            received.set(request);
            return new SecurityRiskAssessment("jev-1.13.0", 0.41, 150, 18);
        };
        final AssessSecurityRiskService service = new AssessSecurityRiskService(port);
        final AssessSecurityRiskCommand command = new AssessSecurityRiskCommand(
                "private diff", List.of(new ChangedFile("src/Example.java", "MODIFIED"))
        );

        // when
        final var result = service.assess(command);

        // then
        assertThat(received.get().diff()).isEqualTo("private diff");
        assertThat(received.get().changedFiles()).containsExactly(new ChangedFile("src/Example.java", "MODIFIED"));
        assertThat(result.model()).isEqualTo("jev-1.13.0");
        assertThat(result.probability()).isEqualTo(0.41);
        assertThat(result.inputTokens()).isEqualTo(150);
        assertThat(result.outputTokens()).isEqualTo(18);
    }

    @Test
    @DisplayName("명령과 Port 요청은 diff를 toString에 노출하지 않는다")
    void redactsDiffInStringRepresentation() {
        // given
        final AssessSecurityRiskCommand command = new AssessSecurityRiskCommand("secret content", List.of());
        final SecurityRiskAssessmentRequest request = new SecurityRiskAssessmentRequest("secret content", List.of());

        // when
        final String commandString = command.toString();
        final String requestString = request.toString();

        // then
        assertThat(command).hasToString("AssessSecurityRiskCommand[diff=<redacted>]");
        assertThat(request).hasToString("SecurityRiskAssessmentRequest[diff=<redacted>]");
        assertThat(commandString).doesNotContain("secret content");
        assertThat(requestString).doesNotContain("secret content");
    }
}
