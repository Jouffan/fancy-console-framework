package dev.consolekit.core;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;

import dev.consolekit.core.internal.TextWidth;

/**
 * One line of text as {@code (text, style)} spans; the print interchange together with {@link Renderable}.
 *
 * <p>Content is made safe on the way in: escape sequences are removed, tabs expanded, line breaks turned
 * into spaces and other control characters replaced, so a line never carries a raw control character to
 * the terminal. A line is immutable.
 *
 * {@snippet :
 * StyledText line = StyledText.builder()
 *     .append("ok ", Style.fg(Color.GREEN).bold())
 *     .append("3 files")
 *     .build();
 * int columns = line.width();   // 10
 * }
 */
public final class StyledText {

    public static final StyledText EMPTY = new StyledText(List.of());

    /**
     * A run of text in one style.
     *
     * {@snippet :
     * for (StyledText.Span span : line.spans()) {
     *     System.out.print(span.text());
     * }
     * }
     */
    public record Span(String text, Style style) {
        public Span {
            if (text == null) throw new IllegalArgumentException("text must not be null");
            if (style == null) throw new IllegalArgumentException("style must not be null");
        }
    }

    /**
     * Assembles a line span by span.
     *
     * {@snippet :
     * StyledText line = StyledText.builder().append("a", Style.NONE.bold()).append("b").build();
     * }
     */
    public static final class Builder {

        private final List<Span> spans = new ArrayList<>();

        private Builder() {
        }

        public Builder append(String text) {
            return append(text, Style.NONE);
        }

        public Builder append(String text, Style style) {
            spans.add(new Span(text, style));
            return this;
        }

        public StyledText build() {
            return StyledText.of(spans);
        }
    }

    private final List<Span> spans;
    private final String text;
    private final int width;

    private StyledText(List<Span> spans) {
        this.spans = spans;
        StringBuilder all = new StringBuilder();
        for (Span span : spans) {
            all.append(span.text());
        }
        this.text = all.toString();
        this.width = TextWidth.width(text);
    }

    public static StyledText of(String text) {
        return of(text, Style.NONE);
    }

    public static StyledText of(String text, Style style) {
        if (text == null) throw new IllegalArgumentException("text must not be null");
        if (style == null) throw new IllegalArgumentException("style must not be null");
        return of(List.of(new Span(text, style)));
    }

    public static StyledText of(List<Span> spans) {
        if (spans == null) throw new IllegalArgumentException("spans must not be null");
        List<Span> clean = new ArrayList<>(spans.size());
        int column = 0;
        for (Span span : spans) {
            if (span == null) throw new IllegalArgumentException("spans must not contain null");
            String text = TextWidth.sanitize(span.text(), column);
            if (text.isEmpty()) continue;
            clean.add(text == span.text() ? span : new Span(text, span.style()));
            column += TextWidth.width(text);
        }
        return clean.isEmpty() ? EMPTY : new StyledText(List.copyOf(clean));
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<Span> spans() {
        return spans;
    }

    /** The text of every span, without styles. */
    public String text() {
        return text;
    }

    /** Display width in columns. */
    public int width() {
        return width;
    }

    public boolean isEmpty() {
        return spans.isEmpty();
    }

    /** This line cut to at most {@code columns}, on grapheme boundaries; a grapheme that would straddle is dropped. */
    StyledText clip(int columns) {
        if (width <= columns) return this;
        List<Span> kept = new ArrayList<>();
        int used = 0;
        BreakIterator graphemes = BreakIterator.getCharacterInstance();
        for (Span span : spans) {
            graphemes.setText(span.text());
            int start = graphemes.first();
            int cut = start;
            for (int end = graphemes.next(); end != BreakIterator.DONE; start = end, end = graphemes.next()) {
                int w = TextWidth.graphemeWidth(span.text().substring(start, end));
                if (used + w > columns) break;
                used += w;
                cut = end;
            }
            if (cut == span.text().length()) {
                kept.add(span);
            } else if (cut > 0) {
                kept.add(new Span(span.text().substring(0, cut), span.style()));
            }
            if (cut < span.text().length()) break;
        }
        return kept.isEmpty() ? EMPTY : new StyledText(List.copyOf(kept));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof StyledText other && other.spans.equals(spans);
    }

    @Override
    public int hashCode() {
        return spans.hashCode();
    }

    @Override
    public String toString() {
        return text;
    }
}
