package dev.consolekit.core.internal;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import dev.consolekit.core.Capabilities;
import dev.consolekit.core.GlyphTier;

import static org.junit.jupiter.api.Assertions.*;

class EncodingTest {

    @Test
    void cr28_consoleOutputPrefersStdoutEncoding() {
        assertEquals(Charset.forName("IBM852"),
            Encoding.resolveOutput(true, "IBM852", "windows-1250", "UTF-8"));
    }

    @Test
    void cr28_consoleOutputFallsBackToConsoleCharsetThenNative() {
        assertEquals(Charset.forName("windows-1250"), Encoding.resolveOutput(true, "", "windows-1250", "UTF-8"));
        assertEquals(StandardCharsets.ISO_8859_1, Encoding.resolveOutput(true, "", "", "ISO-8859-1"));
    }

    @Test
    void cr28_redirectedOutputIgnoresConsoleCharset() {
        assertEquals(StandardCharsets.UTF_8, Encoding.resolveOutput(false, "", "IBM852", "UTF-8"));
        assertEquals(Charset.forName("IBM852"), Encoding.resolveOutput(false, "IBM852", "windows-1250", "UTF-8"));
    }

    @Test
    void cr28_unknownCharsetNameIsSkipped() {
        assertEquals(StandardCharsets.UTF_8, Encoding.resolveOutput(true, "no-such-charset", "bad name!", "UTF-8"));
    }

    @Test
    void cr28_neverAssumesUtf8() {
        assertEquals(StandardCharsets.US_ASCII, Encoding.resolveOutput(true, "", "", ""));
        assertEquals(StandardCharsets.US_ASCII, Encoding.resolveInput("", "", ""));
    }

    @Test
    void cr28_inputPrefersStdinEncoding() {
        assertEquals(Charset.forName("IBM852"), Encoding.resolveInput("IBM852", "UTF-8", "UTF-8"));
        assertEquals(Charset.forName("windows-1250"), Encoding.resolveInput("", "windows-1250", "UTF-8"));
    }

    @Test
    void cr7_utf8ProbesFull() {
        assertEquals(GlyphTier.FULL, Encoding.probeTier(StandardCharsets.UTF_8));
    }

    @Test
    void cr7_codePage437ProbesCp437() {
        assertEquals(GlyphTier.CP437, Encoding.probeTier(Charset.forName("IBM437")));
    }

    @Test
    void cr7_czechConsoleCodePageProbesCp437() {
        assertEquals(GlyphTier.CP437, Encoding.probeTier(Charset.forName("IBM852")));
    }

    @Test
    void cr7_ansiCodePageProbesAscii() {
        assertEquals(GlyphTier.ASCII, Encoding.probeTier(Charset.forName("windows-1250")));
        assertEquals(GlyphTier.ASCII, Encoding.probeTier(StandardCharsets.US_ASCII));
    }

    @Test
    void cr28_forOutputUsesRecordedCharset() {
        Capabilities caps = Capabilities.builder().outputCharset(StandardCharsets.US_ASCII).build();
        Encoding encoding = Encoding.forOutput(caps);
        assertEquals(StandardCharsets.US_ASCII, encoding.charset());
        assertTrue(encoding.canEncode("plain"));
        assertFalse(encoding.canEncode("kůň"));
    }
}
