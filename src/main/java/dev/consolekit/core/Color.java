package dev.consolekit.core;

/**
 * The one colour type of the library: a named ANSI colour, a 256-palette index, a 24-bit RGB value, or
 * {@link #DEFAULT}, the terminal's own default foreground or background.
 *
 * <p>{@code DEFAULT} is a colour and is emitted explicitly. A {@link Style} channel that holds no colour
 * at all is <em>unset</em>, which is a different thing: on the paint door it means "keep what is underneath".
 *
 * {@snippet :
 * Style warning = Style.fg(Color.YELLOW).withBg(Color.DEFAULT);
 * Color accent = Color.rgb(255, 128, 0);
 * Color shown = accent.downgrade(ColorDepth.ANSI256);   // Color.index(208)
 * }
 */
public final class Color {

    /**
     * The sixteen ANSI colours, in SGR order.
     *
     * {@snippet :
     * Color c = Color.named(Color.Named.BRIGHT_CYAN);   // same as Color.BRIGHT_CYAN
     * }
     */
    public enum Named {
        BLACK, RED, GREEN, YELLOW, BLUE, MAGENTA, CYAN, WHITE,
        BRIGHT_BLACK, BRIGHT_RED, BRIGHT_GREEN, BRIGHT_YELLOW,
        BRIGHT_BLUE, BRIGHT_MAGENTA, BRIGHT_CYAN, BRIGHT_WHITE
    }

    /**
     * What a colour is made of, for code that has to spell it out (the escape emitter).
     *
     * {@snippet :
     * if (color.kind() == Color.Kind.RGB) {
     *     int r = color.red();
     * }
     * }
     */
    public enum Kind {
        DEFAULT, NAMED, INDEX, RGB
    }

    static final int ABSENT = 0;
    private static final int KIND_DEFAULT = 1;
    private static final int KIND_NAMED = 2;
    private static final int KIND_INDEX = 3;
    private static final int KIND_RGB = 4;

    public static final Color DEFAULT = new Color(KIND_DEFAULT, 0);

    public static final Color BLACK = new Color(KIND_NAMED, 0);
    public static final Color RED = new Color(KIND_NAMED, 1);
    public static final Color GREEN = new Color(KIND_NAMED, 2);
    public static final Color YELLOW = new Color(KIND_NAMED, 3);
    public static final Color BLUE = new Color(KIND_NAMED, 4);
    public static final Color MAGENTA = new Color(KIND_NAMED, 5);
    public static final Color CYAN = new Color(KIND_NAMED, 6);
    public static final Color WHITE = new Color(KIND_NAMED, 7);
    public static final Color BRIGHT_BLACK = new Color(KIND_NAMED, 8);
    public static final Color BRIGHT_RED = new Color(KIND_NAMED, 9);
    public static final Color BRIGHT_GREEN = new Color(KIND_NAMED, 10);
    public static final Color BRIGHT_YELLOW = new Color(KIND_NAMED, 11);
    public static final Color BRIGHT_BLUE = new Color(KIND_NAMED, 12);
    public static final Color BRIGHT_MAGENTA = new Color(KIND_NAMED, 13);
    public static final Color BRIGHT_CYAN = new Color(KIND_NAMED, 14);
    public static final Color BRIGHT_WHITE = new Color(KIND_NAMED, 15);

    private static final Color[] NAMED = {
        BLACK, RED, GREEN, YELLOW, BLUE, MAGENTA, CYAN, WHITE,
        BRIGHT_BLACK, BRIGHT_RED, BRIGHT_GREEN, BRIGHT_YELLOW,
        BRIGHT_BLUE, BRIGHT_MAGENTA, BRIGHT_CYAN, BRIGHT_WHITE,
    };

    // xterm's defaults; the reference for "nearest" when a palette colour has to be matched
    private static final int[] NAMED_RGB = {
        0x000000, 0xCD0000, 0x00CD00, 0xCDCD00, 0x0000EE, 0xCD00CD, 0x00CDCD, 0xE5E5E5,
        0x7F7F7F, 0xFF0000, 0x00FF00, 0xFFFF00, 0x5C5CFF, 0xFF00FF, 0x00FFFF, 0xFFFFFF,
    };

    private static final int[] CUBE_LEVELS = {0, 95, 135, 175, 215, 255};

    private static final Color[] INDEXED = indexed();

    private final int kind;
    private final int payload;

    private Color(int kind, int payload) {
        this.kind = kind;
        this.payload = payload;
    }

    private static Color[] indexed() {
        Color[] all = new Color[256];
        for (int i = 0; i < all.length; i++) {
            all[i] = new Color(KIND_INDEX, i);
        }
        return all;
    }

    public static Color named(Named name) {
        if (name == null) throw new IllegalArgumentException("name must not be null");
        return NAMED[name.ordinal()];
    }

    public static Color index(int index) {
        if (index < 0 || index > 255) throw new IllegalArgumentException("index must be 0..255: " + index);
        return INDEXED[index];
    }

