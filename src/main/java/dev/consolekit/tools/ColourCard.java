package dev.consolekit.tools;

import java.util.ArrayList;
import java.util.List;

import dev.consolekit.core.Capabilities;
import dev.consolekit.core.Color;
import dev.consolekit.core.GlyphTier;
import dev.consolekit.core.RenderContext;
import dev.consolekit.core.Renderable;
import dev.consolekit.core.Rendering;
import dev.consolekit.core.Style;
import dev.consolekit.core.StyledText;
import dev.consolekit.core.Theme;
import dev.consolekit.core.internal.Glyphs;
import dev.consolekit.core.internal.Glyphs.Glyph;
import dev.consolekit.core.internal.TextWidth;

/**
 * A colour and glyph card for checking the core print path by eye on a real terminal: the resolved
 * capabilities, the sixteen named colours, {@link Color#DEFAULT} next to unstyled text, the attributes, a
 * colour ramp, the glyph tier, the width fixtures, and a ruler exactly as wide as the ambient width.
 *
 * {@snippet :
 * // java -cp target/classes dev.consolekit.tools.ColourCard
 * System.out.println(new ColourCard());
 * }
 */
public final class ColourCard implements Renderable {

    private static final int SWATCH = 16;

    public static void main(String[] args) {
        Rendering.print(new ColourCard(), System.out);
    }

    @Override
    public List<StyledText> render(RenderContext ctx) {
        Capabilities caps = ctx.capabilities();
        Style heading = ctx.theme().style(Theme.Role.HIGHLIGHT);
        List<StyledText> lines = new ArrayList<>();

        lines.add(StyledText.of("ConsoleKit colour card", heading));
        lines.add(StyledText.of("depth " + caps.colorDepth() + ", glyphs " + caps.glyphTier()
            + ", tty " + yes(caps.isTty()) + ", escapes " + yes(caps.canEmitEscapes()) + ", width " + ctx.width()));
        lines.add(StyledText.of("charset out " + caps.outputCharset().name() + ", in " + caps.inputCharset().name()));
        for (String reason : caps.reasons()) {
            lines.add(StyledText.of("degraded: " + reason, ctx.theme().style(Theme.Role.WARNING)));
        }

        lines.add(StyledText.EMPTY);
        lines.add(StyledText.of("Named foreground", heading));
        swatches(lines, ctx, false);
        lines.add(StyledText.of("Named background", heading));
        swatches(lines, ctx, true);

        lines.add(StyledText.of("Color.DEFAULT (each pair must look identical)", heading));
        lines.add(StyledText.builder()
            .append("default fg", Style.fg(Color.DEFAULT))
            .append(" | ")
            .append("default fg")
            .build());
        lines.add(StyledText.builder()
            .append("[")
            .append("          ", Style.bg(Color.DEFAULT))
            .append("] | [          ]")
            .build());

        lines.add(StyledText.of("Attributes", heading));
        lines.add(StyledText.builder()
            .append("bold", Style.NONE.bold()).append(" ")
            .append("dim", Style.NONE.dim()).append(" ")
            .append("italic", Style.NONE.italic()).append(" ")
            .append("underline", Style.NONE.underline()).append(" ")
            .append("strike", Style.NONE.strike()).append(" ")
            .append("reverse", Style.NONE.reverse())
            .build());

        lines.add(StyledText.of("Colour ramp (downgraded to " + caps.colorDepth() + ")", heading));
        lines.add(ramp(ctx.width()));

        lines.add(StyledText.of("Glyph tier " + caps.glyphTier(), heading));
        glyphBox(lines, ctx);

        lines.add(StyledText.of("Width fixtures (right edges must line up)", heading));
        String bar = Glyphs.glyph(Glyph.V_LINE, caps.glyphTier());
        fixture(lines, bar, Glyphs.FIXTURE_CZECH);
        fixture(lines, bar, Glyphs.FIXTURE_CJK);
        fixture(lines, bar, Glyphs.FIXTURE_EMOJI + " " + Glyphs.FIXTURE_ZWJ);

        lines.add(StyledText.of("Ruler (exactly the width, must not wrap)", heading));
        lines.add(ruler(ctx));
        return lines;
    }

