package dev.consolekit.testing;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.AssertionFailedError;

import static org.junit.jupiter.api.Assertions.*;

class GoldenTest {

    @TempDir
    Path root;

    @Test
    void nfr12_missingGoldenFailsWithoutUpdateFlag() {
        assertThrows(AssertionFailedError.class, () -> Golden.assertMatches(root, "x.txt", "a\n", false));
        assertFalse(Files.exists(root.resolve("x.txt")));
    }

    @Test
    void nfr12_differentGoldenFailsAndIsNotRewritten() throws IOException {
        Files.writeString(root.resolve("x.txt"), "old\n", StandardCharsets.UTF_8);
        assertThrows(AssertionFailedError.class, () -> Golden.assertMatches(root, "x.txt", "new\n", false));
        assertEquals("old\n", Files.readString(root.resolve("x.txt"), StandardCharsets.UTF_8));
    }

    @Test
    void nfr12_matchingGoldenPassesWithNormalisedLineEndings() throws IOException {
        Files.writeString(root.resolve("x.txt"), "a\r\nb\n", StandardCharsets.UTF_8);
        Golden.assertMatches(root, "x.txt", "a\nb\r\n", false);
    }

    @Test
    void nfr12_updateFlagWritesUtf8() throws IOException {
        Golden.assertMatches(root, "sub/x.txt", "kůň\n", true);
        assertEquals("kůň\n", Files.readString(root.resolve("sub/x.txt"), StandardCharsets.UTF_8));
    }

    @Test
    void nfr12_emptyGoldenIsNeverWritten() {
        assertThrows(AssertionFailedError.class, () -> Golden.assertMatches(root, "x.txt", "", true));
    }
}
