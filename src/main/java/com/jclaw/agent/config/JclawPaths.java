package com.jclaw.agent.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public record JclawPaths(
        Path launchDirectory,
        Path projectDataDirectory,
        Path sessionDatabasePath,
        Path globalDataDirectory,
        Path globalConfigDatabasePath) {

    public static JclawPaths create(SessionProperties properties) {
        Path launchDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path projectDataDirectory = launchDirectory.resolve(properties.projectDirectoryName()).normalize();
        Path globalDataDirectory = Path.of(System.getProperty("user.home"), properties.globalDirectoryName())
                .toAbsolutePath()
                .normalize();

        createDirectory(projectDataDirectory);
        createDirectory(globalDataDirectory);

        return new JclawPaths(
                launchDirectory,
                projectDataDirectory,
                projectDataDirectory.resolve(properties.sessionDatabaseName()).normalize(),
                globalDataDirectory,
                globalDataDirectory.resolve(properties.globalConfigDatabaseName()).normalize()
        );
    }

    private static void createDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
        }
        catch (IOException e) {
            throw new IllegalStateException("Failed to create directory: " + directory, e);
        }
    }
}
