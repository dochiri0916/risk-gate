package io.github.dochiri0916.riskgate.riskassessment.adapter.in.bootstrap;

import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class LocalRiskGateConfiguration {
    @Bean
    public LocalRiskGateCli localRiskGateCli(final AssessRiskUseCase assessRiskUseCase) {
        return new LocalRiskGateCli(assessRiskUseCase);
    }

    @Bean
    public CiRiskGateCli ciRiskGateCli(final AssessRiskUseCase assessRiskUseCase) {
        return new CiRiskGateCli(assessRiskUseCase);
    }
}
