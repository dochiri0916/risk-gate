package io.github.dochiri0916.riskgate.riskassessment.adapter.in.web.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

public record SemgrepReportRequest(@NotNull @Valid List<@NotNull @Valid ResultRequest> results) {
    public SemgrepReportRequest {
        results = results == null ? null : Collections.unmodifiableList(new ArrayList<>(results));
    }
    public record ResultRequest(
            @NotBlank @JsonProperty("check_id") String checkId,
            @NotBlank String path,
            @NotNull @Valid PositionRequest start,
            @NotNull @Valid PositionRequest end,
            @NotNull @Valid ExtraRequest extra
    ) { }

    public record PositionRequest(@NotNull @Positive Integer line, @NotNull @Positive Integer col) { }

    public record ExtraRequest(
            @NotBlank @Pattern(regexp = "ERROR|WARNING|INFO|CRITICAL|HIGH|MEDIUM|LOW|EXPERIMENT|INVENTORY")
            String severity,
            @NotBlank String message
    ) { }
}
