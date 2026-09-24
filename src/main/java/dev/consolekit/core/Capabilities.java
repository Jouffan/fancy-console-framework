package dev.consolekit.core;

import java.io.Console;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import dev.consolekit.core.internal.Encoding;

/**
 * What the output can show, resolved once per process from the JDK and the environment only: colour depth,
 * glyph tier, TTY-ness, whether escapes can be emitted at all, supported attributes, the output and input
 * charsets, a probe-time size, and a human-readable reason for anything that was degraded.
 *
 * <p>Precedence, highest first: explicit configuration, {@code NO_COLOR}, {@code FORCE_COLOR} /
 * {@code CLICOLOR_FORCE}, TTY-ness, {@code TERM}, {@code TERM_PROGRAM}, {@code WT_SESSION},
 * {@code COLORTERM}. The glyph tier is explicit configuration, then {@code CONSOLEKIT_GLYPHS}
 * ({@code full}, {@code cp437} or {@code ascii}), then a probe of the output charset.
 *
 * <p>{@link #size()} is diagnostic only. Layout uses the width in its {@link RenderContext}.
 *
 * {@snippet :
 * Capabilities caps = ConsoleRuntime.capabilities();
 * if (!caps.reason().isEmpty()) {
 *     System.err.println("degraded: " + caps.reason());
 * }
 *
 * // a fixed environment, for tests and goldens
 * Capabilities ansi16 = Capabilities.builder().colorDepth(ColorDepth.ANSI16).glyphTier(GlyphTier.CP437).build();
 * }
 */
public final class Capabilities {

    /**
     * Terminal size from {@code COLUMNS} / {@code LINES} at probe time; 0 on an axis that did not parse.
     *
     * {@snippet :
     * Capabilities.Size size = caps.size();
     * String shown = size.isKnown() ? size.columns() + "x" + size.rows() : "unknown";
     * }
     */
    public record Size(int columns, int rows) {
        public static final Size UNKNOWN = new Size(0, 0);

        public boolean isKnown() {
            return columns > 0 && rows > 0;
        }
    }

    /**
     * Builds a fixed set of capabilities. The defaults describe a modern UTF-8 truecolor terminal.
     *
     * {@snippet :
     * Capabilities redirected = Capabilities.builder()
     *     .tty(false).escapes(false).colorDepth(ColorDepth.NONE)
     *     .build();
     * }
     */
    public static final class Builder {

        private ColorDepth colorDepth = ColorDepth.TRUECOLOR;
        private GlyphTier glyphTier = GlyphTier.FULL;
        private boolean tty = true;
        private boolean escapes = true;
        private Set<Attr> attrs = EnumSet.allOf(Attr.class);
        private Size size = Size.UNKNOWN;
        private Charset outputCharset = StandardCharsets.UTF_8;
        private Charset inputCharset = StandardCharsets.UTF_8;
        private final List<String> reasons = new ArrayList<>();

        private Builder() {
        }

        public Builder colorDepth(ColorDepth colorDepth) {
            if (colorDepth == null) throw new IllegalArgumentException("colorDepth must not be null");
            this.colorDepth = colorDepth;
            return this;
        }

        public Builder glyphTier(GlyphTier glyphTier) {
            if (glyphTier == null) throw new IllegalArgumentException("glyphTier must not be null");
            this.glyphTier = glyphTier;
            return this;
        }

        public Builder tty(boolean tty) {
            this.tty = tty;
            return this;
        }

        public Builder escapes(boolean escapes) {
            this.escapes = escapes;
            return this;
        }

        public Builder attrs(Set<Attr> attrs) {
            if (attrs == null) throw new IllegalArgumentException("attrs must not be null");
            this.attrs = attrs.isEmpty() ? EnumSet.noneOf(Attr.class) : EnumSet.copyOf(attrs);
            return this;
        }

        public Builder size(Size size) {
            if (size == null) throw new IllegalArgumentException("size must not be null");
            this.size = size;
            return this;
        }

        public Builder outputCharset(Charset outputCharset) {
            if (outputCharset == null) throw new IllegalArgumentException("outputCharset must not be null");
            this.outputCharset = outputCharset;
            return this;
        }

        public Builder inputCharset(Charset inputCharset) {
            if (inputCharset == null) throw new IllegalArgumentException("inputCharset must not be null");
            this.inputCharset = inputCharset;
            return this;
        }

        public Builder reason(String reason) {
            if (reason == null) throw new IllegalArgumentException("reason must not be null");
            reasons.add(reason);
            return this;
        }

        public Capabilities build() {
            if (!escapes && colorDepth != ColorDepth.NONE) {
                throw new IllegalArgumentException("colorDepth must be NONE when escapes are off: " + colorDepth);
            }
            int bits = 0;
            if (escapes) {
                for (Attr a : attrs) bits |= a.bit();
            }
            return new Capabilities(colorDepth, glyphTier, tty, escapes, bits, size, outputCharset, inputCharset,
                List.copyOf(reasons));
        }
    }

