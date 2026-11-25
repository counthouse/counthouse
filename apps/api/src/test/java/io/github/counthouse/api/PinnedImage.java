package io.github.counthouse.api;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.testcontainers.utility.DockerImageName;

/**
 * An image pinned in {@code infra/images/<name>.Dockerfile}, the only place its tag and digest are written. Dependabot
 * updates the file; the {@code workflows} job accepts the same single line.
 */
public final class PinnedImage {

    static final Path DIRECTORY = Path.of("../../infra/images").toAbsolutePath().normalize();

    private static final Pattern LINE =
            Pattern.compile("FROM ([a-z0-9./_-]+:[A-Za-z0-9_][A-Za-z0-9_.-]*@sha256:[0-9a-f]{64})\n");

    private PinnedImage() {}

    public static DockerImageName of(String name) {
        return read(DIRECTORY.resolve(name + ".Dockerfile"));
    }

    static DockerImageName read(Path file) {
        String content;
        try {
            content = Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        var line = LINE.matcher(content);
        if (!line.matches()) {
            throw new IllegalStateException(file + " is not one line FROM <image>:<tag>@sha256:<digest>");
        }
        return DockerImageName.parse(line.group(1));
    }
}
