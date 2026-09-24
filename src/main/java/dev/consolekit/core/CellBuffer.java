package dev.consolekit.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import dev.consolekit.core.internal.GraphemeTable;
import dev.consolekit.core.internal.TextWidth;

/**
 * A mutable, fixed-size grid of {@link Cell}s, and the only place cells are composited.
 *
 * <p>{@link #composite} applies the absence rule per channel: a present source glyph replaces the
 * destination glyph <em>and</em> its attributes; a present colour replaces that colour; anything absent
 * leaves the destination as it was. {@link #put} is the same rule for a single cell. A wide glyph occupies
 * its cell plus a continuation marker in the next column, is written only when both columns fit, and
 * writing over either half of an existing wide cell blanks the other half, so a wide cell is never split.
 *
 * <p>The representation is packed, so {@code put}, {@code fill}, {@code clear} and {@code composite} do
 * not allocate once warm. {@link #get} and {@link #dump} allocate and are for tests and diagnostics.
 * Out-of-range writes are skipped, never thrown.
 *
 * <p>A buffer has one owner and is not thread-safe.
 *
 * {@snippet :
 * CellBuffer back = CellBuffer.of(80, 24);
 * back.fill(Cell.of(' ', Style.fg(Color.DEFAULT).withBg(Color.DEFAULT)));   // opaque blank
 *
 * CellBuffer sprite = CellBuffer.of(3, 1);                                   // starts transparent
 * sprite.put(1, 0, Cell.of('@', Style.fg(Color.YELLOW)));
 *
 * back.composite(sprite, 10, 5, 0, 0, 80, 24);   // only the '@' lands; the rest shows through
 * String text = back.dump();
 * }
 */
public final class CellBuffer {

    private static final int ABSENT = 0;
    private static final int CONTINUATION = -1;
    private static final int SPACE = ' ';
    private static final int REPLACEMENT = '?';

    private final int width;
    private final int height;
    // >= 1 code point, 0 absent, -1 continuation of the wide cell to the left, <= -2 interned cluster id
    private final int[] glyphs;
    private final int[] fg;
    private final int[] bg;
    private final short[] attrs;

    private CellBuffer(int width, int height) {
        this.width = width;
        this.height = height;
        int size = Math.multiplyExact(width, height);
        this.glyphs = new int[size];
        this.fg = new int[size];
        this.bg = new int[size];
        this.attrs = new short[size];
    }

