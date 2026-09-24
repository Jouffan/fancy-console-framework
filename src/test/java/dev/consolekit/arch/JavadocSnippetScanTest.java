package dev.consolekit.arch;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JavadocSnippetScanTest {

    private static final Pattern PUBLIC_TYPE = Pattern.compile(
        "(?m)^[ \\t]*public\\s+(?:(?:static|final|abstract|sealed|non-sealed|strictfp)\\s+)*"
            + "(?:class|interface|enum|record|@interface)\\s+(\\w+)");
    private static final Pattern ANNOTATIONS_ONLY = Pattern.compile("(?s)\\s*(?:@[\\w.]+(?:\\([^)]*\\))?\\s*)*");

    @Test
    void nfr1_everyPublicTypeHasJavadocWithASnippet() throws IOException {
        List<String> missing = new ArrayList<>();
        try (Stream<Path> files = Files.walk(JavaSource.MAIN)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                Matcher m = PUBLIC_TYPE.matcher(text);
                while (m.find()) {
                    if (!hasSnippetJavadoc(text, m.start())) missing.add(file + ": " + m.group(1));
                }
            }
        }
        assertEquals(List.of(), missing);
    }

    private static boolean hasSnippetJavadoc(String text, int declaration) {
        int end = text.lastIndexOf("*/", declaration);
        if (end < 0) return false;
        if (!ANNOTATIONS_ONLY.matcher(text.substring(end + 2, declaration)).matches()) return false;
        int start = text.lastIndexOf("/**", end);
        return start >= 0 && text.substring(start, end).contains("{@snippet");
    }
}