    /** The raw environment one resolution reads; blank strings for anything missing. */
    record Inputs(Map<String, String> env, String osName, boolean tty, String stdoutEncoding,
                  String stdinEncoding, String consoleCharset, String nativeEncoding) {
    }

    private final ColorDepth colorDepth;
    private final GlyphTier glyphTier;
    private final boolean tty;
    private final boolean escapes;
    private final int attrs;
    private final Size size;
    private final Charset outputCharset;
    private final Charset inputCharset;
    private final List<String> reasons;

    private Capabilities(ColorDepth colorDepth, GlyphTier glyphTier, boolean tty, boolean escapes, int attrs,
                         Size size, Charset outputCharset, Charset inputCharset, List<String> reasons) {
        this.colorDepth = colorDepth;
        this.glyphTier = glyphTier;
        this.tty = tty;
        this.escapes = escapes;
        this.attrs = attrs;
        this.size = size;
        this.outputCharset = outputCharset;
        this.inputCharset = inputCharset;
        this.reasons = reasons;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Resolves from the real process environment. {@link ConsoleRuntime#capabilities()} caches this. */
    static Capabilities detect(ConsoleOptions options) {
        Console console = System.console();
        boolean tty = console != null && console.isTerminal();
        String consoleCharset = console != null ? console.charset().name() : "";
        Inputs in = new Inputs(System.getenv(), System.getProperty("os.name", ""), tty,
            System.getProperty("stdout.encoding", ""), System.getProperty("stdin.encoding", ""),
            consoleCharset, System.getProperty("native.encoding", ""));
        return resolve(options, in);
    }

    static Capabilities resolve(ConsoleOptions options, Inputs in) {
        Builder b = builder().tty(in.tty());
        Map<String, String> env = in.env();
        String term = env.getOrDefault("TERM", "");
        boolean windows = in.osName().startsWith("Windows");
        boolean terminal = in.tty() && !term.equals("dumb") && (!windows || hasVtHint(env));

        if (options.colorDepth().isPresent()) {
            ColorDepth depth = options.colorDepth().get();
            b.colorDepth(depth).escapes(depth != ColorDepth.NONE || terminal);
        } else if (isSet(env, "NO_COLOR")) {
            b.colorDepth(ColorDepth.NONE).escapes(terminal).reason("colour off: NO_COLOR is set");
        } else if (isForcedOff(env)) {
            b.colorDepth(ColorDepth.NONE).escapes(terminal).reason("colour off: FORCE_COLOR=" + env.get("FORCE_COLOR"));
        } else if (isForcedOn(env)) {
            b.colorDepth(forcedDepth(env, b)).escapes(true);
        } else if (!in.tty()) {
            b.colorDepth(ColorDepth.NONE).escapes(false).reason("no escapes: output is not a terminal");
        } else if (term.equals("dumb")) {
            b.colorDepth(ColorDepth.NONE).escapes(false).reason("no escapes: TERM=dumb");
        } else if (!terminal) {
            b.colorDepth(ColorDepth.NONE).escapes(false)
                .reason("no escapes: Windows console without a VT hint (TERM, TERM_PROGRAM, WT_SESSION, COLORTERM);"
                    + " set FORCE_COLOR to enable");
        } else {
            b.colorDepth(detectDepth(env, b));
        }

        if (term.equals("linux")) {
            b.attrs(EnumSet.of(Attr.BOLD, Attr.DIM, Attr.UNDERLINE, Attr.REVERSE));
            if (terminal) b.reason("italic and strike dropped: TERM=linux");
        }

        Charset output =
            Encoding.resolveOutput(in.tty(), in.stdoutEncoding(), in.consoleCharset(), in.nativeEncoding());
        b.outputCharset(output)
            .inputCharset(Encoding.resolveInput(in.stdinEncoding(), in.consoleCharset(), in.nativeEncoding()))
            .glyphTier(glyphTier(options, env, output, b))
            .size(size(env));
        return b.build();
    }

    private static boolean isSet(Map<String, String> env, String name) {
        String value = env.get(name);
        return value != null && !value.isEmpty();
    }

    private static boolean hasVtHint(Map<String, String> env) {
        return isSet(env, "TERM") || isSet(env, "TERM_PROGRAM") || isSet(env, "WT_SESSION") || isSet(env, "COLORTERM")
            || "ON".equalsIgnoreCase(env.get("ConEmuANSI"));
    }

    private static boolean isForcedOff(Map<String, String> env) {
        String force = env.getOrDefault("FORCE_COLOR", "");
        return force.equals("0") || force.equalsIgnoreCase("false");
    }

    private static boolean isForcedOn(Map<String, String> env) {
        return isSet(env, "FORCE_COLOR") || (isSet(env, "CLICOLOR_FORCE") && !env.get("CLICOLOR_FORCE").equals("0"));
    }

    private static ColorDepth forcedDepth(Map<String, String> env, Builder b) {
        return switch (env.getOrDefault("FORCE_COLOR", "")) {
            case "1" -> ColorDepth.ANSI16;
            case "2" -> ColorDepth.ANSI256;
            case "3" -> ColorDepth.TRUECOLOR;
            default -> detectDepth(env, b);
        };
    }

    private static ColorDepth detectDepth(Map<String, String> env, Builder b) {
        String term = env.getOrDefault("TERM", "");
        String program = env.getOrDefault("TERM_PROGRAM", "");
        String colorterm = env.getOrDefault("COLORTERM", "").toLowerCase(Locale.ROOT);
        ColorDepth depth = ColorDepth.ANSI16;
        if (term.contains("256color")) depth = ColorDepth.ANSI256;
        if (term.contains("direct") || term.contains("truecolor") || term.contains("24bit")) {
            depth = ColorDepth.TRUECOLOR;
        }
        if (program.equals("iTerm.app") || program.equals("WezTerm") || program.equals("vscode")
            || program.equals("ghostty")) {
            depth = ColorDepth.TRUECOLOR;
        }
        if (isSet(env, "WT_SESSION")) depth = ColorDepth.TRUECOLOR;
        if (colorterm.equals("truecolor") || colorterm.equals("24bit")) depth = ColorDepth.TRUECOLOR;
        // a higher-precedence source can also cap what a lower one claims
        if (program.equals("Apple_Terminal") && depth.compareTo(ColorDepth.ANSI256) > 0) {
            depth = ColorDepth.ANSI256;
            b.reason("colour capped at ANSI256: TERM_PROGRAM=Apple_Terminal");
        }
        if (term.equals("linux") && depth.compareTo(ColorDepth.ANSI16) > 0) {
            depth = ColorDepth.ANSI16;
            b.reason("colour capped at ANSI16: TERM=linux");
        }
        return depth;
    }

    private static GlyphTier glyphTier(ConsoleOptions options, Map<String, String> env, Charset output, Builder b) {
        if (options.glyphTier().isPresent()) return options.glyphTier().get();
        String requested = env.getOrDefault("CONSOLEKIT_GLYPHS", "").trim();
        if (!requested.isEmpty()) {
            switch (requested.toLowerCase(Locale.ROOT)) {
                case "full" -> {
                    return GlyphTier.FULL;
                }
                case "cp437" -> {
                    return GlyphTier.CP437;
                }
                case "ascii" -> {
                    return GlyphTier.ASCII;
                }
                default -> b.reason("ignored CONSOLEKIT_GLYPHS=" + requested + " (expected full, cp437 or ascii)");
            }
        }
        GlyphTier tier = Encoding.probeTier(output);
        if (tier != GlyphTier.FULL) {
            String missing = Encoding.firstUnencodable(output, GlyphTier.FULL);
            b.reason("glyph tier " + tier + ": " + output.name() + " cannot encode U+"
                + String.format("%04X", missing.codePointAt(0)));
        }
        return tier;
    }

    private static Size size(Map<String, String> env) {
        int columns = positive(env.get("COLUMNS"));
        int rows = positive(env.get("LINES"));
        return columns == 0 && rows == 0 ? Size.UNKNOWN : new Size(columns, rows);
    }

    static int positive(String value) {
        if (value == null) return 0;
        try {
            int n = Integer.parseInt(value.trim());
            return Math.max(n, 0);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public ColorDepth colorDepth() {
        return colorDepth;
    }

    public GlyphTier glyphTier() {
        return glyphTier;
    }

    public boolean isTty() {
        return tty;
    }

    /** Whether any escape sequence may be written; when false, text goes out unchanged. */
    public boolean canEmitEscapes() {
        return escapes;
    }

    public Set<Attr> attrs() {
        EnumSet<Attr> set = EnumSet.noneOf(Attr.class);
        for (Attr a : Attr.values()) {
            if (supports(a)) set.add(a);
        }
        return set;
    }

    public boolean supports(Attr attr) {
        return (attrs & attr.bit()) != 0;
    }

    /** Probe-time size from the environment; diagnostic only, never for layout. */
    public Size size() {
        return size;
    }

    public Charset outputCharset() {
        return outputCharset;
    }

    public Charset inputCharset() {
        return inputCharset;
    }

    public List<String> reasons() {
        return reasons;
    }

    /** Every degradation reason joined with {@code "; "}, or {@code ""} when nothing was degraded. */
    public String reason() {
        return String.join("; ", reasons);
    }

    @Override
    public String toString() {
        return "Capabilities[" + colorDepth + ", " + glyphTier + ", tty=" + tty + ", escapes=" + escapes
            + ", attrs=" + attrs() + ", out=" + outputCharset.name() + ", in=" + inputCharset.name()
            + ", size=" + size + "]";
    }
}
