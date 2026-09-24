package dev.consolekit.core.internal;

import java.text.BreakIterator;

/**
 * Display width, measured in terminal columns per grapheme cluster, with escape sequences ignored.
 * {@code String.length()} is never a width.
 *
 * {@snippet :
 * int w = TextWidth.width("漢字 ok");                       // 7
 * String safe = TextWidth.sanitize("a\tb\u001b[31m", 0);   // "a       b"
 * }
 */
public final class TextWidth {

    /** Tab stops for expanded tabs. */
    public static final int TAB = 8;

    private static final char REPLACEMENT = '?';

    // East Asian Wide / Fullwidth and emoji-presentation blocks, as inclusive [start, end] pairs, sorted
    private static final int[] WIDE = {
        0x1100, 0x115F, 0x231A, 0x231B, 0x2329, 0x232A, 0x23E9, 0x23EC, 0x23F0, 0x23F0, 0x23F3, 0x23F3,
        0x25FD, 0x25FE, 0x2614, 0x2615, 0x2648, 0x2653, 0x267F, 0x267F, 0x2693, 0x2693, 0x26A1, 0x26A1,
        0x26AA, 0x26AB, 0x26BD, 0x26BE, 0x26C4, 0x26C5, 0x26CE, 0x26CE, 0x26D4, 0x26D4, 0x26EA, 0x26EA,
        0x26F2, 0x26F3, 0x26F5, 0x26F5, 0x26FA, 0x26FA, 0x26FD, 0x26FD, 0x2705, 0x2705, 0x270A, 0x270B,
        0x2728, 0x2728, 0x274C, 0x274C, 0x274E, 0x274E, 0x2753, 0x2755, 0x2757, 0x2757, 0x2795, 0x2797,
        0x27B0, 0x27B0, 0x27BF, 0x27BF, 0x2B1B, 0x2B1C, 0x2B50, 0x2B50, 0x2B55, 0x2B55, 0x2E80, 0x303E,
        0x3041, 0x33FF, 0x3400, 0x4DBF, 0x4E00, 0x9FFF, 0xA000, 0xA4CF, 0xA960, 0xA97F, 0xAC00, 0xD7A3,
        0xF900, 0xFAFF, 0xFE10, 0xFE19, 0xFE30, 0xFE6F, 0xFF00, 0xFF60, 0xFFE0, 0xFFE6,
        0x16FE0, 0x16FE4, 0x17000, 0x18AFF, 0x1B000, 0x1B16F, 0x1F004, 0x1F004, 0x1F0CF, 0x1F0CF,
        0x1F18E, 0x1F18E, 0x1F191, 0x1F19A, 0x1F200, 0x1F202, 0x1F210, 0x1F23B, 0x1F240, 0x1F248,
        0x1F250, 0x1F251, 0x1F260, 0x1F265, 0x1F300, 0x1F320, 0x1F32D, 0x1F335, 0x1F337, 0x1F37C,
        0x1F37E, 0x1F393, 0x1F3A0, 0x1F3CA, 0x1F3CF, 0x1F3D3, 0x1F3E0, 0x1F3F0, 0x1F3F4, 0x1F3F4,
        0x1F3F8, 0x1F43E, 0x1F440, 0x1F440, 0x1F442, 0x1F4FC, 0x1F4FF, 0x1F53D, 0x1F54B, 0x1F54E,
        0x1F550, 0x1F567, 0x1F57A, 0x1F57A, 0x1F595, 0x1F596, 0x1F5A4, 0x1F5A4, 0x1F5FB, 0x1F64F,
        0x1F680, 0x1F6C5, 0x1F6CC, 0x1F6CC, 0x1F6D0, 0x1F6D2, 0x1F6D5, 0x1F6D7, 0x1F6DC, 0x1F6DF,
        0x1F6EB, 0x1F6EC, 0x1F6F4, 0x1F6FC, 0x1F7E0, 0x1F7EB, 0x1F7F0, 0x1F7F0, 0x1F90C, 0x1F93A,
        0x1F93C, 0x1F945, 0x1F947, 0x1F9FF, 0x1FA70, 0x1FAFF, 0x20000, 0x2FFFD, 0x30000, 0x3FFFD,
    };

    private TextWidth() {
    }

