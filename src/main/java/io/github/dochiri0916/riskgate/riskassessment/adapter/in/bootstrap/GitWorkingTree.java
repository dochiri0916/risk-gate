package io.github.dochiri0916.riskgate.riskassessment.adapter.in.bootstrap;

import io.github.dochiri0916.riskgate.riskassessment.application.port.in.AssessRiskUseCase.AssessRiskCommand.ChangedFile;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class GitWorkingTree {
    private GitWorkingTree() { }

    static String resolveBase(final Path project, final String requested) throws IOException {
        if (requested != null && !requested.isBlank()) {
            return resolveCommit(project, requested);
        }
        final String originMain = resolveOptional(project, "origin/main");
        if (originMain != null) {
            return originMain;
        }
        final String remotes = command(project, "remote");
        for (String remote : remotes.lines().toList()) {
            if (remote.isBlank()) {
                continue;
            }
            final String defaultRef = resolveOptional(project, "refs/remotes/" + remote + "/HEAD");
            if (defaultRef != null) {
                return resolveCommit(project, defaultRef);
            }
        }
        throw new LocalRiskGateCli.LocalInputException("Unable to determine a base commit; pass --base <ref>");
    }

    static String head(final Path project) throws IOException {
        return command(project, "rev-parse", "--verify", "HEAD^{commit}").trim();
    }

    static Snapshot snapshot(final Path project, final String base) throws IOException {
        final List<ChangedFile> changedFiles = changedFiles(project, base);
        final StringBuilder diff = new StringBuilder(command(project,
                "diff", "--no-ext-diff", "--no-color", "--find-renames", base, "--"));
        for (String path : nulSeparated(command(project, "ls-files", "--others", "--exclude-standard", "-z"))) {
            final CommandResult result = execute(project, true,
                    "diff", "--no-index", "--no-ext-diff", "--no-color", "--", "/dev/null", path);
            if (result.exitCode() != 1 && result.exitCode() != 0) {
                throw new LocalRiskGateCli.LocalInputException("Unable to read an untracked working tree file");
            }
            diff.append(result.stdout());
        }
        return new Snapshot(changedFiles, diff.toString());
    }

    private static List<ChangedFile> changedFiles(final Path project, final String base) throws IOException {
        final List<ChangedFile> changed = new ArrayList<>();
        final TokenCursor tokens = new TokenCursor(nulSeparated(command(project,
                "diff", "--name-status", "-z", "--find-renames", base, "--")));
        while (tokens.hasNext()) {
            final String status = tokens.next();
            if (status.startsWith("R")) {
                if (tokens.remaining() < 2) {
                    throw new IOException("Invalid git name-status result");
                }
                tokens.next();
                changed.add(new ChangedFile(tokens.next(), "RENAMED"));
                continue;
            }
            if (!tokens.hasNext()) {
                throw new IOException("Invalid git name-status result");
            }
            final String type = switch (status) {
                case "A" -> "ADDED";
                case "D" -> "DELETED";
                case "M", "T" -> "MODIFIED";
                default -> throw new IOException("Unsupported git change type");
            };
            changed.add(new ChangedFile(tokens.next(), type));
        }
        final List<String> untracked = nulSeparated(command(project,
                "ls-files", "--others", "--exclude-standard", "-z"));
        for (String path : untracked) {
            changed.add(new ChangedFile(path, "ADDED"));
        }
        return List.copyOf(changed);
    }

    private static String resolveCommit(final Path project, final String ref) throws IOException {
        try {
            return command(project, "rev-parse", "--verify", ref + "^{commit}").trim();
        } catch (IOException exception) {
            throw new LocalRiskGateCli.LocalInputException("Base ref does not resolve to a commit");
        }
    }

    private static String resolveOptional(final Path project, final String ref) throws IOException {
        try {
            return command(project, "rev-parse", "--verify", ref + "^{commit}").trim();
        } catch (IOException exception) {
            return null;
        }
    }

    private static String command(final Path project, final String... args) throws IOException {
        final CommandResult result = execute(project, false, args);
        if (result.exitCode() != 0) {
            throw new IOException("Git command failed");
        }
        return result.stdout();
    }

    private static CommandResult execute(
            final Path project,
            final boolean suppressErrors,
            final String... args
    ) throws IOException {
        final List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        final ProcessBuilder builder = new ProcessBuilder(command).directory(project.toFile());
        if (suppressErrors) {
            builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        }
        final Process process = builder.start();
        final byte[] stdout;
        try (var input = process.getInputStream(); var output = new ByteArrayOutputStream()) {
            input.transferTo(output);
            stdout = output.toByteArray();
        }
        try {
            return new CommandResult(new String(stdout, StandardCharsets.UTF_8), process.waitFor());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Git command interrupted");
        }
    }

    private static List<String> nulSeparated(final String value) {
        final List<String> values = new ArrayList<>();
        for (String token : value.split("\u0000", -1)) {
            if (!token.isEmpty()) {
                values.add(token);
            }
        }
        return values;
    }

    record Snapshot(List<ChangedFile> changedFiles, String diff) { }
    private record CommandResult(String stdout, int exitCode) { }

    private static final class TokenCursor {
        private final List<String> tokens;
        private int position;

        private TokenCursor(final List<String> tokens) {
            this.tokens = tokens;
        }

        private boolean hasNext() {
            return position < tokens.size();
        }

        private int remaining() {
            return tokens.size() - position;
        }

        private String next() {
            final String token = tokens.get(position);
            position = position + 1;
            return token;
        }
    }
}
