package dev.consolekit.core;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RenderingTest {

    private static final Capabilities PLAIN =
        Capabilities.builder().tty(false).escapes(false).colorDepth(ColorDepth.NONE).build();
    private static final Capabilities ANSI16 = Capabilities.builder().colorDepth(ColorDepth.ANSI16).build();

    private final ByteArrayOutputStream warnings = new ByteArrayOutputStream();

    @BeforeEach
    void captureWarnings() {
        ConsoleRuntime.reset();
        ConsoleRuntime.warnSink(new PrintStream(warnings, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void resetRuntime() {
        ConsoleRuntime.reset();
    }

    private static Renderable lines(StyledText... lines) {
        return ctx -> List.of(lines);
    }

    @Test
    void cr44_toStringJoinsLinesWithNewline() {
        Renderable r = lines(StyledText.of("one"), StyledText.of("two"));
        assertEquals("one\ntwo", Rendering.toString(r, RenderContext.of(PLAIN, Theme.DEFAULT, 80)));
    }

    @Test
    void cr44_escapesAreAppliedPerCapabilities() {
        Renderable r = lines(StyledText.of("x", Style.fg(Color.RED)));
        assertEquals("\u001b[31mx\u001b[39m", Rendering.toString(r, RenderContext.of(ANSI16, Theme.DEFAULT, 80)));
    }

    @Test
    void cr44_textIsUnchangedWhenEscapesCannotRender() {
        Renderable r = lines(StyledText.of("x", Style.fg(Color.RED).bold()));
        assertEquals("x", Rendering.toString(r, RenderContext.of(PLAIN, Theme.DEFAULT, 80)));
    }

    @Test
    void cr42_unsetChannelsPrintAsTerminalDefault() {
        Renderable r = lines(StyledText.of("x", Style.NONE));
        assertEquals("x", Rendering.toString(r, RenderContext.of(ANSI16, Theme.DEFAULT, 80)));
    }

    @Test
    void cr16_sameContextSameOutput() {
        Renderable r = ctx -> List.of(StyledText.of("w=" + ctx.width(), ctx.theme().style(Theme.Role.INFO)));
        RenderContext ctx = RenderContext.of(ANSI16, Theme.DEFAULT, 30);
        assertEquals(Rendering.toString(r, ctx), Rendering.toString(r, ctx));
        assertEquals("\u001b[36mw=30\u001b[39m", Rendering.toString(r, ctx));
    }

    @Test
    void cr17_renderReceivesTheGivenContext() {
        RenderContext[] seen = new RenderContext[1];
        Renderable r = ctx -> {
            seen[0] = ctx;
            return List.of();
        };
        RenderContext ctx = RenderContext.of(PLAIN, Theme.MONOCHROME, 33).withViewport(33, 5);
        Rendering.toString(r, ctx);
        assertSame(ctx, seen[0]);
    }

    @Test
    void cr44_lineLongerThanWidthIsClipped() {
        Renderable r = lines(StyledText.of("příliš žluťoučký kůň"));
        assertEquals("příliš", Rendering.toString(r, RenderContext.of(PLAIN, Theme.DEFAULT, 6)));
    }

    @Test
    void cr45_ambientWidthComesFromColumns() {
        assertEquals(132, Rendering.ambientWidth(Map.of("COLUMNS", "132")));
        assertEquals(132, Rendering.ambientWidth(Map.of("COLUMNS", " 132 ")));
    }

    @Test
    void cr45_ambientWidthFallsBackTo80() {
        assertEquals(80, Rendering.ambientWidth(Map.of()));
        assertEquals(80, Rendering.ambientWidth(Map.of("COLUMNS", "wide")));
        assertEquals(80, Rendering.ambientWidth(Map.of("COLUMNS", "0")));
        assertEquals(80, Rendering.ambientWidth(Map.of("COLUMNS", "-5")));
        assertEquals(80, Rendering.ambientWidth(Map.of("COLUMNS", "")));
    }

    @Test
    void cr44_ambientContextUsesRuntimeCapabilitiesDefaultThemeAndAmbientWidth() {
        RenderContext ctx = Rendering.ambientContext();
        assertSame(ConsoleRuntime.capabilities(), ctx.capabilities());
        assertSame(Theme.DEFAULT, ctx.theme());
        assertEquals(Rendering.ambientWidth(), ctx.width());
        assertFalse(ctx.hasViewport());
    }

    @Test
    void cr44_oneArgumentFormUsesAmbientContext() {
        ConsoleRuntime.configure(ConsoleOptions.builder().colorDepth(ColorDepth.ANSI16).build());
        Renderable r = lines(StyledText.of("x", Style.fg(Color.RED)));
        assertEquals("\u001b[31mx\u001b[39m", Rendering.toString(r));
    }

    @Test
    void cr44_printWritesEachLine() {
        ConsoleRuntime.configure(ConsoleOptions.builder().colorDepth(ColorDepth.NONE).build());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8);

        Rendering.print(lines(StyledText.of("a"), StyledText.of("b")), out);

        assertEquals("a\nb\n", bytes.toString(StandardCharsets.UTF_8));
    }

    @Test
    void cr24_renderFailureFallsBackToPlainOutput() {
        Renderable broken = ctx -> {
            throw new IllegalStateException("boom");
        };

        String first = Rendering.toString(broken, RenderContext.of(ANSI16, Theme.DEFAULT, 80));
        String second = Rendering.toString(broken, RenderContext.of(ANSI16, Theme.DEFAULT, 80));

        assertFalse(first.contains("\u001b"), first);
        assertTrue(first.contains("render failed"), first);
        assertEquals(first, second);
        String warned = warnings.toString(StandardCharsets.UTF_8);
        assertEquals(warned.indexOf("boom"), warned.lastIndexOf("boom"), warned);
        assertTrue(warned.contains("boom"), warned);
    }

    @Test
    void cr24_nullLinesDoNotThrow() {
        Renderable r = ctx -> null;
        assertEquals("", Rendering.toString(r, RenderContext.of(PLAIN, Theme.DEFAULT, 80)));
        Renderable withNull = ctx -> java.util.Arrays.asList(StyledText.of("a"), null);
        assertEquals("a\n", Rendering.toString(withNull, RenderContext.of(PLAIN, Theme.DEFAULT, 80)));
    }

    @Test
    void cr24_nullRenderableDoesNotThrow() {
        assertEquals("null", Rendering.toString(null, RenderContext.of(PLAIN, Theme.DEFAULT, 80)));
    }
}
