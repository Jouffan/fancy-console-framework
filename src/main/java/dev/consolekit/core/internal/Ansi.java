package dev.consolekit.core.internal;

import dev.consolekit.core.Attr;
import dev.consolekit.core.Capabilities;
import dev.consolekit.core.Color;
import dev.consolekit.core.ColorDepth;
import dev.consolekit.core.Style;
import dev.consolekit.core.StyledText;

/**
 * The only class that writes escape sequences, and the one that recognises them in text.
 *
 * <p>Every span is opened with its own SGR parameters and closed with the matching specific "off" codes,
 * never a blanket reset, so styles around it survive. When the capabilities say escapes cannot render, the
 * text is returned unchanged.
 *
 * {@snippet :
 * String red = Ansi.span("error", Style.fg(Color.RED), ConsoleRuntime.capabilities());
 * String line = Ansi.line(StyledText.of("ok", Style.fg(Color.GREEN)), caps);
 * }
 */
public final class Ansi {

    private static final char ESC = '\u001b';
    private static final char BEL = '\u0007';
    private static final int CSI_C1 = 0x9B;
    private static final int OSC_C1 = 0x9D;
    private static final String CSI = "\u001b[";

    // SGR parameter order; the "off" codes come out in the same order
    private static final Attr[] ATTR_ORDER = {
        Attr.BOLD, Attr.DIM, Attr.ITALIC, Attr.UNDERLINE, Attr.REVERSE, Attr.STRIKE,
    };

    private Ansi() {
    }

    /** One line, spans in order, each opened and closed on its own. */
    public static String line(StyledText line, Capabilities caps) {
        StringBuilder out = new StringBuilder(line.text().length() + 16);
        for (StyledText.Span span : line.spans()) {
            appendSpan(out, span.text(), span.style(), caps);
        }
        return out.toString();
    }

    /** {@code text} wrapped in the SGR codes for {@code style}; the text itself is not inspected. */
    public static String span(String text, Style style, Capabilities caps) {
        StringBuilder out = new StringBuilder(text.length() + 16);
        appendSpan(out, text, style, caps);
        return out.toString();
    }

    private static void appendSpan(StringBuilder out, String text, Style style, Capabilities caps) {
        if (text.isEmpty()) return;
        if (!caps.canEmitEscapes()) {
            out.append(text);
            return;
        }
        ColorDepth depth = caps.colorDepth();
        boolean fg = style.hasFg() && depth != ColorDepth.NONE;
        boolean bg = style.hasBg() && depth != ColorDepth.NONE;
        StringBuilder open = new StringBuilder();
        StringBuilder close = new StringBuilder();
        boolean intensity = false;
        for (Attr attr : ATTR_ORDER) {
            if (!style.has(attr) || !caps.supports(attr)) continue;
            appendParam(open, openCode(attr));
            if (attr == Attr.BOLD || attr == Attr.DIM) {
                intensity = true;
            } else {
                appendParam(close, closeCode(attr));
            }
        }
        if (intensity) close.insert(0, close.isEmpty() ? "22" : "22;");
        if (fg) {
            appendColor(open, style.fg().downgrade(depth), false);
            appendParam(close, "39");
        }
        if (bg) {
            appendColor(open, style.bg().downgrade(depth), true);
            appendParam(close, "49");
        }
        if (open.isEmpty()) {
            out.append(text);
            return;
        }
        out.append(CSI).append(open).append('m').append(text).append(CSI).append(close).append('m');
    }

    private static String openCode(Attr attr) {
        return switch (attr) {
            case BOLD -> "1";
            case DIM -> "2";
            case ITALIC -> "3";
            case UNDERLINE -> "4";
            case REVERSE -> "7";
            case STRIKE -> "9";
        };
    }

    private static String closeCode(Attr attr) {
        return switch (attr) {
            case BOLD, DIM -> "22";
            case ITALIC -> "23";
            case UNDERLINE -> "24";
            case REVERSE -> "27";
            case STRIKE -> "29";
        };
    }

    private static void appendParam(StringBuilder params, String code) {
        if (!params.isEmpty()) params.append(';');
        params.append(code);
    }

    private static void appendColor(StringBuilder params, Color c, boolean background) {
        if (!params.isEmpty()) params.append(';');
        switch (c.kind()) {
            case DEFAULT -> params.append(background ? "49" : "39");
            case NAMED -> {
                int code = c.code();
                int base = code < 8 ? (background ? 40 : 30) : (background ? 100 : 90);
                params.append(base + code % 8);
            }
            case INDEX -> params.append(background ? "48;5;" : "38;5;").append(c.code());
            case RGB -> params.append(background ? "48;2;" : "38;2;")
                .append(c.red()).append(';').append(c.green()).append(';').append(c.blue());
        }
    }

    /**
     * The length of the escape sequence starting at {@code i}, or 0 if none starts there. Recognises CSI
     * (7-bit and C1), OSC and the other string sequences up to BEL or ST, and two-character escapes. A lone
     * trailing ESC has length 1.
     */
    public static int escapeLength(CharSequence s, int i) {
        int n = s.length();
        if (i >= n) return 0;
        char c = s.charAt(i);
        if (c == CSI_C1) return csiEnd(s, i + 1) - i;
        if (c == OSC_C1) return stringEnd(s, i + 1) - i;
        if (c != ESC) return 0;
        if (i + 1 >= n) return 1;
        char next = s.charAt(i + 1);
        if (next == '[') return csiEnd(s, i + 2) - i;
        if (next == ']' || next == 'P' || next == '_' || next == '^' || next == 'X') return stringEnd(s, i + 2) - i;
        int j = i + 1;
        while (j < n && s.charAt(j) >= 0x20 && s.charAt(j) <= 0x2F) j++;
        return Math.min(j + 1, n) - i;
    }

    private static int csiEnd(CharSequence s, int j) {
        int n = s.length();
        while (j < n && s.charAt(j) >= 0x20 && s.charAt(j) <= 0x3F) j++;
        return Math.min(j + 1, n);
    }

    private static int stringEnd(CharSequence s, int j) {
        int n = s.length();
        while (j < n) {
            char c = s.charAt(j);
            if (c == BEL) return j + 1;
            if (c == ESC && j + 1 < n && s.charAt(j + 1) == '\\') return j + 2;
            j++;
        }
        return n;
    }
}
