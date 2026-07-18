package com.aiprovider.service;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class FoundryCommandRunner {
    private static final int MAX_OUTPUT_CHARS = 64 * 1024;

    public CommandResult run(List<String> command, long timeoutMs)
        throws IOException, InterruptedException, TimeoutException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        FutureTask<String> outputTask = new FutureTask<>(() -> readOutput(process));
        Thread outputThread = new Thread(outputTask, "foundry-command-output");
        outputThread.setDaemon(true);
        outputThread.start();
        if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            throw new TimeoutException("Foundry 命令执行超时");
        }
        try {
            return new CommandResult(process.exitValue(), outputTask.get().trim());
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof IOException) throw (IOException) cause;
            throw new IOException("读取 Foundry 命令输出失败", cause);
        }
    }

    private static String readOutput(Process process) throws IOException {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) >= 0) {
                if (output.length() < MAX_OUTPUT_CHARS) {
                    int accepted = Math.min(read, MAX_OUTPUT_CHARS - output.length());
                    output.append(buffer, 0, accepted);
                }
            }
        }
        return output.toString();
    }

    public static class CommandResult {
        private final int exitCode;
        private final String output;

        public CommandResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }

        public int getExitCode() { return exitCode; }
        public String getOutput() { return output; }
    }
}
