package dev.consolekit.core;

/**
 * A text attribute carried by a {@link Style}. Terminals that cannot show one have it dropped by the escape
 * emitter rather than printing garbage.
 *
 * {@snippet :
 * Style emphasis = Style.NONE.bold().underline();
 * boolean loud = emphasis.has(Attr.BOLD);   // true
 * }
 */
public enum Attr {
    BOLD,
    DIM,
    ITALIC,
    UNDERLINE,
    STRIKE,
    REVERSE;

    int bit() {
        return 1 << ordinal();
    }
}
