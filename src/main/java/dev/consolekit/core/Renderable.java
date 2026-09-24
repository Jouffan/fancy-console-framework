package dev.consolekit.core;

import java.util.List;

/**
 * Anything that can be printed: a pure function from a {@link RenderContext} to styled lines. Same context,
 * same state, same output; no cursor, no global state, no I/O. Implement this and the content can be
 * printed as a {@code String}, into scrollback, or blitted, without knowing which.
 *
 * <p>A shipped implementation's {@code toString()} is {@code Rendering.toString(this)}.
 *
 * {@snippet :
 * final class Greeting implements Renderable {
 *     @Override
 *     public List<StyledText> render(RenderContext ctx) {
 *         return List.of(StyledText.of("hello", ctx.theme().style(Theme.Role.HIGHLIGHT)));
 *     }
 *
 *     @Override
 *     public String toString() {
 *         return Rendering.toString(this);
 *     }
 * }
 * }
 */
@FunctionalInterface
public interface Renderable {

    List<StyledText> render(RenderContext ctx);
}
