# ConsoleKit Core — Architecture

Part 1 of 4. Requirements: [core/requirements.md](requirements.md).
See [the map](../architecture.md).

Core is a library of **values and one print path**. It has no threads,
no terminal, no lifecycle and MUST NOT depend on the other parts
(CR-43). It is written for them; §4 of the requirements is allowed
knowledge of what they need.

## 1. Packages

Artefact `consolekit-core`, module `dev.consolekit.core`. No third-party
dependency — not even JLine (NFR-8); the terminal lives in canvas.

```
dev.consolekit.core          Color, Style, Attr, Cell, Capabilities, GlyphTier,
                             StyledText, Theme, RenderContext, ConsoleOptions,
                             ConsoleRuntime  (the one static holder)
                             Probe           (diagnostics)
dev.consolekit.core.render   Renderable                  (the static contract)
dev.consolekit.core.widget   Table, Box, KeyValueBlock, Tree, Sparkline,
                             Rule, PatternHighlighter, Border
dev.consolekit.core.internal NOT exported. Ansi, Glyphs, TextWidth, Encoding
```

`ConsoleRuntime` holds process-wide mutable state (NFR-12) but MUST NOT
know what that state *is*: it offers an **opaque slot** that a consumer
claims and reads back through its own type. The canvas session lives in
that slot without core ever naming a canvas type — otherwise core would
depend on canvas and CR-43 would be a compile error.

`Probe` reports what **core** resolved: colour depth, glyph tier,
charsets, code page, the test card (CR-15, CR-38). It does not dump
`.art`; that is `bitmap.ArtProbe` (AF-7), because core cannot see the
bitmap part.

`Capabilities` is resolved from the JDK and the environment only —
`System.console()`, `NO_COLOR`, `TERM`, charsets. The **probe-time size**
(CR-12) is therefore optional: absent unless something with a terminal
fills it. Layout never reads it anyway (CR-17).

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
Renderable ──► List<StyledText> ──► core.internal.Ansi ──► String / stream
                                     (the only escape emitter, CR-21)
```

This path belongs to core, not to the text part. Facades (`Text` /
`Styler` / `Snippets`) and any `Renderable` (including a bitmap) use it
to print to `System.out` without owning a second emitter. Capability
gating happens once, here: when escapes can't be rendered the spans are
concatenated unchanged.

`core.internal.Ansi` is the only class containing escape bytes and
`core.internal.Glyphs` the only class containing non-ASCII literals
(CR-21, CR-23); source-scan tests enforce both, scoped to `src/main`.
The CR-22 JLine monopoly is `canvas.internal.TerminalPort` \u2014 core has no
JLine dependency at all. CR-43 needs no scan here: it is a compile error
(NFR-10).

## 4. Encoding (CR-28…CR-38)

`Encoding.forOutput(caps)` resolves the charset once, then
`substitute(text)` does NFC normalise → encodable check →
transliterate-or-replace, width-preserving. The rule everywhere is
**substitute, then measure, then place** — never measure one string and
emit another. `TextWidth` measures graphemes, ignoring escapes.

The glyph side (`Glyphs.candidateGlyphs(tier)` probed against the console
encoder) is done; the content side is M5.

## 5. Test support

The golden files under `src/test/resources/golden/` are the shared
assertion surface, and any part may assert through `Renderable → String`
with no terminal. The virtual terminal emulator is **canvas-only**
(`canvas.internal.VirtualTerminal`, NFR-3); core cannot see it and does
not need it.
