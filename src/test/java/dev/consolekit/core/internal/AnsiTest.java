package dev.consolekit.core.internal;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;

import dev.consolekit.core.Attr;
import dev.consolekit.core.Capabilities;
import dev.consolekit.core.Color;
import dev.consolekit.core.ColorDepth;
import dev.consolekit.core.Style;
import dev.consolekit.core.StyledText;

import static org.junit.jupiter.api.Assertions.*;

class AnsiTest {

    private static Capabilities depth(ColorDepth depth) {
        return Capabilities.builder().colorDepth(depth).build();
    }

    private static final Capabilities PLAIN =
        Capabilities.builder().tty(false).escapes(false).colorDepth(ColorDepth.NONE).build();

    @Test
    void cr21_spanIsClosedWithSpecificOffCodes() {
        String s = Ansi.span("x", Style.fg(Color.RED).bold(), depth(ColorDepth.ANSI16));
        assertEquals("\u001b[1;31mx\u001b[22;39m", s);
    }

    @Test
    void cr21_everyAttributeHasItsOwnOffCode() {
        Style all = Style.NONE.bold().dim().italic().underline().reverse().strike();
        String s = Ansi.span("x", all, depth(ColorDepth.TRUECOLOR));
        assertEquals("\u001b[1;2;3;4;7;9mx\u001b[22;23;24;27;29m", s);
    }

    @Test
    void cr40_defaultEmitsSgr39And49() {
        String s = Ansi.span("x", Style.fg(Color.DEFAULT).withBg(Color.DEFAULT), depth(ColorDepth.ANSI16));
        assertEquals("\u001b[39;49mx\u001b[39;49m", s);
    }

    @Test
    void cr42_unsetChannelsEmitNothing() {
        assertEquals("x", Ansi.span("x", Style.NONE, depth(ColorDepth.TRUECOLOR)));
    }

    @Test
    void cr2_truecolorEmitsRgb() {
        String s = Ansi.span("x", Style.fg(Color.rgb(1, 2, 3)).withBg(Color.rgb(4, 5, 6)), depth(ColorDepth.TRUECOLOR));
        assertEquals("\u001b[38;2;1;2;3;48;2;4;5;6mx\u001b[39;49m", s);
    }

    @Test
    void cr2_rgbIsDowngradedTo256() {
        String s = Ansi.span("x", Style.fg(Color.rgb(255, 0, 0)), depth(ColorDepth.ANSI256));
        assertEquals("\u001b[38;5;196mx\u001b[39m", s);
    }

    @Test
    void cr2_brightNamedUsesAixtermCodes() {
        String s = Ansi.span("x", Style.fg(Color.BRIGHT_CYAN).withBg(Color.BRIGHT_BLACK), depth(ColorDepth.ANSI16));
        assertEquals("\u001b[96;100mx\u001b[39;49m", s);
    }

    @Test
    void cr3_noColourDepthKeepsAttributesOnly() {
        String s = Ansi.span("x", Style.fg(Color.RED).bold(), depth(ColorDepth.NONE));
        assertEquals("\u001b[1mx\u001b[22m", s);
    }

    @Test
    void cr13_noEscapesReturnsTextUnchanged() {
        assertEquals("x", Ansi.span("x", Style.fg(Color.RED).bold(), PLAIN));
    }

    @Test
    void cr4_unsupportedAttributeIsDropped() {
        Capabilities caps = Capabilities.builder().attrs(EnumSet.of(Attr.BOLD)).build();
        assertEquals("\u001b[1mx\u001b[22m", Ansi.span("x", Style.NONE.bold().italic(), caps));
        assertEquals("x", Ansi.span("x", Style.NONE.italic(), caps));
    }

    @Test
    void cr21_emptySpanEmitsNothing() {
        assertEquals("", Ansi.span("", Style.fg(Color.RED), depth(ColorDepth.ANSI16)));
    }

    @Test
    void cr44_lineConcatenatesSpans() {
        StyledText line = StyledText.builder().append("a", Style.fg(Color.RED)).append("b").build();
        assertEquals("\u001b[31ma\u001b[39mb", Ansi.line(line, depth(ColorDepth.ANSI16)));
        assertEquals("ab", Ansi.line(line, PLAIN));
    }

    @Test
    void cr6_escapeLengthRecognisesCsiOscAndShortSequences() {
        assertEquals(7, Ansi.escapeLength("\u001b[1;31mx", 0));
        assertEquals(6, Ansi.escapeLength("\u001b]0;t\u0007", 0));
        assertEquals(7, Ansi.escapeLength("\u001b]0;t\u001b\\", 0));
        assertEquals(2, Ansi.escapeLength("\u001bc", 0));
        assertEquals(4, Ansi.escapeLength("\u009b31m", 0));
        assertEquals(1, Ansi.escapeLength("\u001b", 0));
        assertEquals(0, Ansi.escapeLength("x", 0));
    }
}
