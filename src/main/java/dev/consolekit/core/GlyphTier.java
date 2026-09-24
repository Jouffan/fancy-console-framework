package dev.consolekit.core;

/**
 * The character repertoire the output charset can actually encode: Unicode box drawing and marks, the
 * box-drawing subset shared by the OEM code pages, or plain ASCII.
 *
 * {@snippet :
 * if (ConsoleRuntime.capabilities().glyphTier() == GlyphTier.ASCII) {
 *     // draw frames with '+', '-' and '|'
 * }
 * }
 */
public enum GlyphTier {
    FULL,
    CP437,
    ASCII
}
