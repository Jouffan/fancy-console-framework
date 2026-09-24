package dev.consolekit.core;

import java.lang.management.ManagementFactory;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CellBufferTest {

    private static final Style RED = Style.fg(Color.RED);

    @AfterEach
    void resetRuntime() {
        ConsoleRuntime.reset();
    }

    private static CellBuffer row(String glyphs) {
        CellBuffer b = CellBuffer.of(glyphs.length(), 1);
        for (int x = 0; x < glyphs.length(); x++) {
            char c = glyphs.charAt(x);
            if (c != ' ') b.put(x, 0, Cell.of(c, Style.NONE));
        }
        return b;
    }

    @Test
    void cr46_newBufferIsTransparent() {
        CellBuffer b = CellBuffer.of(3, 2);
        assertEquals(3, b.width());
        assertEquals(2, b.height());
        assertEquals(Cell.TRANSPARENT, b.get(0, 0));
        assertEquals(Cell.TRANSPARENT, b.get(2, 1));
        assertEquals("   \n   \n", b.dump());
    }

    @Test
    void cr46_nonPositiveSizeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> CellBuffer.of(0, 1));
        assertThrows(IllegalArgumentException.class, () -> CellBuffer.of(1, -1));
    }

    @Test
    void cr46_dumpRendersGlyphRows() {
        CellBuffer b = CellBuffer.of(3, 2);
        b.put(0, 0, Cell.of('a', Style.NONE));
        b.put(2, 1, Cell.of('b', Style.NONE));
        assertEquals("a  \n  b\n", b.dump());
    }

    @Test
    void cr46_putAppliesTheAbsenceRule() {
        CellBuffer b = CellBuffer.of(1, 1);
        b.put(0, 0, Cell.of('a', RED));
        b.put(0, 0, Cell.of(Style.bg(Color.BLUE)));
        assertEquals(Cell.of('a', Style.fg(Color.RED).withBg(Color.BLUE)), b.get(0, 0));
    }

    @Test
    void cr46_hotPathPutMatchesCellPut() {
        CellBuffer a = CellBuffer.of(2, 1);
        CellBuffer b = CellBuffer.of(2, 1);
        a.put(0, 0, Cell.of('x', RED.bold()));
        b.put(0, 0, 'x', RED.bold());
        assertEquals(a.get(0, 0), b.get(0, 0));
    }

    @Test
    void cr41_compositeKeepsGlyphWhenSourceAbsent() {
        CellBuffer dst = CellBuffer.of(1, 1);
        dst.put(0, 0, Cell.of('a', RED));
        CellBuffer src = CellBuffer.of(1, 1);
        src.put(0, 0, Cell.of(Style.bg(Color.BLUE)));

        dst.composite(src, 0, 0, 0, 0, 1, 1);

        assertEquals(Cell.of('a', Style.fg(Color.RED).withBg(Color.BLUE)), dst.get(0, 0));
    }

    @Test
    void cr41_compositeTakesGlyphAndAttributesTogether() {
        CellBuffer dst = CellBuffer.of(1, 1);
        dst.put(0, 0, Cell.of('a', Style.NONE.bold()));
        CellBuffer src = CellBuffer.of(1, 1);
        src.put(0, 0, Cell.of('b', Style.NONE.italic()));

        dst.composite(src, 0, 0);

        assertEquals(Cell.of('b', Style.NONE.italic()), dst.get(0, 0));
    }

    @Test
    void cr41_absentGlyphKeepsDestinationAttributes() {
        CellBuffer dst = CellBuffer.of(1, 1);
        dst.put(0, 0, Cell.of('a', Style.NONE.bold()));
        CellBuffer src = CellBuffer.of(1, 1);
        src.put(0, 0, Cell.of(Style.fg(Color.GREEN).italic()));

        dst.composite(src, 0, 0);

        assertEquals(Cell.of('a', Style.fg(Color.GREEN).bold()), dst.get(0, 0));
    }

    @Test
    void cr41_absentColourKeepsDestinationColour() {
        CellBuffer dst = CellBuffer.of(1, 1);
        dst.put(0, 0, Cell.of('a', Style.fg(Color.RED).withBg(Color.BLUE)));
        CellBuffer src = CellBuffer.of(1, 1);
        src.put(0, 0, Cell.of('b', Style.NONE));

        dst.composite(src, 0, 0);

        assertEquals(Cell.of('b', Style.fg(Color.RED).withBg(Color.BLUE)), dst.get(0, 0));
    }

    @Test
    void cr41_defaultBackgroundIsOpaque() {
        CellBuffer dst = CellBuffer.of(1, 1);
        dst.put(0, 0, Cell.of('a', Style.bg(Color.BLUE)));
        CellBuffer src = CellBuffer.of(1, 1);
        src.put(0, 0, Cell.of(' ', Style.bg(Color.DEFAULT)));

        dst.composite(src, 0, 0);

        assertEquals(Cell.of(' ', Style.bg(Color.DEFAULT)), dst.get(0, 0));
    }

    @Test
    void cr41_transparentSourceLeavesDestinationUntouched() {
        CellBuffer dst = row("abc");
        dst.composite(CellBuffer.of(3, 1), 0, 0);
        assertEquals("abc\n", dst.dump());
    }

    @Test
    void cr46_sourceIsPlacedAtOffset() {
        CellBuffer dst = row("......");
        dst.composite(row("ab"), 3, 0);
        assertEquals("...ab.\n", dst.dump());
    }

    @Test
    void cr46_sourceBeyondBufferIsCut() {
        CellBuffer dst = row("....");
        dst.composite(row("abc"), 2, 0);
        assertEquals("..ab\n", dst.dump());
        dst.composite(row("xyz"), -2, 0);
        assertEquals("z.ab\n", dst.dump());
    }

    @Test
    void cr46_clipIsInDestinationSpace() {
        CellBuffer dst = row(".....");
        dst.composite(row("abcde"), 0, 0, 1, 0, 3, 1);
        assertEquals(".bcd.\n", dst.dump());
    }

    @Test
    void cr46_clipAppliesToRows() {
        CellBuffer dst = CellBuffer.of(1, 3);
        dst.fill(Cell.of('.', Style.NONE));
        CellBuffer src = CellBuffer.of(1, 3);
        src.fill(Cell.of('x', Style.NONE));
        dst.composite(src, 0, 0, 0, 1, 1, 1);
        assertEquals(".\nx\n.\n", dst.dump());
    }

    @Test
    void cr46_emptyClipWritesNothing() {
        CellBuffer dst = row("...");
        dst.composite(row("abc"), 0, 0, 0, 0, 0, 1);
        dst.composite(row("abc"), 0, 0, 0, 0, -3, 1);
        assertEquals("...\n", dst.dump());
    }

    @Test
    void cr46_cr35_wideGlyphIsCellPlusContinuation() {
        CellBuffer b = row("....");
        b.put(1, 0, Cell.of("漢", Style.NONE));
        assertEquals(".漢.\n", b.dump());
        assertFalse(b.isContinuation(1, 0));
        assertTrue(b.isContinuation(2, 0));
        assertEquals(Cell.of("漢", Style.NONE), b.get(1, 0));
    }

    @Test
    void cr46_cr35_wideGlyphSkippedWhenContinuationClipped() {
        CellBuffer dst = row("....");
        CellBuffer src = CellBuffer.of(2, 1);
        src.put(0, 0, Cell.of("漢", Style.NONE));

        dst.composite(src, 1, 0, 0, 0, 2, 1);

        assertEquals("....\n", dst.dump());
    }

    @Test
    void cr46_cr35_wideGlyphSkippedAtBufferEdge() {
        CellBuffer dst = row("...");
        CellBuffer src = CellBuffer.of(2, 1);
        src.put(0, 0, Cell.of("漢", Style.NONE));

        dst.composite(src, 2, 0);

        assertEquals("...\n", dst.dump());
    }

    @Test
    void cr46_putWideGlyphInLastColumnSkips() {
        CellBuffer b = row("...");
        b.put(2, 0, Cell.of("漢", Style.NONE));
        assertEquals("...\n", b.dump());
    }

    @Test
    void cr46_putWideGlyphInOneWideBufferSkips() {
        CellBuffer b = CellBuffer.of(1, 1);
        b.put(0, 0, Cell.of("漢", Style.NONE));
        assertEquals(Cell.TRANSPARENT, b.get(0, 0));
    }

    @Test
    void cr46_overwritingContinuationBlanksLead() {
        CellBuffer b = row("....");
        b.put(0, 0, Cell.of("漢", RED));
        b.put(1, 0, Cell.of('x', Style.NONE));
        assertEquals(" x..\n", b.dump());
        assertEquals(Cell.of(' ', RED), b.get(0, 0));
        assertFalse(b.isContinuation(1, 0));
    }

    @Test
    void cr46_overwritingLeadBlanksContinuation() {
        CellBuffer b = row("....");
        b.put(0, 0, Cell.of("漢", Style.NONE));
        b.put(0, 0, Cell.of('x', Style.NONE));
        assertEquals("x ..\n", b.dump());
        assertFalse(b.isContinuation(1, 0));
    }

    @Test
    void cr46_blankingOtherHalfIsNotClipLimited() {
        CellBuffer dst = row("....");
        dst.put(0, 0, Cell.of("漢", Style.NONE));

        dst.composite(row("x"), 1, 0, 1, 0, 1, 1);

        assertEquals(" x..\n", dst.dump());
    }

    @Test
    void cr46_wideOverWideNeverLeavesASplitCell() {
        CellBuffer dst = row("....");
        dst.put(1, 0, Cell.of("漢", Style.NONE));
        dst.put(0, 0, Cell.of("字", Style.NONE));
        assertEquals("字 .\n", dst.dump());
        assertFalse(dst.isContinuation(2, 0));
    }

    @Test
    void cr46_colourOnlyOverWideCellBlanksBothHalves() {
        CellBuffer dst = row("....");
        dst.put(0, 0, Cell.of("漢", Style.NONE));

        dst.put(1, 0, Cell.of(Style.bg(Color.BLUE)));

        assertEquals("  ..\n", dst.dump());
        assertEquals(Cell.of(' ', Style.bg(Color.BLUE)), dst.get(1, 0));
        assertFalse(dst.isContinuation(1, 0));
    }

    @Test
    void cr46_compositeCopiesWideSource() {
        CellBuffer src = CellBuffer.of(3, 1);
        src.put(0, 0, Cell.of("漢", RED));
        src.put(2, 0, Cell.of('!', Style.NONE));
        CellBuffer dst = row(".....");

        dst.composite(src, 1, 0);

        assertEquals(".漢!.\n", dst.dump());
        assertTrue(dst.isContinuation(2, 0));
        assertEquals(Cell.of("漢", RED), dst.get(1, 0));
    }

    @Test
    void cr46_multiCodePointClusterRoundTrips() {
        CellBuffer b = CellBuffer.of(3, 1);
        b.put(0, 0, Cell.of("👩‍💻", Style.NONE));
        assertEquals(Cell.of("👩‍💻", Style.NONE), b.get(0, 0));
        assertEquals("👩‍💻 \n", b.dump());

        CellBuffer dst = CellBuffer.of(3, 1);
        dst.composite(b, 1, 0);
        assertEquals(" 👩‍💻\n", dst.dump());
    }

    @Test
    void cr46_fillSetsEveryCell() {
        CellBuffer b = CellBuffer.of(2, 2);
        Cell blank = Cell.of(' ', Style.fg(Color.DEFAULT).withBg(Color.DEFAULT));
        b.fill(blank);
        assertEquals(blank, b.get(0, 0));
        assertEquals(blank, b.get(1, 1));
    }

    @Test
    void cr46_fillWithTransparentMakesEveryCellAbsent() {
        CellBuffer b = row("ab");
        b.fill(Cell.TRANSPARENT);
        assertEquals(Cell.TRANSPARENT, b.get(0, 0));
    }

    @Test
    void cr46_clearResetsToTransparent() {
        CellBuffer b = row("ab");
        b.clear();
        assertEquals(Cell.TRANSPARENT, b.get(1, 0));
        assertEquals("  \n", b.dump());
    }

    @Test
    void cr24_outOfBoundsWritesAreIgnored() {
        CellBuffer b = row("ab");
        b.put(-1, 0, Cell.of('x', Style.NONE));
        b.put(2, 0, Cell.of('x', Style.NONE));
        b.put(0, 1, 'x', Style.NONE);
        assertEquals("ab\n", b.dump());
    }

    @Test
    void cr9_hotPathPutReplacesControlCharacter() {
        CellBuffer b = CellBuffer.of(1, 1);
        b.put(0, 0, 0x1B, Style.NONE);
        assertEquals("?\n", b.dump());
    }

    @Test
    void cr42_toStyledTextResolvesAbsentGlyphToSpace() {
        CellBuffer b = CellBuffer.of(3, 1);
        b.put(1, 0, Cell.of('a', RED));
        b.put(2, 0, Cell.of(Style.NONE.bold()));

        List<StyledText> lines = b.toStyledText();

        assertEquals(1, lines.size());
        assertEquals(" a ", lines.get(0).text());
        assertEquals(List.of(
            new StyledText.Span(" ", Style.NONE),
            new StyledText.Span("a", RED),
            new StyledText.Span(" ", Style.NONE)), lines.get(0).spans());
    }

    @Test
    void cr42_toStyledTextKeepsWideGlyphOnce() {
        CellBuffer b = CellBuffer.of(3, 1);
        b.put(0, 0, Cell.of("漢", Style.NONE));
        assertEquals("漢 ", b.toStyledText().get(0).text());
    }

    @Test
    void cr46_steadyStateOperationsDoNotAllocate() {
        com.sun.management.ThreadMXBean threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        CellBuffer dst = CellBuffer.of(80, 24);
        CellBuffer src = CellBuffer.of(20, 5);
        Cell blank = Cell.of(' ', Style.fg(Color.DEFAULT).withBg(Color.DEFAULT));
        Cell wide = Cell.of("漢", RED);
        Style style = Style.fg(Color.rgb(1, 2, 3)).bold();
        src.put(0, 0, Cell.of("👩‍💻", Style.NONE));
        for (int i = 0; i < 20_000; i++) {
            exercise(dst, src, blank, wide, style, i);
        }

        long before = threads.getCurrentThreadAllocatedBytes();
        for (int i = 0; i < 20_000; i++) {
            exercise(dst, src, blank, wide, style, i);
        }
        long allocated = threads.getCurrentThreadAllocatedBytes() - before;

        assertTrue(allocated < 4096, "allocated " + allocated + " bytes");
    }

    private static void exercise(CellBuffer dst, CellBuffer src, Cell blank, Cell wide, Style style, int i) {
        dst.fill(blank);
        src.put(i % 20, i % 5, 'a' + i % 26, style);
        src.put((i + 3) % 20, (i + 1) % 5, wide);
        dst.composite(src, i % 70, i % 20, 0, 0, 80, 24);
        dst.put(i % 80, i % 24, wide);
        dst.clear();
    }
}
