package io.github.chenyurumeng.aghmanager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

final class RootShell {
    static final class Result {
        final int code;
        final String output;

        Result(int code, String output) {
            this.code = code;
            this.output = output == null ? "" : output.trim();
        }

        boolean ok() {
            return code == 0;
        }
    }

    static Result exec(String command) {
        return exec(command, 30);
    }

    static Result exec(String command, long timeoutSeconds) {
        Process process = null;
        StringBuilder out = new StringBuilder();
        try {
            process = new ProcessBuilder("su", "-c", command)
                    .redirectErrorStream(true)
                    .start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (out.length() > 0) out.append('\n');
                    out.append(line);
                }
            }

            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new Result(124, "Command timed out after " + timeoutSeconds + "s");
            }
            return new Result(process.exitValue(), out.toString());
        } catch (Exception e) {
            return new Result(127, e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            if (process != null) process.destroy();
        }
    }

    private RootShell() {}
}
