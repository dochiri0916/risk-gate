package io.github.dochiri0916.riskgate.riskassessment.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessSecurityRiskUseCase.AssessSecurityRiskCommand;
import io.github.dochiri0916.riskgate.riskassessment.application.port.out.ChangeContext;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.JevAssessment;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.RiskProbability;
import io.github.dochiri0916.riskgate.riskassessment.application.port.out.SecurityRiskAssessmentPort;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AssessSecurityRiskServiceTests {
    @Test
    @DisplayName("provider-neutral change context를 Port에 전달하고 typed assessment를 반환한다")
    void delegatesContextAndMapsResult() {
        // given
        final AtomicReference<ChangeContext> received = new AtomicReference<>();
        final JevAssessment assessment = new JevAssessment(
                new RiskProbability(0.41), new RiskProbability(0.2),
                new RiskProbability(0.3), new RiskProbability(0.4)
        );
        final SecurityRiskAssessmentPort port = context -> {
            received.set(context);
            return assessment;
        };
        final AssessSecurityRiskService service = new AssessSecurityRiskService(port);
        final ChangeContext context = new ChangeContext(
                List.of(new ChangeContext.ChangedFile("src/Example.java", "MODIFIED")),
                "private diff", "PASS", List.of()
        );

        // when
        final var result = service.assess(new AssessSecurityRiskCommand(context));

        // then
        assertThat(received.get()).isSameAs(context);
        assertThat(result.assessment()).isEqualTo(assessment);
    }

}
