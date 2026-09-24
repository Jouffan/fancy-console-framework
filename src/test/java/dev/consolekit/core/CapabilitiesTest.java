package dev.consolekit.core;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CapabilitiesTest {

    private static final ConsoleOptions NO_OPTIONS = ConsoleOptions.builder().build();

    private static Capabilities.Inputs tty(String... env) {
        return inputs(true, "Linux", "UTF-8", env);
    }

    private static Capabilities.Inputs redirected(String... env) {
        return inputs(false, "Linux", "UTF-8", env);
    }

    private static Capabilities.Inputs inputs(boolean tty, String os, String charset, String... env) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < env.length; i += 2) {
            map.put(env[i], env[i + 1]);
        }
        return new Capabilities.Inputs(map, os, tty, charset, charset, charset, charset);
    }

    private static Capabilities resolve(Capabilities.Inputs in) {
        return Capabilities.resolve(NO_OPTIONS, in);
    }

    @Test
    void cr11_explicitConfigurationOutranksEnvironment() {
        ConsoleOptions options = ConsoleOptions.builder().colorDepth(ColorDepth.ANSI256).build();
        Capabilities caps = Capabilities.resolve(options, redirected("NO_COLOR", "1"));
        assertEquals(ColorDepth.ANSI256, caps.colorDepth());
        assertTrue(caps.canEmitEscapes());
    }

    @Test
    void cr3_noColorSuppressesAllColour() {
        Capabilities caps = resolve(tty("TERM", "xterm-256color", "NO_COLOR", "1"));
        assertEquals(ColorDepth.NONE, caps.colorDepth());
        assertTrue(caps.reason().contains("NO_COLOR"), caps.reason());
    }

    @Test
    void cr3_emptyNoColorIsIgnored() {
        assertEquals(ColorDepth.ANSI256, resolve(tty("TERM", "xterm-256color", "NO_COLOR", "")).colorDepth());
    }

    @Test
    void cr3_noColorOutranksForceColor() {
        Capabilities caps = resolve(tty("TERM", "xterm", "NO_COLOR", "1", "FORCE_COLOR", "3"));
        assertEquals(ColorDepth.NONE, caps.colorDepth());
    }

    @Test
    void cr3_forceColorForcesColourOnWhenRedirected() {
        Capabilities caps = resolve(redirected("FORCE_COLOR", "1"));
        assertTrue(caps.canEmitEscapes());
        assertEquals(ColorDepth.ANSI16, caps.colorDepth());
    }

    @Test
    void cr3_forceColorLevelSelectsDepth() {
        assertEquals(ColorDepth.ANSI256, resolve(redirected("FORCE_COLOR", "2")).colorDepth());
        assertEquals(ColorDepth.TRUECOLOR, resolve(redirected("FORCE_COLOR", "3")).colorDepth());
    }

    @Test
    void cr3_forceColorKeepsDetectedDepth() {
        Capabilities caps = resolve(redirected("FORCE_COLOR", "true", "COLORTERM", "truecolor"));
        assertEquals(ColorDepth.TRUECOLOR, caps.colorDepth());
    }

    @Test
    void cr3_cliColorForceForcesColourOn() {
        Capabilities caps = resolve(redirected("CLICOLOR_FORCE", "1", "TERM", "xterm-256color"));
        assertTrue(caps.canEmitEscapes());
        assertEquals(ColorDepth.ANSI256, caps.colorDepth());
    }

    @Test
    void cr3_forceColorZeroTurnsColourOff() {
        assertEquals(ColorDepth.NONE, resolve(tty("TERM", "xterm", "FORCE_COLOR", "0")).colorDepth());
    }

    @Test
    void cr13_redirectedOutputHasNoEscapes() {
        Capabilities caps = resolve(redirected("TERM", "xterm-256color", "COLORTERM", "truecolor"));
        assertFalse(caps.isTty());
        assertFalse(caps.canEmitEscapes());
        assertEquals(ColorDepth.NONE, caps.colorDepth());
        assertTrue(caps.attrs().isEmpty());
        assertFalse(caps.reason().isEmpty());
    }

    @Test
    void cr11_dumbTerminalHasNoEscapes() {
        Capabilities caps = resolve(tty("TERM", "dumb"));
        assertFalse(caps.canEmitEscapes());
        assertEquals(ColorDepth.NONE, caps.colorDepth());
    }

    @Test
    void cr11_termSelectsDepth() {
        assertEquals(ColorDepth.ANSI16, resolve(tty("TERM", "xterm")).colorDepth());
        assertEquals(ColorDepth.ANSI256, resolve(tty("TERM", "xterm-256color")).colorDepth());
        assertEquals(ColorDepth.TRUECOLOR, resolve(tty("TERM", "xterm-direct")).colorDepth());
    }

    @Test
    void cr11_colortermUpgradesToTruecolor() {
        assertEquals(ColorDepth.TRUECOLOR,
            resolve(tty("TERM", "xterm-256color", "COLORTERM", "truecolor")).colorDepth());
        assertEquals(ColorDepth.TRUECOLOR, resolve(tty("TERM", "xterm-256color", "COLORTERM", "24bit")).colorDepth());
    }

    @Test
    void cr11_termProgramUpgradesToTruecolor() {
        assertEquals(ColorDepth.TRUECOLOR,
            resolve(tty("TERM", "xterm-256color", "TERM_PROGRAM", "iTerm.app")).colorDepth());
        assertEquals(ColorDepth.TRUECOLOR,
            resolve(tty("TERM", "xterm-256color", "TERM_PROGRAM", "vscode")).colorDepth());
    }

    @Test
    void cr11_appleTerminalIsCappedAt256() {
        Capabilities caps =
            resolve(tty("TERM", "xterm-256color", "TERM_PROGRAM", "Apple_Terminal", "COLORTERM", "truecolor"));
        assertEquals(ColorDepth.ANSI256, caps.colorDepth());
    }

    @Test
    void cr11_linuxConsoleIsCappedAt16() {
        assertEquals(ColorDepth.ANSI16, resolve(tty("TERM", "linux", "COLORTERM", "truecolor")).colorDepth());
    }

    @Test
    void cr11_windowsTerminalIsTruecolor() {
        assertEquals(ColorDepth.TRUECOLOR,
            resolve(inputs(true, "Windows 11", "UTF-8", "WT_SESSION", "abc")).colorDepth());
    }

    @Test
    void cr11_windowsConsoleWithoutHintIsPlain() {
        Capabilities caps = resolve(inputs(true, "Windows 10", "IBM852"));
        assertFalse(caps.canEmitEscapes());
        assertTrue(caps.reason().contains("FORCE_COLOR"), caps.reason());
    }

    @Test
    void cr4_linuxConsoleDropsItalicAndStrike() {
        Capabilities caps = resolve(tty("TERM", "linux"));
        assertFalse(caps.supports(Attr.ITALIC));
        assertFalse(caps.supports(Attr.STRIKE));
        assertTrue(caps.supports(Attr.BOLD));
    }

    @Test
    void cr4_ordinaryTerminalSupportsEveryAttribute() {
        assertEquals(EnumSet.allOf(Attr.class), resolve(tty("TERM", "xterm-256color")).attrs());
    }

    @Test
    void cr3_noColorKeepsAttributesOnATerminal() {
        Capabilities caps = resolve(tty("TERM", "xterm", "NO_COLOR", "1"));
        assertTrue(caps.canEmitEscapes());
        assertTrue(caps.supports(Attr.BOLD));
    }

    @Test
    void cr12_probeTimeSizeComesFromEnvironment() {
        Capabilities caps = resolve(tty("TERM", "xterm", "COLUMNS", "120", "LINES", "40"));
        assertEquals(new Capabilities.Size(120, 40), caps.size());
        assertTrue(caps.size().isKnown());
    }

    @Test
    void cr12_sizeIsUnknownWhenEnvironmentDoesNotParse() {
        Capabilities caps = resolve(tty("TERM", "xterm", "COLUMNS", "wide"));
        assertFalse(caps.size().isKnown());
        assertEquals(Capabilities.Size.UNKNOWN, caps.size());
    }

    @Test
    void cr12_reasonIsEmptyWhenNothingDegraded() {
        assertEquals("", resolve(tty("TERM", "xterm-256color", "COLORTERM", "truecolor")).reason());
    }

    @Test
    void cr7_tierIsProbedFromOutputCharset() {
        assertEquals(GlyphTier.FULL, resolve(tty("TERM", "xterm")).glyphTier());
        Capabilities czech = resolve(inputs(true, "Windows 10", "IBM852"));
        assertEquals(GlyphTier.CP437, czech.glyphTier());
        assertTrue(czech.reason().contains("IBM852"), czech.reason());
        assertEquals(GlyphTier.ASCII, resolve(inputs(true, "Windows 10", "windows-1250")).glyphTier());
    }

    @Test
    void cr8_tierIsOverriddenByConfiguration() {
        ConsoleOptions options = ConsoleOptions.builder().glyphTier(GlyphTier.ASCII).build();
        assertEquals(GlyphTier.ASCII, Capabilities.resolve(options, tty("TERM", "xterm")).glyphTier());
    }

    @Test
    void cr8_tierIsOverriddenByEnvironment() {
        assertEquals(GlyphTier.CP437, resolve(tty("TERM", "xterm", "CONSOLEKIT_GLYPHS", "cp437")).glyphTier());
        assertEquals(GlyphTier.ASCII, resolve(tty("TERM", "xterm", "CONSOLEKIT_GLYPHS", "ASCII")).glyphTier());
    }

    @Test
    void cr8_unknownTierInEnvironmentIsIgnoredWithReason() {
        Capabilities caps = resolve(tty("TERM", "xterm", "CONSOLEKIT_GLYPHS", "fancy"));
        assertEquals(GlyphTier.FULL, caps.glyphTier());
        assertTrue(caps.reason().contains("CONSOLEKIT_GLYPHS"), caps.reason());
    }

    @Test
    void cr8_configurationOutranksEnvironment() {
        ConsoleOptions options = ConsoleOptions.builder().glyphTier(GlyphTier.FULL).build();
        assertEquals(GlyphTier.FULL,
            Capabilities.resolve(options, tty("TERM", "xterm", "CONSOLEKIT_GLYPHS", "ascii")).glyphTier());
    }

    @Test
    void cr28_outputAndInputCharsetsAreRecorded() {
        Capabilities.Inputs in = new Capabilities.Inputs(Map.of("TERM", "xterm"), "Linux", true,
            "IBM852", "windows-1250", "UTF-8", "UTF-8");
        Capabilities caps = resolve(in);
        assertEquals(Charset.forName("IBM852"), caps.outputCharset());
        assertEquals(Charset.forName("windows-1250"), caps.inputCharset());
    }

    @Test
    void cr28_redirectedOutputIsResolvedSeparately() {
        Capabilities.Inputs in = new Capabilities.Inputs(Map.of(), "Linux", false,
            "", "", "IBM852", "UTF-8");
        assertEquals(StandardCharsets.UTF_8, resolve(in).outputCharset());
    }

    @Test
    void cr12_builderDefaultsDescribeAModernTerminal() {
        Capabilities caps = Capabilities.builder().build();
        assertEquals(ColorDepth.TRUECOLOR, caps.colorDepth());
        assertEquals(GlyphTier.FULL, caps.glyphTier());
        assertTrue(caps.isTty());
        assertTrue(caps.canEmitEscapes());
        assertEquals(StandardCharsets.UTF_8, caps.outputCharset());
    }

    @Test
    void cr12_builderRejectsColourWithoutEscapes() {
        Capabilities.Builder b = Capabilities.builder().escapes(false);
        assertThrows(IllegalArgumentException.class, b::build);
    }

    @Test
    void cr11_detectReadsTheRealEnvironmentWithoutThrowing() {
        assertNotNull(Capabilities.detect(NO_OPTIONS));
    }
}
