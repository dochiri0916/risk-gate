package io.github.dochiri0916.riskgate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.RiskPolicyService;
import io.github.dochiri0916.riskgate.riskassessment.domain.model.RiskScore;

@SpringBootApplication
public class RiskGateApplication {

    @Bean
    public RiskPolicyService riskPolicyService() {
        return new RiskPolicyService(new RiskScore(90), new RiskScore(70));
    }

    public static void main(String[] args) {
        SpringApplication.run(RiskGateApplication.class, args);
    }

}
