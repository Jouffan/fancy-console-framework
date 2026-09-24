package dev.consolekit.core;

import java.text.BreakIterator;
import java.text.Normalizer;

import dev.consolekit.core.internal.TextWidth;

/**
 * One character position as a value: one grapheme plus one {@link Style}.
 *
 * <p>The glyph, the foreground and the background are each independently <em>absent</em>, and absent means
 * "leave what is underneath" when the cell is composited ({@link CellBuffer#composite}). Attributes travel
 * with the glyph: on a cell without a glyph they have no effect. {@link #TRANSPARENT} is absent everything.
 *
 * <p>Construction validates that the glyph is exactly one grapheme (after NFC) of width 1 or 2.
 *
 * {@snippet :
 * Cell hash = Cell.of('#', Style.fg(Color.RED));
 * Cell accent = Cell.of("é", Style.NONE.bold());
 * Cell tint = Cell.of(Style.bg(Color.BLUE));   // no glyph: recolours what is underneath
 * Cell hole = Cell.TRANSPARENT;
 * }
 */
public final class Cell {

    public static final Cell TRANSPARENT = new Cell("", 0, 0, Style.NONE);

    private final String glyph;
    // the code point when the glyph is exactly one, 0 when absent, -1 for a multi-code-point cluster
    private final int codePoint;
    private final int width;
    private final Style style;

    private Cell(String glyph, int codePoint, int width, Style style) {
        this.glyph = glyph;
        this.codePoint = codePoint;
        this.width = width;
        this.style = style;
    }

    public static Cell of(int codePoint, Style style) {
        if (!Character.isValidCodePoint(codePoint)) {
            throw new IllegalArgumentException("not a code point: " + codePoint);
        }
        if (style == null) throw new IllegalArgumentException("style must not be null");
        return of(Character.toString(codePoint), style);
    }

    public static Cell of(String grapheme, Style style) {
        if (grapheme == null) throw new IllegalArgumentException("grapheme must not be null");
        if (style == null) throw new IllegalArgumentException("style must not be null");
        String nfc = Normalizer.normalize(grapheme, Normalizer.Form.NFC);
        if (nfc.isEmpty()) throw new IllegalArgumentException("grapheme must not be empty");
        BreakIterator graphemes = BreakIterator.getCharacterInstance();
        graphemes.setText(nfc);
        graphemes.first();
        if (graphemes.next() != nfc.length()) {
            throw new IllegalArgumentException("must be exactly one grapheme: \"" + escape(nfc) + "\"");
        }
        for (int i = 0; i < nfc.length(); i++) {
            char c = nfc.charAt(i);
            if (c < 0x20 || (c >= 0x7F && c < 0xA0)) {
                throw new IllegalArgumentException("control character: U+" + hex(c));
            }
            if (Character.isSurrogate(c) && !(Character.isHighSurrogate(c) && i + 1 < nfc.length()
                && Character.isLowSurrogate(nfc.charAt(++i)))) {
                throw new IllegalArgumentException("lone surrogate: U+" + hex(c));
            }
        }
        int width = TextWidth.graphemeWidth(nfc);
        if (width < 1 || width > 2) {
            throw new IllegalArgumentException("glyph must be 1 or 2 columns wide: \"" + escape(nfc) + "\"");
        }
        int single = nfc.codePointCount(0, nfc.length()) == 1 ? nfc.codePointAt(0) : -1;
        return new Cell(nfc, single, width, style);
    }

    /** A cell with no glyph: only the style's colour channels have an effect. */
    public static Cell of(Style style) {
        if (style == null) throw new IllegalArgumentException("style must not be null");
        if (style.equals(Style.NONE)) return TRANSPARENT;
        return new Cell("", 0, 0, style);
    }

    private static String hex(int c) {
        return String.format("%04X", c);
    }

    private static String escape(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            if (cp >= 0x20 && cp < 0x7F) {
                out.appendCodePoint(cp);
            } else {
                out.append("\\u{").append(hex(cp)).append('}');
            }
            i += Character.charCount(cp);
        }
        return out.toString();
    }

    public boolean hasGlyph() {
        return !glyph.isEmpty();
    }

    /** The grapheme; fails if the glyph is absent. */
    public String glyph() {
        if (glyph.isEmpty()) throw new IllegalStateException("glyph is absent");
        return glyph;
    }

    /** Columns the glyph occupies: 0 when absent, else 1 or 2. */
    public int width() {
        return width;
    }

    public Style style() {
        return style;
    }

    int codePoint() {
        return codePoint;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Cell other && other.glyph.equals(glyph) && other.style.equals(style);
    }

    @Override
    public int hashCode() {
        return glyph.hashCode() * 31 + style.hashCode();
    }

    @Override
    public String toString() {
        return hasGlyph() ? "Cell[\"" + escape(glyph) + "\", " + style + "]" : "Cell[absent, " + style + "]";
    }
}
