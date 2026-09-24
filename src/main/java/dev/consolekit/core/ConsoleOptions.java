package dev.consolekit.core;

import java.util.Optional;

/**
 * Explicit configuration, which outranks everything the environment says. Set at most once per process,
 * before any output, through {@link ConsoleRuntime#configure}.
 *
 * {@snippet :
 * ConsoleRuntime.configure(ConsoleOptions.builder()
 *     .colorDepth(ColorDepth.ANSI256)
 *     .glyphTier(GlyphTier.ASCII)
 *     .build());
 * }
 */
public final class ConsoleOptions {

    /**
     * Collects the options; anything not set is left to the environment.
     *
     * {@snippet :
     * ConsoleOptions options = ConsoleOptions.builder().glyphTier(GlyphTier.CP437).build();
     * }
     */
    public static final class Builder {

        private ColorDepth colorDepth;
        private GlyphTier glyphTier;

        private Builder() {
        }

        public Builder colorDepth(ColorDepth colorDepth) {
            if (colorDepth == null) throw new IllegalArgumentException("colorDepth must not be null");
            this.colorDepth = colorDepth;
            return this;
        }

        public Builder glyphTier(GlyphTier glyphTier) {
            if (glyphTier == null) throw new IllegalArgumentException("glyphTier must not be null");
            this.glyphTier = glyphTier;
            return this;
        }

        public ConsoleOptions build() {
            return new ConsoleOptions(Optional.ofNullable(colorDepth), Optional.ofNullable(glyphTier));
        }
    }

    private final Optional<ColorDepth> colorDepth;
    private final Optional<GlyphTier> glyphTier;

    private ConsoleOptions(Optional<ColorDepth> colorDepth, Optional<GlyphTier> glyphTier) {
        this.colorDepth = colorDepth;
        this.glyphTier = glyphTier;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<ColorDepth> colorDepth() {
        return colorDepth;
    }

    public Optional<GlyphTier> glyphTier() {
        return glyphTier;
    }
}
