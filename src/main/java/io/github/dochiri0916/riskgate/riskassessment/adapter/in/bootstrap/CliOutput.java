package io.github.dochiri0916.riskgate.riskassessment.adapter.in.bootstrap;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

final class CliOutput {
    private final PrintStream outputStream;
    private final PrintStream errorStream;

    CliOutput() {
        this(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8),
                new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));
    }

    CliOutput(final PrintStream stdout, final PrintStream stderr) {
        this.outputStream = stdout;
        this.errorStream = stderr;
    }

    PrintStream stdout() {
        return outputStream;
    }

    void error(final String message) {
        errorStream.println(message);
    }
}
