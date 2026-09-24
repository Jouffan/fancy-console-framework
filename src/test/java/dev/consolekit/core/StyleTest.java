package dev.consolekit.core;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StyleTest {

    @Test
    void cr1_noneHasNoChannelsAndNoAttributes() {
        assertFalse(Style.NONE.hasFg());
        assertFalse(Style.NONE.hasBg());
        assertTrue(Style.NONE.attrs().isEmpty());
    }

    @Test
    void cr1_unsetChannelIsNotDefault() {
        Style explicitDefault = Style.fg(Color.DEFAULT);
        assertTrue(explicitDefault.hasFg());
        assertEquals(Color.DEFAULT, explicitDefault.fg());
        assertNotEquals(Style.NONE, explicitDefault);
    }

    @Test
    void cr1_readingAnUnsetChannelFails() {
        assertThrows(IllegalStateException.class, Style.NONE::fg);
        assertThrows(IllegalStateException.class, Style.NONE::bg);
    }

    @Test
    void cr1_fgOrFallsBackOnlyWhenUnset() {
        assertEquals(Color.DEFAULT, Style.NONE.fgOr(Color.DEFAULT));
        assertEquals(Color.RED, Style.fg(Color.RED).fgOr(Color.DEFAULT));
        assertEquals(Color.BLUE, Style.bg(Color.BLUE).bgOr(Color.DEFAULT));
    }

    @Test
    void cr1_attributesAccumulate() {
        Style s = Style.fg(Color.RED).bold().italic().underline().strike().reverse().dim();
        assertEquals(EnumSet.allOf(Attr.class), s.attrs());
        assertTrue(s.has(Attr.BOLD));
        assertEquals(Color.RED, s.fg());
    }

    @Test
    void cr1_styleIsImmutable() {
        Style base = Style.fg(Color.RED);
        Style changed = base.withBg(Color.BLUE).bold();
        assertFalse(base.hasBg());
        assertFalse(base.has(Attr.BOLD));
        assertEquals(Color.BLUE, changed.bg());
        assertEquals(Color.RED, changed.fg());
    }

    @Test
    void cr1_withoutAttributeRemovesIt() {
        assertFalse(Style.NONE.bold().without(Attr.BOLD).has(Attr.BOLD));
    }

    @Test
    void cr1_equalStylesAreEqual() {
        assertEquals(Style.fg(Color.RED).bold(), Style.NONE.bold().withFg(Color.RED));
        assertEquals(Style.fg(Color.RED).bold().hashCode(), Style.NONE.bold().withFg(Color.RED).hashCode());
    }

    @Test
    void cr1_nullColourIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Style.fg(null));
        assertThrows(IllegalArgumentException.class, () -> Style.bg(null));
        assertThrows(IllegalArgumentException.class, () -> Style.NONE.withFg(null));
        assertThrows(IllegalArgumentException.class, () -> Style.NONE.with(null));
    }
}
