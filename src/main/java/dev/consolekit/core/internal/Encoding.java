package dev.consolekit.core.internal;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;

import dev.consolekit.core.Capabilities;
import dev.consolekit.core.GlyphTier;

/**
 * Charset resolution and the glyph side of encoding: which charset the output really uses, and which glyph
 * tier that charset can encode. Content substitution (transliterate, then replace) is not here yet.
 *
 * {@snippet :
 * Encoding out = Encoding.forOutput(ConsoleRuntime.capabilities());
 * if (!out.canEncode("kůň")) {
 *     // the console would mangle it
 * }
 * }
 */
public final class Encoding {

    private final Charset charset;

    private Encoding(Charset charset) {
        this.charset = charset;
    }

    public static Encoding forOutput(Capabilities caps) {
        return new Encoding(caps.outputCharset());
    }

    public Charset charset() {
        return charset;
    }

    public boolean canEncode(CharSequence s) {
        return charset.canEncode() && charset.newEncoder().canEncode(s);
    }

    /**
     * The output charset. A terminal takes {@code stdout.encoding}, then {@code Console.charset()}, then
     * {@code native.encoding}; redirected output skips the console, which is not where the bytes go. Blank or
     * unknown names are skipped, and if nothing resolves the answer is US-ASCII, never an assumed UTF-8.
     */
    public static Charset resolveOutput(boolean tty, String stdoutEncoding, String consoleCharset,
                                        String nativeEncoding) {
        return tty
            ? first(stdoutEncoding, consoleCharset, nativeEncoding)
            : first(stdoutEncoding, "", nativeEncoding);
    }

    /** The input charset: {@code stdin.encoding}, then {@code Console.charset()}, then {@code native.encoding}. */
    public static Charset resolveInput(String stdinEncoding, String consoleCharset, String nativeEncoding) {
        return first(stdinEncoding, consoleCharset, nativeEncoding);
    }

    private static Charset first(String a, String b, String c) {
        Charset found = lookup(a);
        if (found == null) found = lookup(b);
        if (found == null) found = lookup(c);
        return found == null ? StandardCharsets.US_ASCII : found;
    }

    private static Charset lookup(String name) {
        if (name == null || name.isBlank()) return null;
        try {
            return Charset.forName(name.trim());
        } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
            return null;
        }
    }

    /** The richest tier every one of whose glyphs {@code charset} can encode. */
    public static GlyphTier probeTier(Charset charset) {
        if (!charset.canEncode()) return GlyphTier.ASCII;
        CharsetEncoder encoder = charset.newEncoder();
        if (encodesAll(encoder, GlyphTier.FULL)) return GlyphTier.FULL;
        if (encodesAll(encoder, GlyphTier.CP437)) return GlyphTier.CP437;
        return GlyphTier.ASCII;
    }

    /** The first glyph of {@code tier} that {@code charset} cannot encode, or {@code ""} if there is none. */
    public static String firstUnencodable(Charset charset, GlyphTier tier) {
        if (!charset.canEncode()) return Glyphs.candidateGlyphs(tier).get(0);
        CharsetEncoder encoder = charset.newEncoder();
        for (String glyph : Glyphs.candidateGlyphs(tier)) {
            if (!encoder.canEncode(glyph)) return glyph;
        }
        return "";
    }

    private static boolean encodesAll(CharsetEncoder encoder, GlyphTier tier) {
        for (String glyph : Glyphs.candidateGlyphs(tier)) {
            if (!encoder.canEncode(glyph)) return false;
        }
        return true;
    }
}
