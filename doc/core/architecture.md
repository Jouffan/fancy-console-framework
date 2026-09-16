# ConsoleKit Core — Architecture

Part 1 of 4. Requirements: [core/requirements.md](requirements.md).
See [the map](../architecture.md).

Core is a library of **values and one output path**. It has no threads,
no terminal, no lifecycle and no knowledge of the other parts (CR-43).

## 1. Packages

```
dev.consolekit.core       Color, Style, Attr, Cell, Capabilities, GlyphTier,
                          StyledText, Theme, RenderContext, ConsoleOptions
dev.consolekit.render     Renderable                  (the static contract)
dev.consolekit.widget     Table, Box, KeyValueBlock, Tree, Sparkline,
                          Rule, PatternHighlighter, Border
dev.consolekit            Probe                       (diagnostics)
                          ConsoleRuntime              (the one static holder)
dev.consolekit.internal   NOT exported. Ansi, Glyphs, TextWidth, Encoding,
                          TerminalPort
```

`Cell` lives here, not in `canvas` (CR-41): the canvas back buffer and an
`AsciiBitmap` are the same grid of the same type, and absence means the
same thing in both. Putting it in either consumer would force the other
to convert.

## 2. The types

`Color` is the single colour type (CR-40) with named / index / rgb
constructors, the sixteen named constants as static fields, and
depth-downgrade built in. `Style` is fg + bg + attribute flags, immutable.
`Cell` is one glyph plus one `Style` with each of glyph / fg / bg
independently absent; absence composites as "leave what is underneath".

`StyledText` is a line as `(text, style)` spans. `Renderable` is a pure
function `RenderContext -> List<StyledText>` (CR-16). Together they are
the *only* interchange between parts (CR-42): implement `Renderable` and
every consumer in the library can already print or blit you.

`RenderContext` carries capabilities, theme and available width — and, in
canvas mode, the per-frame canvas size (CR-17). Nothing reads
`Capabilities.size()` for layout.

## 3. The output path (CR-44)

```
Renderable ──► List<StyledText> ──► internal.Ansi ──► String / stream
                                     (the only escape emitter, CR-21)
```

This path belongs to core, not to the text part. `Text`/`Styler`/
`Snippets` are a facade over it (TX-1), and an `AsciiBitmap` uses the
same path to print to `System.out` (BM-4) without importing the text
part. Capability gating happens once, here: when escapes can't be
rendered the spans are concatenated unchanged (TX-3).

`internal.Ansi` is the only class containing escape bytes, `Glyphs` the
only class containing non-ASCII literals, `TerminalPort` the only class
touching JLine (CR-21…CR-23). Source-scan tests enforce all three, plus
the CR-43 package layering (NFR-10).

## 4. Encoding (CR-28…CR-39)

`Encoding.forOutput(caps)` resolves the charset once, then
`substitute(text)` does NFC normalise → encodable check →
transliterate-or-replace, width-preserving. The rule everywhere is
**substitute, then measure, then place** — never measure one string and
emit another. `TextWidth` measures graphemes, ignoring escapes.

The glyph side (`Glyphs.candidateGlyphs(tier)` probed against the console
encoder) is done; the content side is M5.

## 5. Test support

`VirtualTerminal` (in `internal`, used by canvas tests) and the golden
files under `src/test/resources/golden/` are shared by every part. Any
part may assert through `Renderable → String`; only canvas needs the
terminal emulator.
