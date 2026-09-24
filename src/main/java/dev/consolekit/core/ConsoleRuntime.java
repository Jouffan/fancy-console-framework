package dev.consolekit.core;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import dev.consolekit.core.internal.GraphemeTable;

/**
 * The one holder of process-wide state: the once-settable {@link ConsoleOptions}, the resolved
 * {@link Capabilities}, the grapheme intern table, the warn-once flag, and an opaque session slot per type
 * for the other parts. Core never names the types stored in the slot. Thread-safe.
 *
 * {@snippet :
 * ConsoleRuntime.configure(ConsoleOptions.builder().glyphTier(GlyphTier.ASCII).build());   // optional, once
 * Capabilities caps = ConsoleRuntime.capabilities();
 *
 * // another part keeps its process-wide engine here, under its own type
 * Engine engine = ConsoleRuntime.session(Engine.class, Engine::open);
 * }
 */
public final class ConsoleRuntime {

    private static final Object LOCK = new Object();

    private static ConsoleOptions options;
    private static Capabilities capabilities;
    private static GraphemeTable graphemes = new GraphemeTable(GraphemeTable.DEFAULT_BOUND);
    private static final Map<Class<?>, Object> sessions = new HashMap<>();
    private static boolean warned;
    private static PrintStream warnSink;

    private ConsoleRuntime() {
    }

    /**
     * Sets explicit configuration. Allowed once, and only before capabilities have been resolved (that is,
     * before any output); anything else fails loudly rather than produce inconsistent output.
     */
    public static void configure(ConsoleOptions options) {
        if (options == null) throw new IllegalArgumentException("options must not be null");
        synchronized (LOCK) {
            if (ConsoleRuntime.options != null) throw new IllegalStateException("ConsoleOptions already configured");
            if (capabilities != null) {
                throw new IllegalStateException("ConsoleOptions must be configured before any output");
            }
            ConsoleRuntime.options = options;
        }
    }

    /** The capabilities of this process's stdout, resolved on first use and then fixed. */
    public static Capabilities capabilities() {
        synchronized (LOCK) {
            if (capabilities == null) {
                capabilities = Capabilities.detect(options != null ? options : ConsoleOptions.builder().build());
            }
            return capabilities;
        }
    }

    /** The session stored under {@code type}, opening it with {@code opener} the first time. */
    public static <T> T session(Class<T> type, Supplier<T> opener) {
        if (type == null) throw new IllegalArgumentException("type must not be null");
        if (opener == null) throw new IllegalArgumentException("opener must not be null");
        synchronized (LOCK) {
            Object existing = sessions.get(type);
            if (existing != null) return type.cast(existing);
            T opened = opener.get();
            if (opened == null) throw new IllegalArgumentException("opener returned null for " + type.getName());
            sessions.put(type, opened);
            return opened;
        }
    }

    /** The session stored under {@code type}, if one has been opened. */
    public static <T> Optional<T> sessionIfOpen(Class<T> type) {
        if (type == null) throw new IllegalArgumentException("type must not be null");
        synchronized (LOCK) {
            return Optional.ofNullable(type.cast(sessions.get(type)));
        }
    }

    /** Writes {@code message} to stderr the first time any part warns; later warnings are dropped. */
    public static void warnOnce(String message) {
        PrintStream sink;
        synchronized (LOCK) {
            if (warned) return;
            warned = true;
            sink = warnSink != null ? warnSink : System.err;
        }
        sink.println("consolekit: " + message);
    }

    static GraphemeTable graphemes() {
        synchronized (LOCK) {
            return graphemes;
        }
    }

    /** Test hook: back to a fresh process state. */
    static void reset() {
        synchronized (LOCK) {
            options = null;
            capabilities = null;
            graphemes = new GraphemeTable(GraphemeTable.DEFAULT_BOUND);
            sessions.clear();
            warned = false;
            warnSink = null;
        }
    }

    /** Test hook: where {@link #warnOnce} writes. */
    static void warnSink(PrintStream sink) {
        synchronized (LOCK) {
            warnSink = sink;
        }
    }
}