    /** A {@code w × h} buffer with every cell {@link Cell#TRANSPARENT}. */
    public static CellBuffer of(int w, int h) {
        if (w <= 0) throw new IllegalArgumentException("width must be > 0: " + w);
        if (h <= 0) throw new IllegalArgumentException("height must be > 0: " + h);
        return new CellBuffer(w, h);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** Every cell back to {@link Cell#TRANSPARENT}. */
    public void clear() {
        Arrays.fill(glyphs, ABSENT);
        Arrays.fill(fg, Color.ABSENT);
        Arrays.fill(bg, Color.ABSENT);
        Arrays.fill(attrs, (short) 0);
    }

    /**
     * Every cell set to exactly {@code c}, absent channels included; this is a starting fill, not a
     * composite. A wide glyph fills column pairs, and an odd last column gets a space in the same style.
     */
    public void fill(Cell c) {
        Style s = c.style();
        int glyph = glyphCode(c);
        int w = glyph == REPLACEMENT && c.codePoint() != REPLACEMENT ? 1 : c.width();
        short a = (short) s.attrBits();
        Arrays.fill(fg, s.fgPacked());
        Arrays.fill(bg, s.bgPacked());
        Arrays.fill(attrs, a);
        if (w < 2) {
            Arrays.fill(glyphs, glyph);
            return;
        }
        for (int y = 0; y < height; y++) {
            int row = y * width;
            int x = 0;
            for (; x + 1 < width; x += 2) {
                glyphs[row + x] = glyph;
                glyphs[row + x + 1] = CONTINUATION;
            }
            if (x < width) glyphs[row + x] = SPACE;
        }
    }

    /** {@code c} composited onto the cell at {@code (x, y)}; see the class description for the rule. */
    public void put(int x, int y, Cell c) {
        Style s = c.style();
        int glyph = glyphCode(c);
        if (glyph == REPLACEMENT && c.width() == 2 && c.codePoint() != REPLACEMENT) {
            // the cluster table is full: keep the width with two replacement characters
            if (x + 1 >= width) return;
            write(x, y, REPLACEMENT, 1, s.fgPacked(), s.bgPacked(), s.attrBits(), 0, 0, width, height);
            write(x + 1, y, REPLACEMENT, 1, s.fgPacked(), s.bgPacked(), s.attrBits(), 0, 0, width, height);
            return;
        }
        write(x, y, glyph, c.width(), s.fgPacked(), s.bgPacked(), s.attrBits(), 0, 0, width, height);
    }

    /**
     * The allocation-free form of {@link #put(int, int, Cell)} for one code point. A code point that cannot
     * stand in a cell on its own (a control character, a combining mark, a surrogate) is written as
     * {@code '?'}.
     */
    public void put(int x, int y, int codePoint, Style s) {
        int w = Character.isValidCodePoint(codePoint) ? TextWidth.codePointWidth(codePoint) : 0;
        int glyph = codePoint;
        if (w == 0) {
            glyph = REPLACEMENT;
            w = 1;
        }
        write(x, y, glyph, w, s.fgPacked(), s.bgPacked(), s.attrBits(), 0, 0, width, height);
    }

    /** {@code src} composited with its top-left at {@code (x, y)}, clipped to this buffer. */
    public void composite(CellBuffer src, int x, int y) {
        composite(src, x, y, 0, 0, width, height);
    }

    /**
     * {@code src} composited with its top-left at {@code (x, y)}. The clip rectangle is in this buffer's
     * coordinates: a destination cell is written only if it lies inside the clip and inside the buffer.
     */
    public void composite(CellBuffer src, int x, int y, int clipX, int clipY, int clipW, int clipH) {
        if (clipW <= 0 || clipH <= 0) return;
        int cx0 = Math.max(clipX, 0);
        int cy0 = Math.max(clipY, 0);
        int cx1 = (int) Math.min((long) clipX + clipW, width);
        int cy1 = (int) Math.min((long) clipY + clipH, height);
        if (cx0 >= cx1 || cy0 >= cy1) return;
        int syStart = (int) Math.max(0L, (long) cy0 - y);
        int syEnd = (int) Math.min(src.height, (long) cy1 - y);
        int sxStart = (int) Math.max(0L, (long) cx0 - x);
        int sxEnd = (int) Math.min(src.width, (long) cx1 - x);
        for (int sy = syStart; sy < syEnd; sy++) {
            int row = sy * src.width;
            for (int sx = sxStart; sx < sxEnd; sx++) {
                int si = row + sx;
                int glyph = src.glyphs[si];
                if (glyph == CONTINUATION) continue;
                write(x + sx, y + sy, glyph, glyphWidth(glyph), src.fg[si], src.bg[si], src.attrs[si],
                    cx0, cy0, cx1, cy1);
            }
        }
    }

    private void write(int x, int y, int glyph, int w, int fgColor, int bgColor, int attrBits,
                       int cx0, int cy0, int cx1, int cy1) {
        if (glyph == ABSENT && fgColor == Color.ABSENT && bgColor == Color.ABSENT) return;
        if (x < cx0 || x >= cx1 || y < cy0 || y >= cy1) return;
        if (w == 2 && x + 1 >= cx1) return;
        int i = y * width + x;
        if (glyph == ABSENT) {
            breakWide(x, i);
        } else {
            breakWide(x, i);
            if (w == 2) breakWide(x + 1, i + 1);
            glyphs[i] = glyph;
            attrs[i] = (short) attrBits;
        }
        if (fgColor != Color.ABSENT) fg[i] = fgColor;
        if (bgColor != Color.ABSENT) bg[i] = bgColor;
        if (w == 2) {
            glyphs[i + 1] = CONTINUATION;
            attrs[i + 1] = attrs[i];
            fg[i + 1] = fg[i];
            bg[i + 1] = bg[i];
        }
    }

    // never leave a wide cell split: touching either half blanks both, whatever the clip says
    private void breakWide(int x, int i) {
        if (glyphs[i] == CONTINUATION) {
            blank(i - 1);
            blank(i);
        } else if (x + 1 < width && glyphs[i + 1] == CONTINUATION) {
            blank(i);
            blank(i + 1);
        }
    }

    private void blank(int i) {
        glyphs[i] = SPACE;
        attrs[i] = 0;
    }

    private static int glyphCode(Cell c) {
        if (!c.hasGlyph()) return ABSENT;
        if (c.codePoint() > 0) return c.codePoint();
        return ConsoleRuntime.graphemes().intern(c.glyph());
    }

    private static int glyphWidth(int glyph) {
        if (glyph == ABSENT) return 0;
        if (glyph > 0) return TextWidth.codePointWidth(glyph);
        return ConsoleRuntime.graphemes().width(glyph);
    }

    private static String glyphString(int glyph) {
        return glyph > 0 ? Character.toString(glyph) : ConsoleRuntime.graphemes().cluster(glyph);
    }

    private int index(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            throw new IndexOutOfBoundsException("(" + x + ", " + y + ") outside " + width + "x" + height);
        }
        return y * width + x;
    }

    /**
     * The cell at {@code (x, y)}. For the right half of a wide cell this is a glyph-less cell carrying its
     * style; {@link #isContinuation} tells the two apart. Allocates; for tests and dumps.
     */
    public Cell get(int x, int y) {
        int i = index(x, y);
        int glyph = glyphs[i];
        Style style = Style.ofPacked(fg[i], bg[i], attrs[i]);
        if (glyph == ABSENT || glyph == CONTINUATION) return Cell.of(style);
        return Cell.of(glyphString(glyph), style);
    }

    /** Whether {@code (x, y)} is the right half of a wide cell. */
    public boolean isContinuation(int x, int y) {
        return glyphs[index(x, y)] == CONTINUATION;
    }

    /**
     * The glyphs as plain text, one {@code '\n'}-terminated line per row. An absent glyph is a space; a wide
     * glyph appears once and covers its continuation column.
     */
    public String dump() {
        StringBuilder out = new StringBuilder((width + 1) * height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int glyph = glyphs[y * width + x];
                if (glyph == CONTINUATION) continue;
                if (glyph == ABSENT) {
                    out.append(' ');
                } else if (glyph > 0) {
                    out.appendCodePoint(glyph);
                } else {
                    out.append(glyphString(glyph));
                }
            }
            out.append('\n');
        }
        return out.toString();
    }

    /**
     * The rows as print-door lines: an absent glyph becomes a space without attributes, unset colours stay
     * unset and so print as the terminal default.
     */
    public List<StyledText> toStyledText() {
        List<StyledText> lines = new ArrayList<>(height);
        for (int y = 0; y < height; y++) {
            StyledText.Builder line = StyledText.builder();
            StringBuilder run = new StringBuilder();
            Style runStyle = null;
            for (int x = 0; x < width; x++) {
                int i = y * width + x;
                int glyph = glyphs[i];
                if (glyph == CONTINUATION) continue;
                Style style = Style.ofPacked(fg[i], bg[i], glyph == ABSENT ? 0 : attrs[i]);
                if (runStyle != null && !style.equals(runStyle)) {
                    line.append(run.toString(), runStyle);
                    run.setLength(0);
                }
                runStyle = style;
                if (glyph == ABSENT) {
                    run.append(' ');
                } else {
                    run.append(glyphString(glyph));
                }
            }
            if (runStyle != null) line.append(run.toString(), runStyle);
            lines.add(line.build());
        }
        return List.copyOf(lines);
    }
}
