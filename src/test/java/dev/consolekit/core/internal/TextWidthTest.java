package dev.consolekit.core.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TextWidthTest {

    private static final String FIXTURE = "příliš žluťoučký kůň";

    @Test
    void cr6_asciiIsOneColumnPerCharacter() {
        assertEquals(5, TextWidth.width("hello"));
        assertEquals(0, TextWidth.width(""));
    }

    @Test
    void cr6_cjkIsTwoColumns() {
        assertEquals(10, TextWidth.width("漢字テスト"));
        assertEquals(2, TextWidth.codePointWidth(0x6F22));
    }

    @Test
    void cr6_emojiIsTwoColumns() {
        assertEquals(2, TextWidth.width("👍"));
        assertEquals(2, TextWidth.codePointWidth(0x1F44D));
    }

    @Test
    void cr6_cr35_zwjSequenceIsOneWideGrapheme() {
        assertEquals(2, TextWidth.width("👩‍💻"));
        assertEquals(2, TextWidth.graphemeWidth("👩‍💻"));
    }

    @Test
    void cr6_emojiPresentationSelectorMakesGlyphWide() {
        assertEquals(2, TextWidth.width("❤️"));
        assertEquals(1, TextWidth.width("❤"));
    }

    @Test
    void cr6_regionalIndicatorPairIsOneWideGrapheme() {
        assertEquals(2, TextWidth.width("🇨🇿"));
    }

    @Test
    void cr6_combiningMarkIsZeroWidth() {
        assertEquals(0, TextWidth.codePointWidth(0x0301));
        assertEquals(1, TextWidth.width("é"));
        assertEquals(0, TextWidth.codePointWidth(0x200D));
    }

    @Test
    void cr6_controlCharacterHasNoWidth() {
        assertEquals(0, TextWidth.codePointWidth(0x07));
        assertEquals(0, TextWidth.codePointWidth(0x9B));
    }

    @Test
    void cr6_escapeSequencesAreIgnored() {
        assertEquals(3, TextWidth.width("\u001b[1;31mred\u001b[22;39m"));
        assertEquals(4, TextWidth.width("\u001b]8;;http://x\u0007link\u001b]8;;\u001b\\"));
    }

    @Test
    void cr10_fixtureIsTwentyColumns() {
        assertEquals(20, TextWidth.width(FIXTURE));
        assertEquals(20, TextWidth.width("\u001b[31m" + FIXTURE + "\u001b[39m"));
        assertEquals(20, TextWidth.width(java.text.Normalizer.normalize(FIXTURE, java.text.Normalizer.Form.NFD)));
    }

    @Test
    void cr10_fixtureMixedWithCjkAndEmoji() {
        assertEquals(20 + 1 + 4 + 1 + 2, TextWidth.width(FIXTURE + " 漢字 👍"));
    }

    @Test
    void cr9_tabExpandsToNextStop() {
        assertEquals("ab      c", TextWidth.sanitize("ab\tc", 0));
        assertEquals("  c", TextWidth.sanitize("\tc", 6));
    }

    @Test
    void cr9_lineBreaksBecomeSpaces() {
        assertEquals("a b c", TextWidth.sanitize("a\r\nb\nc", 0));
    }

    @Test
    void cr9_escapeSequencesAreStripped() {
        assertEquals("red", TextWidth.sanitize("\u001b[31mred\u001b[0m", 0));
        assertEquals("x", TextWidth.sanitize("\u009b31mx", 0));
    }

    @Test
    void cr9_otherControlCharactersAreReplaced() {
        assertEquals("a?b?", TextWidth.sanitize("a\u0007b\u007f", 0));
    }

    @Test
    void cr9_loneSurrogateIsReplaced() {
        assertEquals("a?", TextWidth.sanitize("a\uD800", 0));
    }

    @Test
    void cr9_cleanTextIsReturnedAsIs() {
        String clean = "příliš";
        assertSame(clean, TextWidth.sanitize(clean, 0));
    }
}
