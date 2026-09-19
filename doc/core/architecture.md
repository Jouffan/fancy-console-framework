# ConsoleKit Core — Architecture

Part 1 of 4. Requirements: [core/requirements.md](requirements.md).
See [the map](../architecture.md).

Core is a library of **values, one grid, and one print path**. It has no
threads, no terminal, no terminal library, no lifecycle, and MUST NOT
depend on the other parts (CR-43). It is written for them; §4 of the
requirements is allowed knowledge of what they need.

## 1. Packages

```
dev.consolekit.core            Color, Style, Attr, Cell, CellBuffer,
                               Capabilities, GlyphTier, StyledText, Theme,
                               RenderContext, ConsoleOptions,
                               Renderable, Rendering, ConsoleRuntime
dev.consolekit.core.widget     Table, Box, KeyValueBlock, Tree, Sparkline,
                               Rule, PatternHighlighter, Border
dev.consolekit.core.internal   NOT exported. Ansi, Glyphs, TextWidth,
                               Encoding, GraphemeTable
```

`Probe` is not here: it is an application in `dev.consolekit.tools`
(CR-15, CR-43). `TerminalPort` is not here either: JLine belongs to
canvas (CR-22).

## 2. The value types

`Color` is the single colour type (CR-40): named / index / rgb
constructors, the sixteen named constants as static fields,
`Color.DEFAULT`, and depth-downgrade built in.

`Style` is fg + bg + attribute flags, immutable; each colour channel may
be unset. Unset is not `DEFAULT`:

| Channel state | Print door (CR-42) | Paint door (CR-46) |
|---|---|---|
| unset | resolves to `DEFAULT` | transparent — keep destination |
| `Color.DEFAULT` | SGR 39 / 49 | opaque, terminal default |
| explicit colour | that colour, downgraded | that colour, downgraded |

`Cell` is `(grapheme | absent, Style)` — a small immutable value used at
API boundaries: `AsciiBitmap.generate`, `Surface.put`, test assertions.
`Cell.of('#', style)`, `Cell.of("é", style)`, `Cell.TRANSPARENT`.
Construction validates that the glyph is exactly one grapheme and
records its width (1 or 2).

`StyledText` is a line as `(text, style)` spans. `Renderable` is a pure
function `RenderContext -> List<StyledText>` (CR-16). Together they are
the **print** interchange (CR-42).

`RenderContext` carries capabilities, theme and available width, plus an
optional size the caller fills when it has a current viewport (CR-17).
Nothing reads `Capabilities.size()` for layout.

## 3. `CellBuffer` — the grid and the compositor (CR-46)

The representation is packed so that paint allocates nothing:

```
int[]   glyph   ≥ 1   a Unicode code point
                  0   absent
                 -1   continuation of the wide cell to the left
               ≤ -2   id of an interned multi-code-point cluster
int[]   fg, bg  kind << 24 | payload
                kind 0 absent · 1 DEFAULT · 2 named · 3 index · 4 rgb
short[] attrs   the six CR-1 flags
```

Multi-code-point graphemes (ZWJ emoji, sequences NFC cannot compose) are
interned in `core.internal.GraphemeTable`: append-only, bounded, hung off
`ConsoleRuntime` (NFR-12). Interning allocates the first time a cluster
is seen and never again; when the bound is hit the cluster is replaced
per CR-31. Bitmap glyphs never need it (BM-2: one code point, width 1).

API, all non-allocating except where noted:

```java
static CellBuffer of(int w, int h);              // all TRANSPARENT
int  width();  int height();
void clear();                                    // all TRANSPARENT
void fill(Cell c);                               // e.g. blank: ' ' + DEFAULT/DEFAULT
void put(int x, int y, Cell c);
void put(int x, int y, int codePoint, Style s);  // hot-path overload
void composite(CellBuffer src, int x, int y,
               int clipX, int clipY, int clipW, int clipH);
Cell get(int x, int y);                          // MAY allocate: tests, dumps
String dump();                                   // glyphs only, '\n' rows
```

`composite` is the only implementation of the CR-41 rule:

```
for each src cell inside the clip:
    if src.glyph present:  dst.glyph = src.glyph; dst.attrs = src.attrs
    if src.fg    present:  dst.fg    = src.fg
    if src.bg    present:  dst.bg    = src.bg
    touching either half of a wide dst cell blanks the other half
```

A canvas back buffer is filled with *blank* (space, `DEFAULT`,
`DEFAULT`) before painting, so after compositing every cell is fully
resolved and flush never sees absence. A bitmap scratch buffer is
`clear()`ed to transparent before resolution, so absence survives to the
composite. Same type, same rule, different starting fill.

## 4. The output path (CR-44)

```
Renderable ──► List<StyledText> ──► core.internal.Ansi ──► String / stream
                                     (the only escape emitter, CR-21)
```

`Rendering` is the named entry point:

```java
static String toString(Renderable r);                     // ambient context
static String toString(Renderable r, RenderContext ctx);
static void   print(Renderable r, PrintStream out);
```

The ambient context is resolved capabilities for stdout, `Theme.DEFAULT`
and the CR-45 width. It is built here, outside `render(ctx)`, which is
why CR-17's purity rule is not violated. Every shipped `Renderable` has
`toString() { return Rendering.toString(this); }`; a reflective test
walks the shipped implementations and fails on any that does not.

Facades (`Text` / `Styler` / `Snippets`) and any `Renderable` (including
a bitmap) use this path to print to `System.out` without owning a second
emitter. Capability gating happens once, here: when escapes can't be
rendered the spans are concatenated unchanged.

`Ansi` is the only class containing escape bytes, `Glyphs` the only
class containing non-ASCII literals (CR-21, CR-23). Source-scan tests
enforce both, plus "no `org.jline` outside `dev.consolekit.canvas`"
(CR-22) and the CR-43 prefix rule (NFR-10).

## 5. `ConsoleRuntime` — the one static holder (NFR-12)

Holds the resolved `Capabilities`, the once-settable `ConsoleOptions`
(CR-14), the `GraphemeTable`, and an opaque session slot:

```java
static <T> T session(Class<T> type, Supplier<T> opener);  // open-once
static <T> Optional<T> sessionIfOpen(Class<T> type);
```

Canvas stores its process-wide engine there under its own type. Core
never names that type, so the holder stays in core without a core →
canvas edge.

## 6. Encoding (CR-28…CR-38)

`Encoding.forOutput(caps)` resolves the charset once, then
`substitute(text)` does NFC normalise → encodable check →
transliterate-or-replace, width-preserving. The rule everywhere is
**substitute, then measure, then place** — never measure one string and
emit another. `TextWidth` measures graphemes, ignoring escapes.

The glyph side (`Glyphs.candidateGlyphs(tier)` probed against the console
encoder) is M1; the content side is M5.

## 7. The layering test (CR-43, NFR-10)

One rule, applied to every source file by its package:

```
under dev.consolekit.core    → may import: dev.consolekit.core..
under dev.consolekit.text    → + own prefix
under dev.consolekit.bitmap  → + own prefix
under dev.consolekit.canvas  → + own prefix, dev.consolekit.bitmap
                               (never dev.consolekit.bitmap.internal)
under dev.consolekit.tools   → anything; and nothing imports tools
```

It ships with M1, when there is one prefix to check, and simply keeps
passing as the others appear.

## 8. Test support

Golden files under `src/test/resources/golden/` are shared by every part
and regenerate only under `-Dconsolekit.golden.update=true` (NFR-12).
Any part may assert through `Rendering.toString` or `CellBuffer.dump()`;
only canvas needs the terminal emulator, which lives in canvas test
sources.
