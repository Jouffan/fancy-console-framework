package dev.consolekit.arch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The scanners must catch what they claim to catch, or a green scan proves nothing. */
class ScansTest {

    private static JavaSource src(String pkg, String body) {
        String path = "src/main/java/" + pkg.replace('.', '/') + "/X.java";
        return JavaSource.parse(path, "package " + pkg + ";\n" + body);
    }

    @Test
    void cr43_importOfAnotherPartIsAViolation() {
        JavaSource s = src("dev.consolekit.core", "import dev.consolekit.canvas.FancyConsole;\nclass X {}");
        assertEquals(1, Scans.layeringViolations(s).size());
    }

    @Test
    void cr43_fullyQualifiedNameIsAViolation() {
        JavaSource s = src("dev.consolekit.bitmap", "class X { dev.consolekit.canvas.Rect r; }");
        assertEquals(1, Scans.layeringViolations(s).size());
    }

    @Test
    void cr43_qualifiedNameSplitByWhitespaceIsAViolation() {
        JavaSource s = src("dev.consolekit.bitmap", "class X { dev . consolekit\n . canvas . Rect r; }");
        assertEquals(1, Scans.layeringViolations(s).size());
    }

    @Test
    void cr43_unicodeEscapedNameIsAViolation() {
        JavaSource s = src("dev.consolekit.core", "class X { dev.consolekit.\\u0074ext.Text t; }");
        assertEquals(1, Scans.layeringViolations(s).size());
    }

    @Test
    void cr43_staticImportIsAViolation() {
        JavaSource s = src("dev.consolekit.core", "import static dev.consolekit.text.Text.red;\nclass X {}");
        assertEquals(1, Scans.layeringViolations(s).size());
    }

    @Test
    void cr43_commentsAndStringsAreNotReferences() {
        JavaSource s = src("dev.consolekit.core",
            "/** see dev.consolekit.canvas.Surface */\n// dev.consolekit.text.Text\n"
                + "class X { String s = \"dev.consolekit.canvas.Rect\"; String t = \"\"\"\n"
                + "   dev.consolekit.bitmap.Palette\n   \"\"\"; }");
        assertEquals(0, Scans.layeringViolations(s).size());
    }

    @Test
    void cr43_everyPartMayUseCoreAndCoreInternal() {
        for (String part : new String[] {"text", "bitmap", "canvas", "tools"}) {
            JavaSource s = src("dev.consolekit." + part,
                "import dev.consolekit.core.Cell;\nimport dev.consolekit.core.internal.Ansi;\nclass X {}");
            assertEquals(0, Scans.layeringViolations(s).size(), part);
        }
    }

    @Test
    void cr43_canvasMayUseBitmapButNotBitmapInternal() {
        assertEquals(0, Scans.layeringViolations(
            src("dev.consolekit.canvas.widget", "import dev.consolekit.bitmap.AsciiAnimation;\nclass X {}")).size());
        assertEquals(1, Scans.layeringViolations(
            src("dev.consolekit.canvas", "import dev.consolekit.bitmap.internal.ArtFormat;\nclass X {}")).size());
    }

    @Test
    void cr43_onlyToolsMayUseText() {
        assertEquals(1, Scans.layeringViolations(
            src("dev.consolekit.canvas", "import dev.consolekit.text.Text;\nclass X {}")).size());
        assertEquals(0, Scans.layeringViolations(
            src("dev.consolekit.tools", "import dev.consolekit.text.Text;\nclass X {}")).size());
    }

    @Test
    void cr43_nothingMayReferenceTools() {
        assertEquals(1, Scans.layeringViolations(
            src("dev.consolekit.core", "import dev.consolekit.tools.Probe;\nclass X {}")).size());
    }

    @Test
    void cr43_otherPartsInternalIsAViolation() {
        assertEquals(1, Scans.layeringViolations(
            src("dev.consolekit.bitmap", "import dev.consolekit.canvas.internal.TerminalPort;\nclass X {}")).size());
    }

