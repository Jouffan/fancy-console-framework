package dev.consolekit.core.internal;

import java.util.ArrayList;
import java.util.List;

import dev.consolekit.core.GlyphTier;

/**
 * The only class holding non-ASCII literals: the library's own glyphs per tier, and the standard width
 * fixtures.
 *
 * <p>The {@code CP437} tier is deliberately the single-line box drawing and shade blocks only, because
 * those are shared by the OEM code pages consoles actually run (437, 850, 852, 866). Anything outside that
 * set falls back to ASCII in that tier.
 *
 * {@snippet :
 * GlyphTier tier = ConsoleRuntime.capabilities().glyphTier();
 * String rule = Glyphs.glyph(Glyphs.Glyph.H_LINE, tier).repeat(20);
 * }
 */
public final class Glyphs {

    /** The standard alignment fixture: twenty columns of Czech with diacritics. */
    public static final String FIXTURE_CZECH = "příliš žluťoučký kůň";
    /** Five CJK characters, ten columns. */
    public static final String FIXTURE_CJK = "漢字テスト";
    /** One emoji, two columns. */
    public static final String FIXTURE_EMOJI = "👍";
    /** A ZWJ sequence: one grapheme, two columns, three code points. */
    public static final String FIXTURE_ZWJ = "👩‍💻";

    /**
     * A glyph the library draws, with a form for every tier.
     *
     * {@snippet :
     * String corner = Glyphs.glyph(Glyphs.Glyph.TOP_LEFT, GlyphTier.ASCII);   // "+"
     * }
     */
    public enum Glyph {
        H_LINE("─", "─", "-"),
        V_LINE("│", "│", "|"),
        TOP_LEFT("┌", "┌", "+"),
        TOP_RIGHT("┐", "┐", "+"),
        BOTTOM_LEFT("└", "└", "+"),
        BOTTOM_RIGHT("┘", "┘", "+"),
        TEE_DOWN("┬", "┬", "+"),
        TEE_UP("┴", "┴", "+"),
        TEE_RIGHT("├", "├", "+"),
        TEE_LEFT("┤", "┤", "+"),
        CROSS("┼", "┼", "+"),
        FULL_BLOCK("█", "█", "#"),
        DARK_SHADE("▓", "▓", "%"),
        MEDIUM_SHADE("▒", "▒", ":"),
        LIGHT_SHADE("░", "░", "."),
        BULLET("•", "*", "*"),
        ELLIPSIS("…", "...", "..."),
        CHECK("✓", "v", "v"),
        CROSS_MARK("✗", "x", "x"),
        ARROW_RIGHT("→", ">", ">");

        private final String full;
        private final String cp437;
        private final String ascii;

        Glyph(String full, String cp437, String ascii) {
            this.full = full;
            this.cp437 = cp437;
            this.ascii = ascii;
        }

        public String in(GlyphTier tier) {
            return switch (tier) {
                case FULL -> full;
                case CP437 -> cp437;
                case ASCII -> ascii;
            };
        }
    }

    private Glyphs() {
    }

    public static String glyph(Glyph glyph, GlyphTier tier) {
        return glyph.in(tier);
    }

    /** Every glyph the tier uses, each once; the tier is usable only if the charset encodes all of them. */
    public static List<String> candidateGlyphs(GlyphTier tier) {
        List<String> glyphs = new ArrayList<>();
        for (Glyph g : Glyph.values()) {
            String s = g.in(tier);
            if (!glyphs.contains(s)) glyphs.add(s);
        }
        return List.copyOf(glyphs);
    }
}
