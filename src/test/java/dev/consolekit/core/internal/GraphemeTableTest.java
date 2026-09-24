package dev.consolekit.core.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GraphemeTableTest {

    @Test
    void cr46_internReturnsStableNegativeIds() {
        GraphemeTable table = new GraphemeTable(8);
        int id = table.intern("👩‍💻");
        assertTrue(id <= -2);
        assertEquals(id, table.intern("👩‍💻"));
        assertEquals("👩‍💻", table.cluster(id));
        assertEquals(2, table.width(id));
        assertEquals(1, table.size());
    }

    @Test
    void cr46_distinctClustersGetDistinctIds() {
        GraphemeTable table = new GraphemeTable(8);
        assertNotEquals(table.intern("👩‍💻"), table.intern("🇨🇿"));
    }

    @Test
    void cr46_boundReplacesPastLimit() {
        GraphemeTable table = new GraphemeTable(1);
        table.intern("👩‍💻");
        assertEquals(GraphemeTable.REPLACEMENT, table.intern("🇨🇿"));
        assertEquals(1, table.size());
    }

    @Test
    void cr46_defaultBoundIs4096() {
        assertEquals(4096, GraphemeTable.DEFAULT_BOUND);
    }

    @Test
    void cr46_boundMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new GraphemeTable(0));
    }
}