    /** Columns one code point occupies on its own: 0 for controls and zero-width marks, else 1 or 2. */
    public static int codePointWidth(int cp) {
        if (cp < 0x20 || (cp >= 0x7F && cp < 0xA0)) return 0;
        if (cp < 0x300) return 1;
        if (isZeroWidth(cp)) return 0;
        return isWide(cp) ? 2 : 1;
    }

    private static boolean isZeroWidth(int cp) {
        if (cp == 0x200B || cp == 0x200C || cp == 0x200D || cp == 0x2060 || cp == 0xFEFF) return true;
        if (cp >= 0xFE00 && cp <= 0xFE0F) return true;
        if (cp >= 0xE0100 && cp <= 0xE01EF) return true;
        if (cp >= 0x1160 && cp <= 0x11FF) return true;
        int type = Character.getType(cp);
        return type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK
            || type == Character.FORMAT || type == Character.SURROGATE;
    }

    private static boolean isWide(int cp) {
        int lo = 0;
        int hi = WIDE.length / 2 - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (cp < WIDE[2 * mid]) {
                hi = mid - 1;
            } else if (cp > WIDE[2 * mid + 1]) {
                lo = mid + 1;
            } else {
                return true;
            }
        }
        return false;
    }

    /** Columns one grapheme cluster occupies. */
    public static int graphemeWidth(CharSequence cluster) {
        if (cluster.isEmpty()) return 0;
        int first = Character.codePointAt(cluster, 0);
        int width = codePointWidth(first);
        if (width == 2) return 2;
        int count = 0;
        for (int i = 0; i < cluster.length(); ) {
            int cp = Character.codePointAt(cluster, i);
            // an emoji presentation selector or a joined sequence renders as one wide emoji
            if (cp == 0xFE0F || cp == 0x200D) return 2;
            if (cp >= 0x1F1E6 && cp <= 0x1F1FF) count++;
            i += Character.charCount(cp);
        }
        return count >= 2 ? 2 : width;
    }

    /** Columns {@code s} occupies, counting graphemes and skipping escape sequences. */
    public static int width(CharSequence s) {
        String visible = stripEscapes(s);
        BreakIterator graphemes = BreakIterator.getCharacterInstance();
        graphemes.setText(visible);
        int width = 0;
        int start = graphemes.first();
        for (int end = graphemes.next(); end != BreakIterator.DONE; start = end, end = graphemes.next()) {
            width += graphemeWidth(visible.subSequence(start, end));
        }
        return width;
    }

    private static String stripEscapes(CharSequence s) {
        StringBuilder out = null;
        int i = 0;
        while (i < s.length()) {
            int escape = Ansi.escapeLength(s, i);
            if (escape > 0) {
                if (out == null) out = new StringBuilder(s.length()).append(s, 0, i);
                i += escape;
            } else {
                if (out != null) out.append(s.charAt(i));
                i++;
            }
        }
        return out == null ? s.toString() : out.toString();
    }

    /**
     * {@code s} made safe to place: escape sequences removed, tabs expanded to the next multiple of
     * {@link #TAB} counting from {@code startColumn}, line breaks turned into spaces, and other control
     * characters and lone surrogates replaced by {@code '?'}. Returns {@code s} itself when nothing changes.
     */
    public static String sanitize(String s, int startColumn) {
        if (isClean(s)) return s;
        String visible = stripEscapes(s);
        StringBuilder out = new StringBuilder(visible.length() + 8);
        for (int i = 0; i < visible.length(); i++) {
            char c = visible.charAt(i);
            if (c == '\t') {
                int column = startColumn + width(out);
                out.append(" ".repeat(TAB - column % TAB));
            } else if (c == '\r' && i + 1 < visible.length() && visible.charAt(i + 1) == '\n') {
                out.append(' ');
                i++;
            } else if (c == '\n' || c == '\r') {
                out.append(' ');
            } else if (c < 0x20 || (c >= 0x7F && c < 0xA0)) {
                out.append(REPLACEMENT);
            } else if (Character.isHighSurrogate(c) && i + 1 < visible.length()
                && Character.isLowSurrogate(visible.charAt(i + 1))) {
                out.append(c).append(visible.charAt(++i));
            } else if (Character.isSurrogate(c)) {
                out.append(REPLACEMENT);
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static boolean isClean(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x20 || (c >= 0x7F && c < 0xA0)) return false;
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= s.length() || !Character.isLowSurrogate(s.charAt(i + 1))) return false;
                i++;
            } else if (Character.isLowSurrogate(c)) {
                return false;
            }
        }
        return true;
    }
}
