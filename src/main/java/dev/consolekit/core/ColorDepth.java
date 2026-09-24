package dev.consolekit.core;

/**
 * How many colours the output can show, from none to 24-bit. Every {@link Color} can
 * {@linkplain Color#downgrade(ColorDepth) downgrade} itself to a lower depth.
 *
 * {@snippet :
 * ColorDepth depth = ConsoleRuntime.capabilities().colorDepth();
 * Color shown = Color.rgb(255, 128, 0).downgrade(depth);
 * }
 */
public enum ColorDepth {
    NONE,
    ANSI16,
    ANSI256,
    TRUECOLOR
}
