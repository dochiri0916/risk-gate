package io.github.dochiri0916.riskgate.riskassessment.adapter.in.web.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record BuildConventionReportRequest(
        @NotBlank String schemaVersion,
        @NotBlank String pluginVersion,
        @NotBlank @Pattern(regexp = "PASS|FAIL") String status,
        @NotEmpty Map<@NotBlank String, @NotBlank @Pattern(regexp = "PASS|FAIL|SKIPPED|NOT_APPLIED") String> checks,
        @NotNull @Valid CoverageRequest coverage,
        @NotNull @Valid MutationRequest mutation
) {
    public BuildConventionReportRequest {
        checks = checks == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(checks));
    }

    public record CoverageRequest(
            @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal line,
            @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal branch
    ) { }

    public record MutationRequest(
            @NotNull Boolean enabled,
            @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal score
    ) { }
}
