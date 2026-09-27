package io.github.dochiri0916.riskgate.riskassessment.adapter.in.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LocalRiskGateConfigurationTests {
    @Test
    @DisplayName("application use case로 로컬 CLI bean을 생성한다")
    void createsCliBeanFromUseCase() {
        // given
        final LocalRiskGateConfiguration configuration = new LocalRiskGateConfiguration();
        final AssessRiskUseCase useCase = command -> null;

        // when
        final LocalRiskGateCli cli = configuration.localRiskGateCli(useCase);

        // then
        assertThat(cli).isNotNull();
    }
}
