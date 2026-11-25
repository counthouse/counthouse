package io.github.counthouse.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PinnedImageTests {

    private static final String DIGEST = "sha256:" + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @TempDir
    Path dir;

    @Test
    void readsTheImageWithItsTagAndDigest() throws IOException {
        var image = PinnedImage.read(write("FROM registry.example.com/team/image:1.2.3@" + DIGEST + "\n"));

        assertThat(image.asCanonicalNameString()).isEqualTo("registry.example.com/team/image:1.2.3@" + DIGEST);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "FROM example:1.0\n",
                "FROM example@" + DIGEST + "\n",
                "FROM example:1.0@" + DIGEST,
                "FROM example:1.0@" + DIGEST + "\r\n",
                "FROM example:1.0@" + DIGEST + "\nFROM example:1.0@" + DIGEST + "\n",
                "FROM --platform=linux/amd64 example:1.0@" + DIGEST + "\n",
                "FROM example:1.0@" + DIGEST + " AS db\n",
                "# comment\nFROM example:1.0@" + DIGEST + "\n",
                ""
            })
    void rejectsAnythingButOnePinnedLine(String content) throws IOException {
        var file = write(content);

        assertThatThrownBy(() -> PinnedImage.read(file))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is not one line FROM");
    }

    @Test
    void failsOnAMissingFile() {
        assertThatThrownBy(() -> PinnedImage.read(dir.resolve("missing.Dockerfile")))
                .isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void everyImageInTheRepositoryIsPinned() throws IOException {
        try (var files = Files.list(PinnedImage.DIRECTORY)) {
            assertThat(files.toList())
                    .hasSize(4)
                    .allSatisfy(file -> assertThat(PinnedImage.read(file)).isNotNull());
        }
    }

    private Path write(String content) throws IOException {
        return Files.writeString(dir.resolve("image.Dockerfile"), content);
    }
}
