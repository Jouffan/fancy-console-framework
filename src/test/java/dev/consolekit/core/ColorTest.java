package dev.consolekit.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ColorTest {

    @Test
    void cr40_sixteenNamedConstantsMatchNamedFactory() {
        Color[] constants = {
            Color.BLACK, Color.RED, Color.GREEN, Color.YELLOW,
            Color.BLUE, Color.MAGENTA, Color.CYAN, Color.WHITE,
            Color.BRIGHT_BLACK, Color.BRIGHT_RED, Color.BRIGHT_GREEN, Color.BRIGHT_YELLOW,
            Color.BRIGHT_BLUE, Color.BRIGHT_MAGENTA, Color.BRIGHT_CYAN, Color.BRIGHT_WHITE,
        };
        Color.Named[] names = Color.Named.values();
        assertEquals(16, names.length);
        for (int i = 0; i < names.length; i++) {
            assertSame(constants[i], Color.named(names[i]));
            assertEquals(Color.Kind.NAMED, constants[i].kind());
            assertEquals(i, constants[i].code());
        }
    }

    @Test
    void cr40_defaultIsAColourOfItsOwnKind() {
        assertEquals(Color.Kind.DEFAULT, Color.DEFAULT.kind());
        assertNotEquals(Color.DEFAULT, Color.BLACK);
        assertNotEquals(Color.DEFAULT, Color.WHITE);
    }

    @Test
    void cr40_indexAndRgbAreValues() {
        assertEquals(Color.index(42), Color.index(42));
        assertEquals(Color.rgb(1, 2, 3), Color.rgb(1, 2, 3));
        assertEquals(Color.rgb(1, 2, 3).hashCode(), Color.rgb(1, 2, 3).hashCode());
        assertNotEquals(Color.rgb(1, 2, 3), Color.rgb(1, 2, 4));
        assertEquals(1, Color.rgb(1, 2, 3).red());
        assertEquals(2, Color.rgb(1, 2, 3).green());
        assertEquals(3, Color.rgb(1, 2, 3).blue());
    }

    @Test
    void cr40_indexOutOfRangeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Color.index(-1));
        assertThrows(IllegalArgumentException.class, () -> Color.index(256));
    }

    @Test
    void cr40_rgbOutOfRangeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Color.rgb(256, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> Color.rgb(0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> Color.rgb(0, 0, 300));
    }

    @Test
    void cr40_namedRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> Color.named(null));
    }

    @Test
    void cr40_defaultHasNoPaletteCode() {
        assertThrows(IllegalStateException.class, Color.DEFAULT::code);
        assertThrows(IllegalStateException.class, Color.DEFAULT::red);
    }

    @Test
    void cr2_defaultDowngradesToItselfAtEveryDepth() {
        for (ColorDepth depth : ColorDepth.values()) {
            assertSame(Color.DEFAULT, Color.DEFAULT.downgrade(depth));
        }
    }

    @Test
    void cr2_everyColourDowngradesToDefaultAtNone() {
        assertSame(Color.DEFAULT, Color.RED.downgrade(ColorDepth.NONE));
        assertSame(Color.DEFAULT, Color.index(200).downgrade(ColorDepth.NONE));
        assertSame(Color.DEFAULT, Color.rgb(9, 9, 9).downgrade(ColorDepth.NONE));
    }

    @Test
    void cr2_truecolorIsKeptAtTruecolor() {
        assertEquals(Color.rgb(12, 34, 56), Color.rgb(12, 34, 56).downgrade(ColorDepth.TRUECOLOR));
    }

    @Test
    void cr2_truecolorQuantisesToTheCube() {
        assertEquals(Color.index(196), Color.rgb(255, 0, 0).downgrade(ColorDepth.ANSI256));
        assertEquals(Color.index(16), Color.rgb(0, 0, 0).downgrade(ColorDepth.ANSI256));
        assertEquals(Color.index(231), Color.rgb(255, 255, 255).downgrade(ColorDepth.ANSI256));
        assertEquals(Color.index(59), Color.rgb(100, 100, 100).downgrade(ColorDepth.ANSI256));
    }

    @Test
    void cr2_index256DowngradesToNearestNamed() {
        assertEquals(Color.BRIGHT_RED, Color.index(196).downgrade(ColorDepth.ANSI16));
        assertEquals(Color.BLACK, Color.index(16).downgrade(ColorDepth.ANSI16));
        assertEquals(Color.BRIGHT_WHITE, Color.index(231).downgrade(ColorDepth.ANSI16));
        assertEquals(Color.BLUE, Color.index(19).downgrade(ColorDepth.ANSI16));
    }

    @Test
    void cr2_lowIndexIsTheNamedColour() {
        assertEquals(Color.RED, Color.index(1).downgrade(ColorDepth.ANSI16));
        assertEquals(Color.BRIGHT_CYAN, Color.index(14).downgrade(ColorDepth.ANSI16));
    }

    @Test
    void cr2_rgbDowngradesToNearestNamed() {
        assertEquals(Color.BRIGHT_RED, Color.rgb(250, 10, 10).downgrade(ColorDepth.ANSI16));
        assertEquals(Color.GREEN, Color.rgb(0, 190, 10).downgrade(ColorDepth.ANSI16));
    }

    @Test
    void cr2_namedIsKeptAtEveryColourDepth() {
        assertSame(Color.MAGENTA, Color.MAGENTA.downgrade(ColorDepth.ANSI16));
        assertSame(Color.MAGENTA, Color.MAGENTA.downgrade(ColorDepth.ANSI256));
        assertSame(Color.MAGENTA, Color.MAGENTA.downgrade(ColorDepth.TRUECOLOR));
    }

    @Test
    void cr2_indexIsKeptAt256() {
        assertEquals(Color.index(123), Color.index(123).downgrade(ColorDepth.ANSI256));
    }
}
