package dev.consolekit.core;

import java.util.EnumSet;
import java.util.Set;

/**
 * Foreground colour, background colour and attribute flags, as an immutable value.
 *
 * <p>Each colour channel is either <em>unset</em> or holds a {@link Color}. Unset is not
 * {@link Color#DEFAULT}: printed, an unset channel is the terminal default; painted over other cells, it
 * keeps whatever colour is underneath.
 *
 * {@snippet :
 * Style error = Style.fg(Color.RED).bold();
 * Style tint = Style.bg(Color.BLUE);                 // foreground unset
 * Style erase = Style.bg(Color.DEFAULT);             // opaque terminal default
 * Style both = error.withBg(Color.BRIGHT_WHITE).underline();
 * }
 */
public final class Style {

    public static final Style NONE = new Style(Color.ABSENT, Color.ABSENT, 0);

    private final int fg;
    private final int bg;
    private final int attrs;

    private Style(int fg, int bg, int attrs) {
        this.fg = fg;
        this.bg = bg;
        this.attrs = attrs;
    }

    static Style ofPacked(int fg, int bg, int attrs) {
        if (fg == Color.ABSENT && bg == Color.ABSENT && attrs == 0) return NONE;
        return new Style(fg, bg, attrs);
    }

    public static Style fg(Color color) {
        return NONE.withFg(color);
    }

    public static Style bg(Color color) {
        return NONE.withBg(color);
    }

    public Style withFg(Color color) {
        if (color == null) throw new IllegalArgumentException("color must not be null");
        return new Style(color.packed(), bg, attrs);
    }

    public Style withBg(Color color) {
        if (color == null) throw new IllegalArgumentException("color must not be null");
        return new Style(fg, color.packed(), attrs);
    }

    public Style withoutFg() {
        return ofPacked(Color.ABSENT, bg, attrs);
    }

    public Style withoutBg() {
        return ofPacked(fg, Color.ABSENT, attrs);
    }

    public Style with(Attr attr) {
        if (attr == null) throw new IllegalArgumentException("attr must not be null");
        return new Style(fg, bg, attrs | attr.bit());
    }

    public Style without(Attr attr) {
        if (attr == null) throw new IllegalArgumentException("attr must not be null");
        return ofPacked(fg, bg, attrs & ~attr.bit());
    }

    public Style bold() {
        return with(Attr.BOLD);
    }

    public Style dim() {
        return with(Attr.DIM);
    }

    public Style italic() {
        return with(Attr.ITALIC);
    }

    public Style underline() {
        return with(Attr.UNDERLINE);
    }

    public Style strike() {
        return with(Attr.STRIKE);
    }

    public Style reverse() {
        return with(Attr.REVERSE);
    }

    public boolean hasFg() {
        return fg != Color.ABSENT;
    }

    public boolean hasBg() {
        return bg != Color.ABSENT;
    }

    /** The foreground colour; fails if the channel is unset (see {@link #fgOr(Color)}). */
    public Color fg() {
        if (fg == Color.ABSENT) throw new IllegalStateException("foreground is unset");
        return Color.unpack(fg);
    }

    /** The background colour; fails if the channel is unset (see {@link #bgOr(Color)}). */
    public Color bg() {
        if (bg == Color.ABSENT) throw new IllegalStateException("background is unset");
        return Color.unpack(bg);
    }

    public Color fgOr(Color fallback) {
        return fg == Color.ABSENT ? fallback : Color.unpack(fg);
    }

    public Color bgOr(Color fallback) {
        return bg == Color.ABSENT ? fallback : Color.unpack(bg);
    }

    public boolean has(Attr attr) {
        return (attrs & attr.bit()) != 0;
    }

    public Set<Attr> attrs() {
        EnumSet<Attr> set = EnumSet.noneOf(Attr.class);
        for (Attr a : Attr.values()) {
            if (has(a)) set.add(a);
        }
        return set;
    }

    int fgPacked() {
        return fg;
    }

    int bgPacked() {
        return bg;
    }

    int attrBits() {
        return attrs;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Style other && other.fg == fg && other.bg == bg && other.attrs == attrs;
    }

    @Override
    public int hashCode() {
        return (fg * 31 + bg) * 31 + attrs;
    }

    @Override
    public String toString() {
        if (equals(NONE)) return "Style.NONE";
        return "Style[fg=" + (hasFg() ? fg() : "unset") + ", bg=" + (hasBg() ? bg() : "unset") + ", attrs=" + attrs()
            + "]";
    }
}
