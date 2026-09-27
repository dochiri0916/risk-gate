package io.github.dochiri0916.riskgate;

import io.github.dochiri0916.riskgate.riskassessment.adapter.in.bootstrap.LocalRiskGateCli;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import java.util.function.IntConsumer;

public final class LocalRiskGateApplication {
    private LocalRiskGateApplication() { }

    public static void main(final String[] args) {
        launch(args, System::exit);
    }

    static void launch(final String[] args, final IntConsumer exitHandler) {
        final int exitCode = dispatch(args,
                LocalRiskGateApplication::runLocal,
                serverArgs -> SpringApplication.run(RiskGateApplication.class, serverArgs));
        exitHandler.accept(exitCode);
    }

    static boolean isLocalCommand(final String... args) {
        return args.length > 0 && "local".equals(args[0]);
    }

    static int dispatch(
            final String[] args,
            final ToIntFunction<String[]> localRunner,
            final Consumer<String[]> serverRunner
    ) {
        if (isLocalCommand(args)) {
            return localRunner.applyAsInt(args);
        }
        serverRunner.accept(args);
        return 0;
    }

    static int runLocal(final String... args) {
        try (var context = new SpringApplicationBuilder(RiskGateApplication.class)
                .web(WebApplicationType.NONE)
                .properties("spring.main.banner-mode=off", "logging.level.root=OFF")
                .run()) {
            return context.getBean(LocalRiskGateCli.class).run(args);
        }
    }
}
