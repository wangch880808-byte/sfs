package cn.nus.zknu.module.edu.service.aireview.support;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Component
public class LocalCommandRunner {

    private static final int STDOUT_LIMIT = 1024 * 1024;
    private static final int STDERR_LIMIT = 16 * 1024;
    private static final Pattern SENSITIVE_ASSIGNMENT = Pattern.compile(
            "(?i)(password|token|secret|api[_-]?key)\\s*[=:]\\s*\\S+");
    private static final Pattern API_TOKEN = Pattern.compile("sk-[A-Za-z0-9_-]{8,}");

    public String run(List<String> command, Duration timeout) {
        if (command == null || command.isEmpty() || command.stream().anyMatch(this::isBlank)) {
            throw new IllegalArgumentException("command arguments must not be blank");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("command timeout must be positive");
        }

        Process process;
        try {
            process = new ProcessBuilder(command).start();
        } catch (IOException ex) {
            throw new IllegalStateException("failed to start command: " + executableName(command), ex);
        }

        CompletableFuture<String> stdoutFuture = readBounded(process.getInputStream(), STDOUT_LIMIT);
        CompletableFuture<String> stderrFuture = readBounded(process.getErrorStream(), STDERR_LIMIT);
        boolean finished;
        try {
            finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            destroyProcessTree(process);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("command interrupted: " + executableName(command), ex);
        }
        if (!finished) {
            destroyProcessTree(process);
            awaitTermination(process);
        }

        String stdout = awaitOutput(stdoutFuture);
        String stderr = sanitize(awaitOutput(stderrFuture));
        if (!finished) {
            throw new IllegalStateException("command timed out: " + executableName(command) + stderrSuffix(stderr));
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("command failed: " + executableName(command)
                    + " (exit " + process.exitValue() + ")" + stderrSuffix(stderr));
        }
        return stdout;
    }

    private CompletableFuture<String> readBounded(InputStream stream, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                StringBuilder output = new StringBuilder(Math.min(limit, 8192));
                char[] buffer = new char[4096];
                int count;
                while ((count = reader.read(buffer)) >= 0) {
                    int remaining = limit - output.length();
                    if (remaining > 0) {
                        output.append(buffer, 0, Math.min(count, remaining));
                    }
                }
                return output.toString();
            } catch (IOException ex) {
                throw new CompletionException(ex);
            }
        });
    }

    private String awaitOutput(CompletableFuture<String> future) {
        try {
            return future.join();
        } catch (CompletionException ex) {
            throw new IllegalStateException("failed to read command output", ex.getCause());
        }
    }

    private void awaitTermination(Process process) {
        try {
            process.waitFor(5, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private void destroyProcessTree(Process process) {
        process.toHandle().descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String sanitized = value.replaceAll("[\\p{Cntrl}&&[^\\t\\n\\r]]", "?")
                .replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
        sanitized = SENSITIVE_ASSIGNMENT.matcher(sanitized).replaceAll("$1=<redacted>");
        return API_TOKEN.matcher(sanitized).replaceAll("<redacted>");
    }

    private String stderrSuffix(String stderr) {
        return stderr.isBlank() ? "" : "; stderr: " + stderr;
    }

    private String executableName(List<String> command) {
        String executable = command.get(0).replace('\\', '/');
        int separator = executable.lastIndexOf('/');
        return separator >= 0 ? executable.substring(separator + 1) : executable;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
