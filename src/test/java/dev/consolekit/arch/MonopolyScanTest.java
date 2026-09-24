package dev.consolekit.arch;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MonopolyScanTest {

    @Test
    void cr21_onlyAnsiContainsEscapeSequences() {
        List<String> offenders = JavaSource.mainSources().stream()
            .filter(Scans::containsEscape)
            .map(JavaSource::typeName)
            .filter(name -> !name.equals(Scans.ANSI))
            .toList();
        assertEquals(List.of(), offenders);
    }

    @Test
    void cr21_ansiIsTheEscapeEmitter() {
        assertTrue(JavaSource.mainSources().stream()
            .filter(s -> s.typeName().equals(Scans.ANSI))
            .anyMatch(Scans::containsEscape));
    }

    @Test
    void cr23_onlyGlyphsContainsNonAsciiLiterals() {
        List<String> offenders = JavaSource.mainSources().stream()
            .filter(Scans::containsNonAsciiLiteral)
            .map(JavaSource::typeName)
            .filter(name -> !name.equals(Scans.GLYPHS))
            .toList();
        assertEquals(List.of(), offenders);
    }
}
