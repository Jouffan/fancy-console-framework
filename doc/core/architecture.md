# ConsoleKit Core — Architecture

Part 1 of 4. Requirements: [core/requirements.md](requirements.md).
See [the map](../architecture.md).

Core is a library of **values and one print path**. It has no threads,
no terminal, no lifecycle and MUST NOT depend on the other parts
(CR-43). It is written for them; §4 of the requirements is allowed
knowledge of what they need.

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

`Cell` lives here, not in `canvas` (CR-41): absence means the same thing
everywhere (leave what is underneath). A stored bitmap is slots that
resolve to cells; a canvas back buffer is cells. Putting `Cell` in either
consumer would force the other to convert. Overlay paint is a composite
of these cells, not a memcpy of the stored grid.

## 2. The types

`Color` is the single colour type (CR-40) with named / index / rgb
constructors, the sixteen named constants as static fields, and
depth-downgrade built in. `Style` is fg + bg + attribute flags, immutable.
`Cell` is one glyph plus one `Style` with each of glyph / fg / bg
independently absent; absence composites as "leave what is underneath".

`StyledText` is a line as `(text, style)` spans. `Renderable` is a pure
function `RenderContext -> List<StyledText>` (CR-16). Together they are
the **print** interchange (CR-42): implement `Renderable` and every print
consumer can already use you. Overlay paint composites core `Cell`s; that
is not a third part-to-part type.

`RenderContext` carries capabilities, theme and available width, plus an
optional size the caller fills when it has a current viewport (CR-17).
Nothing reads `Capabilities.size()` for layout.

## 3. The output path (CR-44)

```
Renderable ──► List<StyledText> ──► internal.Ansi ──► String / stream
                                     (the only escape emitter, CR-21)
```

This path belongs to core, not to the text part. Facades (`Text` /
`Styler` / `Snippets`) and any `Renderable` (including a bitmap) use it
to print to `System.out` without owning a second emitter. Capability
gating happens once, here: when escapes can't be rendered the spans are
concatenated unchanged.

`internal.Ansi` is the only class containing escape bytes, `Glyphs` the
only class containing non-ASCII literals, `TerminalPort` the only class
touching JLine (CR-21…CR-23). Source-scan tests enforce all three, plus
the CR-43 package layering (NFR-10).

## 4. Encoding (CR-28…CR-38)

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