    public static Color rgb(int red, int green, int blue) {
        if (red < 0 || red > 255) throw new IllegalArgumentException("red must be 0..255: " + red);
        if (green < 0 || green > 255) throw new IllegalArgumentException("green must be 0..255: " + green);
        if (blue < 0 || blue > 255) throw new IllegalArgumentException("blue must be 0..255: " + blue);
        return new Color(KIND_RGB, red << 16 | green << 8 | blue);
    }

    public Kind kind() {
        return switch (kind) {
            case KIND_DEFAULT -> Kind.DEFAULT;
            case KIND_NAMED -> Kind.NAMED;
            case KIND_INDEX -> Kind.INDEX;
            default -> Kind.RGB;
        };
    }

    /** The palette code: 0..15 for a named colour, 0..255 for an index. */
    public int code() {
        if (kind != KIND_NAMED && kind != KIND_INDEX) throw new IllegalStateException("no palette code: " + this);
        return payload;
    }

    /** The red component; for palette colours, xterm's default for that entry. */
    public int red() {
        return approximateRgb() >> 16 & 0xFF;
    }

    /** The green component; for palette colours, xterm's default for that entry. */
    public int green() {
        return approximateRgb() >> 8 & 0xFF;
    }

    /** The blue component; for palette colours, xterm's default for that entry. */
    public int blue() {
        return approximateRgb() & 0xFF;
    }

    /**
     * This colour as the nearest one {@code depth} can show. {@link #DEFAULT} is kept at every depth, and at
     * {@link ColorDepth#NONE} every colour becomes {@code DEFAULT}.
     */
    public Color downgrade(ColorDepth depth) {
        if (kind == KIND_DEFAULT) return this;
        return switch (depth) {
            case NONE -> DEFAULT;
            case TRUECOLOR -> this;
            case ANSI256 -> kind == KIND_RGB ? INDEXED[cubeIndex(payload)] : this;
            case ANSI16 -> toNamed();
        };
    }

    private Color toNamed() {
        if (kind == KIND_NAMED) return this;
        if (kind == KIND_INDEX && payload < 16) return NAMED[payload];
        return NAMED[nearestNamed(approximateRgb())];
    }

    private int approximateRgb() {
        return switch (kind) {
            case KIND_NAMED -> NAMED_RGB[payload];
            case KIND_INDEX -> indexRgb(payload);
            case KIND_RGB -> payload;
            default -> throw new IllegalStateException("no RGB value: " + this);
        };
    }

    private static int indexRgb(int index) {
        if (index < 16) return NAMED_RGB[index];
        if (index >= 232) {
            int gray = 8 + 10 * (index - 232);
            return gray << 16 | gray << 8 | gray;
        }
        int cube = index - 16;
        return CUBE_LEVELS[cube / 36] << 16 | CUBE_LEVELS[cube / 6 % 6] << 8 | CUBE_LEVELS[cube % 6];
    }

    private static int cubeIndex(int rgb) {
        return 16 + 36 * cubeLevel(rgb >> 16 & 0xFF) + 6 * cubeLevel(rgb >> 8 & 0xFF) + cubeLevel(rgb & 0xFF);
    }

    private static int cubeLevel(int component) {
        if (component < 48) return 0;
        if (component < 115) return 1;
        return (component - 35) / 40;
    }

    // "redmean" weighting: cheap, and much closer to perceived distance than plain RGB
    private static int nearestNamed(int rgb) {
        int best = 0;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < NAMED_RGB.length; i++) {
            long d = distance(rgb, NAMED_RGB[i]);
            if (d < bestDistance) {
                bestDistance = d;
                best = i;
            }
        }
        return best;
    }

    private static long distance(int a, int b) {
        int r1 = a >> 16 & 0xFF;
        int r2 = b >> 16 & 0xFF;
        long meanRed = (r1 + r2) / 2;
        long dr = r1 - r2;
        long dg = (a >> 8 & 0xFF) - (b >> 8 & 0xFF);
        long db = (a & 0xFF) - (b & 0xFF);
        return ((512 + meanRed) * dr * dr >> 8) + 4 * dg * dg + ((767 - meanRed) * db * db >> 8);
    }

    int packed() {
        return kind << 24 | payload;
    }

    static Color unpack(int packed) {
        int payload = packed & 0xFFFFFF;
        return switch (packed >>> 24) {
            case KIND_DEFAULT -> DEFAULT;
            case KIND_NAMED -> NAMED[payload];
            case KIND_INDEX -> INDEXED[payload];
            case KIND_RGB -> new Color(KIND_RGB, payload);
            default -> throw new IllegalArgumentException("not a packed colour: " + packed);
        };
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Color other && other.kind == kind && other.payload == payload;
    }

    @Override
    public int hashCode() {
        return packed();
    }

    @Override
    public String toString() {
        return switch (kind) {
            case KIND_DEFAULT -> "Color.DEFAULT";
            case KIND_NAMED -> "Color." + Named.values()[payload].name();
            case KIND_INDEX -> "Color.index(" + payload + ")";
            default -> "Color.rgb(" + (payload >> 16) + ", " + (payload >> 8 & 0xFF) + ", " + (payload & 0xFF) + ")";
        };
    }
}
