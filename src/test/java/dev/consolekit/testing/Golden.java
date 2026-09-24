package dev.consolekit.testing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Golden-file assertions shared by every part.
 *
 * <p>Files live under {@code src/test/resources/golden/}, are read as UTF-8 with {@code \n} line endings,
 * and are written only when {@code -Dconsolekit.golden.update=true} is set. Without the flag a missing or
 * different golden fails; it is never created or rewritten silently.
 */
public final class Golden {

    public static final String UPDATE_PROPERTY = "consolekit.golden.update";
    public static final Path ROOT = Path.of("src", "test", "resources", "golden");

    private Golden() {
    }

    public static void assertMatches(String name, String actual) {
        assertMatches(ROOT, name, actual, Boolean.getBoolean(UPDATE_PROPERTY));
    }

    static void assertMatches(Path root, String name, String actual, boolean update) {
        Path file = root.resolve(name);
        String normalised = actual.replace("\r\n", "\n");
        if (update) {
            if (normalised.isEmpty()) fail("refusing to write an empty golden: " + file);
            write(file, normalised);
            return;
        }
        if (!Files.exists(file)) {
            fail("missing golden " + file + "; create it with -D" + UPDATE_PROPERTY + "=true and review the diff");
        }
        assertEquals(read(file), normalised, "golden " + file);
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(Path file, String content) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
