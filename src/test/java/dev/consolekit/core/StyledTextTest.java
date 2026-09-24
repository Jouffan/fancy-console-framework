package dev.consolekit.core;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StyledTextTest {

    @Test
    void cr42_spansKeepTheirOrderAndStyles() {
        StyledText line = StyledText.builder()
            .append("ok ", Style.fg(Color.GREEN))
            .append("done")
            .build();
        assertEquals(List.of(
            new StyledText.Span("ok ", Style.fg(Color.GREEN)),
            new StyledText.Span("done", Style.NONE)), line.spans());
        assertEquals("ok done", line.text());
    }

    @Test
    void cr6_widthIsDisplayWidth() {
        assertEquals(20, StyledText.of("příliš žluťoučký kůň").width());
        assertEquals(6, StyledText.builder().append("漢字").append("👍").build().width());
    }

    @Test
    void cr9_controlCharactersAreSanitised() {
        assertEquals("a b?c", StyledText.of("a\nb\u0007c").text());
        assertEquals("red", StyledText.of("\u001b[31mred\u001b[0m").text());
    }

    @Test
    void cr9_tabStopsCountAcrossSpans() {
        StyledText line = StyledText.builder().append("abc").append("\tx").build();
        assertEquals("abc     x", line.text());
    }

    @Test
    void cr42_emptyIsEmpty() {
        assertTrue(StyledText.EMPTY.isEmpty());
        assertEquals(0, StyledText.EMPTY.width());
        assertTrue(StyledText.of("").isEmpty());
    }

    @Test
    void cr35_clipCutsOnGraphemeBoundaries() {
        StyledText line = StyledText.builder().append("ab", Style.fg(Color.RED)).append("漢字").build();
        assertEquals("ab漢", line.clip(4).text());
        assertEquals("ab", line.clip(3).text());
        assertEquals("", line.clip(0).text());
        assertSame(line, line.clip(6));
    }

    @Test
    void cr35_clipNeverSplitsAZwjSequence() {
        StyledText line = StyledText.of("a👩‍💻b");
        assertEquals("a", line.clip(2).text());
        assertEquals("a👩‍💻", line.clip(3).text());
    }

    @Test
    void cr42_equalLinesAreEqual() {
        assertEquals(StyledText.of("x", Style.NONE.bold()), StyledText.of("x", Style.NONE.bold()));
        assertEquals(StyledText.of("x").hashCode(), StyledText.of("x").hashCode());
    }

    @Test
    void cr42_nullArgumentsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> StyledText.of((String) null));
        assertThrows(IllegalArgumentException.class, () -> StyledText.of("x", null));
        assertThrows(IllegalArgumentException.class, () -> new StyledText.Span(null, Style.NONE));
    }
}
