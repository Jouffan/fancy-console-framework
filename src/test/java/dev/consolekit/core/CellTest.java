package dev.consolekit.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CellTest {

    @Test
    void cr41_transparentHasNoGlyphAndNoStyle() {
        assertFalse(Cell.TRANSPARENT.hasGlyph());
        assertEquals(Style.NONE, Cell.TRANSPARENT.style());
        assertEquals(0, Cell.TRANSPARENT.width());
    }

    @Test
    void cr41_ofCodePointHoldsGlyphAndStyle() {
        Cell c = Cell.of('#', Style.fg(Color.RED));
        assertTrue(c.hasGlyph());
        assertEquals("#", c.glyph());
        assertEquals(1, c.width());
        assertEquals(Style.fg(Color.RED), c.style());
    }

    @Test
    void cr41_styleOnlyCellHasNoGlyph() {
        Cell tint = Cell.of(Style.bg(Color.BLUE));
        assertFalse(tint.hasGlyph());
        assertEquals(Color.BLUE, tint.style().bg());
        assertThrows(IllegalStateException.class, tint::glyph);
    }

    @Test
    void cr41_cr6_wideGlyphRecordsWidthTwo() {
        assertEquals(2, Cell.of("漢", Style.NONE).width());
        assertEquals(2, Cell.of(0x6F22, Style.NONE).width());
        assertEquals(2, Cell.of("👩‍💻", Style.NONE).width());
    }

    @Test
    void cr41_cr34_decomposedGraphemeIsNormalised() {
        Cell c = Cell.of("é", Style.NONE);
        assertEquals("é", c.glyph());
        assertEquals(1, c.width());
    }

    @Test
    void cr41_moreThanOneGraphemeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Cell.of("ab", Style.NONE));
    }

    @Test
    void cr41_emptyGlyphIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Cell.of("", Style.NONE));
    }

    @Test
    void cr41_zeroWidthGlyphIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Cell.of(0x0301, Style.NONE));
    }

    @Test
    void cr9_controlCharacterIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Cell.of('\t', Style.NONE));
        assertThrows(IllegalArgumentException.class, () -> Cell.of(0x1B, Style.NONE));
        assertThrows(IllegalArgumentException.class, () -> Cell.of("\n", Style.NONE));
    }

    @Test
    void cr41_invalidCodePointIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Cell.of(0xD800, Style.NONE));
        assertThrows(IllegalArgumentException.class, () -> Cell.of(0x110000, Style.NONE));
    }

    @Test
    void cr41_nullArgumentsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> Cell.of("x", null));
        assertThrows(IllegalArgumentException.class, () -> Cell.of((String) null, Style.NONE));
        assertThrows(IllegalArgumentException.class, () -> Cell.of((Style) null));
    }

    @Test
    void cr41_cellsAreValues() {
        assertEquals(Cell.of('a', Style.NONE.bold()), Cell.of("a", Style.NONE.bold()));
        assertEquals(Cell.of('a', Style.NONE).hashCode(), Cell.of("a", Style.NONE).hashCode());
        assertNotEquals(Cell.of('a', Style.NONE), Cell.of('b', Style.NONE));
        assertEquals(Cell.TRANSPARENT, Cell.of(Style.NONE));
    }
}
