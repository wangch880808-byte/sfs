package cn.nus.zknu.module.edu.service.aireview.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalCommandRunnerTest {

    private final LocalCommandRunner runner = new LocalCommandRunner();

    @TempDir
    Path tempDir;

    @Test
    void preservesSpacesAndDoesNotInterpretShellMetacharacters() throws Exception {
        Path script = createScript("command with spaces.sh", "printf '%s' \"$1\"\n");
        Path marker = tempDir.resolve("injected");
        String input = "audio file.wav; touch " + marker;

        String output = runner.run(List.of(script.toString(), input), Duration.ofSeconds(2));

        assertThat(output).isEqualTo(input);
        assertThat(marker).doesNotExist();
    }

    @Test
    void rejectsNonZeroExitAndSanitizesBoundedStderr() throws Exception {
        Path script = createScript("failure.sh",
                "printf 'password=plain-secret\\nsk-example-secret-token\\nline\\rbreak' >&2\n"
                        + "i=0; while [ $i -lt 20000 ]; do printf x >&2; i=$((i + 1)); done\n"
                        + "exit 7\n");

        assertThatThrownBy(() -> runner.run(List.of(script.toString()), Duration.ofSeconds(5)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exit 7")
                .hasMessageContaining("password=<redacted>")
                .hasMessageNotContaining("plain-secret")
                .hasMessageNotContaining("sk-example-secret-token")
                .satisfies(error -> assertThat(error.getMessage()).hasSizeLessThan(17000));
    }

    @Test
    void terminatesTimedOutCommand() throws Exception {
        Path script = createScript("timeout.sh", "sleep 5\n");

        assertThatThrownBy(() -> runner.run(List.of(script.toString()), Duration.ofMillis(100)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("timed out");
    }

    private Path createScript(String name, String body) throws Exception {
        Path script = tempDir.resolve(name);
        Files.writeString(script, "#!/bin/sh\n" + body);
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwx------"));
        return script;
    }
}
