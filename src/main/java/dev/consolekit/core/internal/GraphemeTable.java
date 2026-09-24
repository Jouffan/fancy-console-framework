package dev.consolekit.core.internal;

import java.util.HashMap;
import java.util.Map;

/**
 * Interned multi-code-point grapheme clusters (ZWJ emoji, sequences NFC cannot compose), so a packed cell
 * grid can hold one as a single {@code int}. Append-only and bounded: past the bound, a new cluster is
 * replaced rather than stored. One table per process, held by {@code ConsoleRuntime}; thread-safe.
 *
 * {@snippet :
 * GraphemeTable table = new GraphemeTable(GraphemeTable.DEFAULT_BOUND);
 * int id = table.intern("👩‍💻");      // <= -2, stable for the life of the table
 * String back = table.cluster(id);
 * }
 */
public final class GraphemeTable {

    public static final int DEFAULT_BOUND = 4096;
    /** What {@link #intern} returns once the table is full: a code point, not an id. */
    public static final int REPLACEMENT = '?';

    private final Map<String, Integer> ids = new HashMap<>();
    private final String[] clusters;
    private final int[] widths;
    private int size;

    public GraphemeTable(int bound) {
        if (bound <= 0) throw new IllegalArgumentException("bound must be > 0: " + bound);
        this.clusters = new String[bound];
        this.widths = new int[bound];
    }

    /** The id of {@code cluster} (always {@code <= -2}), or {@link #REPLACEMENT} when the table is full. */
    public synchronized int intern(String cluster) {
        Integer known = ids.get(cluster);
        if (known != null) return known;
        if (size == clusters.length) return REPLACEMENT;
        clusters[size] = cluster;
        widths[size] = TextWidth.graphemeWidth(cluster);
        int id = -2 - size;
        ids.put(cluster, id);
        size++;
        return id;
    }

    public synchronized String cluster(int id) {
        return clusters[-2 - id];
    }

    public synchronized int width(int id) {
        return widths[-2 - id];
    }

    public synchronized int size() {
        return size;
    }
}