    @Test
    void cr43_ownInternalIsAllowed() {
        assertEquals(0, Scans.layeringViolations(
            src("dev.consolekit.bitmap", "import dev.consolekit.bitmap.internal.ArtFormat;\nclass X {}")).size());
    }

    @Test
    void cr43_rootPackageIsNotAPart() {
        assertEquals(1, Scans.layeringViolations(src("dev.consolekit", "class X {}")).size());
        assertEquals(1, Scans.layeringViolations(src("dev.consolekit.render", "class X {}")).size());
    }

    @Test
    void cr22_jlineReferenceIsDetected() {
        assertTrue(Scans.referencesJline(src("dev.consolekit.core", "class X { org.jline.terminal.Terminal t; }")));
        assertFalse(Scans.referencesJline(src("dev.consolekit.core", "/** org.jline */ class X {}")));
    }

    @Test
    void cr25_loggingReferenceIsDetected() {
        assertTrue(Scans.referencesLogging(src("dev.consolekit.core", "import org.slf4j.Logger;\nclass X {}")));
        assertTrue(Scans.referencesLogging(
            src("dev.consolekit.core", "class X { Object l = System.getLogger(\"x\"); }")));
        assertFalse(Scans.referencesLogging(src("dev.consolekit.core", "class X { String s = \"org.slf4j\"; }")));
    }

    @Test
    void cr21_escapeInStringIsDetected() {
        assertTrue(Scans.containsEscape(src("dev.consolekit.core", "class X { String s = \"\\u001b[0m\"; }")));
        assertTrue(Scans.containsEscape(src("dev.consolekit.core", "class X { String s = \"\\033[0m\"; }")));
        assertTrue(Scans.containsEscape(src("dev.consolekit.core", "class X { char c = '\\33'; }")));
        assertTrue(Scans.containsEscape(src("dev.consolekit.core", "class X { char c = '\\u009b'; }")));
        assertTrue(Scans.containsEscape(
            src("dev.consolekit.core", "class X { String s = \"\"\"\n  \\033[\n  \"\"\"; }")));
    }

    @Test
    void cr21_escapeWrittenAsUnicodeEscapeOutsideLiteralIsDetected() {
        assertTrue(Scans.containsEscape(src("dev.consolekit.core", "class X { String s = \\u0022\\u001b\\u0022; }")));
    }

    @Test
    void cr21_charCastOf27InAnyRadixIsDetected() {
        for (String literal : new String[] {"27", "0x1B", "0x1b", "033", "0b11011", "0X001B"}) {
            assertTrue(Scans.containsEscape(src("dev.consolekit.core", "class X { char c = (char) " + literal + "; }")),
                literal);
        }
        assertFalse(Scans.containsEscape(src("dev.consolekit.core", "class X { char c = (char) 270; }")));
    }

    @Test
    void cr21_escapeInCommentIsExempt() {
        assertFalse(Scans.containsEscape(src("dev.consolekit.core", "/** \\u001b[0m resets */ class X {}")));
    }

    @Test
    void cr21_plainLiteralIsNotAnEscape() {
        assertFalse(Scans.containsEscape(src("dev.consolekit.core", "class X { String s = \"\\\\033\"; }")));
    }

    @Test
    void cr23_nonAsciiLiteralIsDetected() {
        assertTrue(Scans.containsNonAsciiLiteral(src("dev.consolekit.core", "class X { String s = \"─\"; }")));
        assertTrue(Scans.containsNonAsciiLiteral(src("dev.consolekit.core", "class X { String s = \"\\u2500\"; }")));
        assertTrue(Scans.containsNonAsciiLiteral(src("dev.consolekit.core", "class X { char c = '\\u00e9'; }")));
    }

    @Test
    void cr23_nonAsciiCommentIsExempt() {
        assertFalse(Scans.containsNonAsciiLiteral(src("dev.consolekit.core", "/** draws ─ */ class X {}")));
    }
}
