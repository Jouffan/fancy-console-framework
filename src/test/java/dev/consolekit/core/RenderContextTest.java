package dev.consolekit.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RenderContextTest {

    private static final Capabilities CAPS = Capabilities.builder().build();

    @Test
    void cr17_carriesCapabilitiesThemeAndWidth() {
        RenderContext ctx = RenderContext.of(CAPS, Theme.MONOCHROME, 42);
        assertSame(CAPS, ctx.capabilities());
        assertSame(Theme.MONOCHROME, ctx.theme());
        assertEquals(42, ctx.width());
    }

    @Test
    void cr17_viewportIsOptionalAndFilledByTheCaller() {
        RenderContext ctx = RenderContext.of(CAPS, Theme.DEFAULT, 80);
        assertFalse(ctx.hasViewport());

        RenderContext withViewport = ctx.withViewport(100, 30);

        assertTrue(withViewport.hasViewport());
        assertEquals(100, withViewport.viewportColumns());
        assertEquals(30, withViewport.viewportRows());
        assertFalse(ctx.hasViewport());
    }

    @Test
    void cr17_withWidthKeepsTheRest() {
        RenderContext ctx = RenderContext.of(CAPS, Theme.DEFAULT, 80).withViewport(80, 24).withWidth(20);
        assertEquals(20, ctx.width());
        assertTrue(ctx.hasViewport());
    }

    @Test
    void cr17_invalidArgumentsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> RenderContext.of(null, Theme.DEFAULT, 80));
        assertThrows(IllegalArgumentException.class, () -> RenderContext.of(CAPS, null, 80));
        assertThrows(IllegalArgumentException.class, () -> RenderContext.of(CAPS, Theme.DEFAULT, 0));
        RenderContext ctx = RenderContext.of(CAPS, Theme.DEFAULT, 80);
        assertThrows(IllegalArgumentException.class, () -> ctx.withViewport(0, 10));
        assertThrows(IllegalArgumentException.class, () -> ctx.withViewport(10, -1));
    }
}
