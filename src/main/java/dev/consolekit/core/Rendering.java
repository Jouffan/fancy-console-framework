package dev.consolekit.core;

import java.io.PrintStream;
import java.util.List;
import java.util.Map;

import dev.consolekit.core.internal.Ansi;

/**
 * The one path from a {@link Renderable} to characters on an ordinary stream, with escapes applied per the
 * capabilities. Other parts may put a facade over it; none has a second escape emitter.
 *
 * <p>The one-argument forms use the ambient context: this process's capabilities, {@link Theme#DEFAULT},
 * and the ambient width ({@code COLUMNS} if it is a positive integer, else 80). Lines wider than the
 * context are cut on a grapheme boundary. Rendering never throws: a failing renderable is reported once on
 * stderr and replaced by a plain one-line marker.
 *
 * {@snippet :
 * System.out.println(Rendering.toString(table));                 // ambient context
 * String narrow = Rendering.toString(table, RenderContext.of(caps, Theme.MONOCHROME, 40));
 * Rendering.print(table, System.out);
 * }
 */
public final class Rendering {

    private static final int FALLBACK_WIDTH = 80;

    private Rendering() {
    }

    public static String toString(Renderable r) {
        return toString(r, ambientContext());
    }

    public static String toString(Renderable r, RenderContext ctx) {
        if (r == null) return "null";
        List<StyledText> lines = renderSafely(r, ctx);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) out.append('\n');
            StyledText line = lines.get(i);
            if (line != null) out.append(emit(line.clip(ctx.width()), ctx.capabilities()));
        }
        return out.toString();
    }

    /** Each line of {@code r} in the ambient context, followed by {@code '\n'}. */
    public static void print(Renderable r, PrintStream out) {
        String text = toString(r);
        out.print(text.isEmpty() ? text : text + "\n");
        out.flush();
    }

    /** Capabilities for stdout, {@link Theme#DEFAULT}, and {@link #ambientWidth()}. */
    public static RenderContext ambientContext() {
        return RenderContext.of(ConsoleRuntime.capabilities(), Theme.DEFAULT, ambientWidth());
    }

    /** {@code COLUMNS} if it parses as a positive integer, else 80. Never asks the terminal. */
    public static int ambientWidth() {
        return ambientWidth(System.getenv());
    }

    static int ambientWidth(Map<String, String> env) {
        int columns = Capabilities.positive(env.get("COLUMNS"));
        return columns > 0 ? columns : FALLBACK_WIDTH;
    }

    private static List<StyledText> renderSafely(Renderable r, RenderContext ctx) {
        try {
            List<StyledText> lines = r.render(ctx);
            return lines == null ? List.of() : lines;
        } catch (RuntimeException | StackOverflowError e) {
            ConsoleRuntime.warnOnce("render failed in " + r.getClass().getName() + ": " + e);
            return List.of(StyledText.of("[render failed: " + r.getClass().getSimpleName() + "]"));
        }
    }

    private static String emit(StyledText line, Capabilities caps) {
        try {
            return Ansi.line(line, caps);
        } catch (RuntimeException e) {
            ConsoleRuntime.warnOnce("escape output failed: " + e);
            return line.text();
        }
    }
}
