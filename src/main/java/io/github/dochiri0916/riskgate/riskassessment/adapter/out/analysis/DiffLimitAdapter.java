package io.github.dochiri0916.riskgate.riskassessment.adapter.out.analysis;

import io.github.dochiri0916.riskgate.riskassessment.application.port.out.DiffLimitPort;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "risk-assessment")
public class DiffLimitAdapter implements DiffLimitPort {
    private int maxDiffLength = 262144;

    public void setMaxDiffLength(final int maxDiffLength) {
        this.maxDiffLength = maxDiffLength;
    }

    @Override
    public int maxLength() {
        return maxDiffLength;
    }
}
