package dev.consolekit.core;

/**
 * Everything a {@link Renderable} may look at: resolved capabilities, the theme, and the available width,
 * plus an optional viewport size that the caller fills when it has a current one. Nothing else is current;
 * a renderable never reads process-wide state for layout.
 *
 * {@snippet :
 * RenderContext ctx = RenderContext.of(ConsoleRuntime.capabilities(), Theme.DEFAULT, 60);
 * RenderContext inCanvas = ctx.withViewport(120, 40);
 * List<StyledText> lines = table.render(ctx);
 * }
 */
public final class RenderContext {

    private final Capabilities capabilities;
    private final Theme theme;
    private final int width;
    private final int viewportColumns;
    private final int viewportRows;

    private RenderContext(Capabilities capabilities, Theme theme, int width, int viewportColumns, int viewportRows) {
        this.capabilities = capabilities;
        this.theme = theme;
        this.width = width;
        this.viewportColumns = viewportColumns;
        this.viewportRows = viewportRows;
    }

    public static RenderContext of(Capabilities capabilities, Theme theme, int width) {
        if (capabilities == null) throw new IllegalArgumentException("capabilities must not be null");
        if (theme == null) throw new IllegalArgumentException("theme must not be null");
        if (width <= 0) throw new IllegalArgumentException("width must be > 0: " + width);
        return new RenderContext(capabilities, theme, width, 0, 0);
    }

    public RenderContext withWidth(int width) {
        if (width <= 0) throw new IllegalArgumentException("width must be > 0: " + width);
        return new RenderContext(capabilities, theme, width, viewportColumns, viewportRows);
    }

    /** This context with the caller's current viewport size. */
    public RenderContext withViewport(int columns, int rows) {
        if (columns <= 0) throw new IllegalArgumentException("columns must be > 0: " + columns);
        if (rows <= 0) throw new IllegalArgumentException("rows must be > 0: " + rows);
        return new RenderContext(capabilities, theme, width, columns, rows);
    }

    public Capabilities capabilities() {
        return capabilities;
    }

    public Theme theme() {
        return theme;
    }

    public int width() {
        return width;
    }

    public boolean hasViewport() {
        return viewportColumns > 0;
    }

    /** Viewport columns, or 0 when the caller supplied no viewport. */
    public int viewportColumns() {
        return viewportColumns;
    }

    /** Viewport rows, or 0 when the caller supplied no viewport. */
    public int viewportRows() {
        return viewportRows;
    }
}