    private static String yes(boolean b) {
        return b ? "yes" : "no";
    }

    private static void swatches(List<StyledText> lines, RenderContext ctx, boolean background) {
        int perLine = Math.max(1, ctx.width() / SWATCH);
        Color.Named[] names = Color.Named.values();
        StyledText.Builder line = StyledText.builder();
        for (int i = 0; i < names.length; i++) {
            Color c = Color.named(names[i]);
            String label = pad(names[i].name(), SWATCH);
            Style s = background ? Style.bg(c).withFg(contrast(c)) : Style.fg(c);
            line.append(label, s);
            if ((i + 1) % perLine == 0 || i == names.length - 1) {
                lines.add(line.build());
                line = StyledText.builder();
            }
        }
    }

    private static Color contrast(Color c) {
        int luma = c.red() * 299 + c.green() * 587 + c.blue() * 114;
        return luma > 128_000 ? Color.BLACK : Color.BRIGHT_WHITE;
    }

    private static StyledText ramp(int width) {
        StyledText.Builder line = StyledText.builder();
        int n = Math.max(1, width);
        for (int i = 0; i < n; i++) {
            int hue = i * 767 / n;
            int r = hue < 256 ? 255 - hue : hue < 512 ? 0 : hue - 512;
            int g = hue < 256 ? hue : hue < 512 ? 511 - hue : 0;
            int b = hue < 256 ? 0 : hue < 512 ? hue - 256 : 767 - hue;
            line.append(" ", Style.bg(Color.rgb(clamp(r), clamp(g), clamp(b))));
        }
        return line.build();
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static void glyphBox(List<StyledText> lines, RenderContext ctx) {
        GlyphTier tier = ctx.capabilities().glyphTier();
        StringBuilder content = new StringBuilder(" ");
        for (Glyph g : new Glyph[] {Glyph.LIGHT_SHADE, Glyph.MEDIUM_SHADE, Glyph.DARK_SHADE, Glyph.FULL_BLOCK}) {
            content.append(Glyphs.glyph(g, tier));
        }
        for (Glyph g : new Glyph[] {Glyph.BULLET, Glyph.ELLIPSIS, Glyph.CHECK, Glyph.CROSS_MARK, Glyph.ARROW_RIGHT}) {
            content.append(' ').append(Glyphs.glyph(g, tier));
        }
        content.append(' ');
        int inner = TextWidth.width(content);
        String h = Glyphs.glyph(Glyph.H_LINE, tier).repeat(inner);
        String v = Glyphs.glyph(Glyph.V_LINE, tier);
        Style border = ctx.theme().style(Theme.Role.BORDER);
        lines.add(StyledText.of(Glyphs.glyph(Glyph.TOP_LEFT, tier) + h + Glyphs.glyph(Glyph.TOP_RIGHT, tier), border));
        lines.add(StyledText.builder().append(v, border).append(content.toString()).append(v, border).build());
        lines.add(StyledText.of(Glyphs.glyph(Glyph.BOTTOM_LEFT, tier) + h + Glyphs.glyph(Glyph.BOTTOM_RIGHT, tier),
            border));
    }

    private static void fixture(List<StyledText> lines, String bar, String sample) {
        int width = TextWidth.width(sample);
        lines.add(StyledText.of(bar + pad(sample, 20) + bar + " " + width + " columns"));
    }

    private static String pad(String s, int columns) {
        int missing = columns - TextWidth.width(s);
        return missing > 0 ? s + " ".repeat(missing) : s;
    }

    private static StyledText ruler(RenderContext ctx) {
        int width = ctx.width();
        if (width < 2) return StyledText.of("+".repeat(width));
        StringBuilder middle = new StringBuilder();
        for (int i = 1; i < width - 1; i++) {
            middle.append(i % 10 == 0 ? Character.forDigit(i / 10 % 10, 10) : '-');
        }
        return StyledText.of("+" + middle + "+");
    }

    @Override
    public String toString() {
        return Rendering.toString(this);
    }
}
