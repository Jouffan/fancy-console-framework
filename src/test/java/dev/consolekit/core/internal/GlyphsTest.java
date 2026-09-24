package dev.consolekit.core.internal;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;

import org.junit.jupiter.api.Test;

import dev.consolekit.core.GlyphTier;

import static org.junit.jupiter.api.Assertions.*;

class GlyphsTest {

    @Test
    void cr10_fixtureIsTheStandardString() {
        assertEquals("příliš žluťoučký kůň", Glyphs.FIXTURE_CZECH);
    }

    @Test
    void cr7_everyGlyphHasEveryTier() {
        for (Glyphs.Glyph g : Glyphs.Glyph.values()) {
            for (GlyphTier tier : GlyphTier.values()) {
                assertFalse(Glyphs.glyph(g, tier).isEmpty(), g + " " + tier);
            }
        }
    }

    @Test
    void cr7_asciiTierIsPureAscii() {
        for (String glyph : Glyphs.candidateGlyphs(GlyphTier.ASCII)) {
            assertTrue(glyph.chars().allMatch(c -> c >= 0x20 && c < 0x7F), glyph);
        }
    }

    @Test
    void cr7_cp437TierEncodesInOemCodePages() {
        for (String name : new String[] {"IBM437", "IBM850", "IBM852", "IBM866"}) {
            CharsetEncoder encoder = Charset.forName(name).newEncoder();
            for (String glyph : Glyphs.candidateGlyphs(GlyphTier.CP437)) {
                assertTrue(encoder.canEncode(glyph), name + " " + glyph);
            }
        }
    }

    @Test
    void cr7_glyphsAreSingleColumnExceptMultiCharacterFallbacks() {
        for (GlyphTier tier : GlyphTier.values()) {
            for (Glyphs.Glyph g : Glyphs.Glyph.values()) {
                String glyph = Glyphs.glyph(g, tier);
                assertEquals(glyph.codePointCount(0, glyph.length()), TextWidth.width(glyph), g + " " + tier);
            }
        }
    }
}
