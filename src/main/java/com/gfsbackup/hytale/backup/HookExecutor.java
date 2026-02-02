package com.gfsbackup.hytale.backup;

import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.console.ConsoleSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class HookExecutor {
    private static final Logger logger = LoggerFactory.getLogger(HookExecutor.class);
    private final File serverDirectory;

    public HookExecutor(File serverDirectory) {
        this.serverDirectory = serverDirectory;
    }

    public void executePreBackupHooks(List<String> hooks) {
        executeHooks("pre-backup", hooks, Map.of());
    }

    public void executePostBackupHooks(List<String> hooks, String backupFile, String backupFilename, String backupTier, long backupSize) {
        Map<String, String> variables = Map.of(
            "{{backup_file}}", backupFile,
            "{{backup_filename}}", backupFilename,
            "{{backup_tier}}", backupTier,
            "{{backup_size}}", String.valueOf(backupSize)
        );
        executeHooks("post-backup", hooks, variables);
    }

    private void executeHooks(String phase, List<String> hooks, Map<String, String> variables) {
        if (hooks == null || hooks.isEmpty()) {
            return;
        }

        logger.info("Executing {} hooks ({} hooks)", phase, hooks.size());

        for (String hook : hooks) {
            try {
                String processedHook = replaceVariables(hook, variables);

                if (processedHook.startsWith("say ") || processedHook.startsWith("/")) {
                    executeServerCommand(processedHook);
                } else {
                    executeSystemCommand(processedHook);
                }
            } catch (Exception e) {
                logger.error("Failed to execute {} hook: {}", phase, hook, e);
            }
        }
    }

    private void executeServerCommand(String command) {
        try {
            String cmd = command.startsWith("/") ? command.substring(1) : command;
            CommandManager.get().handleCommand(ConsoleSender.INSTANCE, cmd);
            logger.info("Executed server command: {}", cmd);
        } catch (Exception e) {
            logger.error("Failed to execute server command: {}", command, e);
        }
    }

    private void executeSystemCommand(String command) throws IOException, InterruptedException {
        logger.info("Executing system command: {}", command);

        ProcessBuilder pb = new ProcessBuilder("sh", "-c", command);
        pb.directory(serverDirectory);
        pb.redirectErrorStream(true);

        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logger.info("Hook output: {}", line);
            }
        }

        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            logger.warn("Hook timed out after 30 seconds");
        } else {
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                logger.warn("Hook exited with code: {}", exitCode);
            }
        }
    }

    private String replaceVariables(String text, Map<String, String> variables) {
        String result = text;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }
}
