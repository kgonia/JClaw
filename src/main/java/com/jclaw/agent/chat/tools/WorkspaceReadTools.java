package com.jclaw.agent.chat.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

public class WorkspaceReadTools {

    private final Path launchDirectory;

    public WorkspaceReadTools(Path launchDirectory) {
        this.launchDirectory = launchDirectory;
    }

    @Tool(name = "ReadWorkspaceFile", description = "Reads a UTF-8 text file from the current workspace.")
    public String readFile(@ToolParam(description = "Relative or absolute file path") String path) {
        try {
            return Files.readString(resolve(path));
        }
        catch (IOException e) {
            throw new IllegalStateException("Failed to read file: " + path, e);
        }
    }

    @Tool(name = "ListWorkspaceDirectory", description = "Lists files and directories in the current workspace.")
    public String listDirectory(@ToolParam(description = "Relative or absolute directory path. Defaults to the launch directory.", required = false) String path) {
        Path directory = path == null || path.isBlank() ? launchDirectory : resolve(path);
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("Not a directory: " + directory);
        }
        try (var stream = Files.list(directory)) {
            return stream
                    .sorted()
                    .map(entry -> launchDirectory.relativize(entry.toAbsolutePath().normalize()).toString())
                    .collect(Collectors.joining("\n"));
        }
        catch (IOException e) {
            throw new IllegalStateException("Failed to list directory: " + directory, e);
        }
    }

    private Path resolve(String path) {
        Path candidate = Path.of(path);
        if (!candidate.isAbsolute()) {
            candidate = launchDirectory.resolve(candidate);
        }
        return candidate.normalize();
    }
}
