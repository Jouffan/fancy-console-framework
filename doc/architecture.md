# ConsoleKit — Architecture map (v4, four parts)

The structure that implements [the requirements map](requirements.md).
One document per part; this one holds only the seams.

| Part | Document | Owns |
|---|---|---|
| Core | [core/architecture.md](core/architecture.md) | values, encoding, width, capabilities, the escape emitter, `Renderable → String` |
| Text | [text/architecture.md](text/architecture.md) | `Text`, `Styler`, `Snippets` |
| Bitmap | [bitmap/architecture.md](bitmap/architecture.md) | `AsciiBitmap`, `Palette`, `AsciiAnimation`, the `.art` codec |
| Canvas | [canvas/architecture.md](canvas/architecture.md) | `FancyConsole`, `AsciiCanvas`, widgets, events, keys, the render thread |

---

## 1. Four artefacts, four modules

Each part is its own Maven artefact and its own JPMS module. The reactor
**is** the CR-43 enforcement: an illegal edge does not compile, so no
scan has to catch it.

| Artefact | Module | Depends on | Third-party |
|---|---|---|---|
| `consolekit-core` | `dev.consolekit.core` | — | none |
| `consolekit-text` | `dev.consolekit.text` | core | none |
| `consolekit-bitmap` | `dev.consolekit.bitmap` | core | none |
| `consolekit-canvas` | `dev.consolekit.canvas` | core, bitmap | JLine (NFR-8) |

**JLine is a canvas dependency, not a library-wide one.** Core, text and
bitmap have no third-party dependency at all — that is the payoff for
splitting, and it is why `TerminalPort` lives in canvas.

Each module exports its public packages and keeps one non-exported
`*.internal` package. There is no shared `dev.consolekit.internal` and no
shared root package: a class belongs to exactly one artefact, visibly,
from its package name.

```
consolekit-core
  dev.consolekit.core            Color, Style, Attr, Cell, Capabilities,
                                 GlyphTier, StyledText, Theme,
                                 RenderContext, ConsoleOptions,
                                 ConsoleRuntime, Probe
  dev.consolekit.core.render     Renderable
  dev.consolekit.core.widget     Table, Box, KeyValueBlock, Tree, Sparkline,
                                 Rule, PatternHighlighter, Border
  dev.consolekit.core.internal   NOT exported. Ansi, Glyphs, TextWidth,
                                 Encoding

consolekit-text
  dev.consolekit.text            Text, Styler, Snippets

consolekit-bitmap
  dev.consolekit.bitmap          AsciiBitmap, Palette, AsciiAnimation,
                                 Anchor, Art, ArtProbe
  dev.consolekit.bitmap.internal NOT exported. ArtFormat, ArtText

consolekit-canvas
  dev.consolekit.canvas          FancyConsole, Widget, WidgetHandle,
                                 Placement, Dock, Size, Rect, Surface, Focus
  dev.consolekit.canvas.widget   Label, StatusBar, Clock, ProgressBar,
                                 Spinner, MultiProgress, StatusList, Graph,
                                 LogPane, Panel, AsciiSprite
  dev.consolekit.canvas.event    WidgetEvent (sealed) and its records,
                                 EventRecorder / EventReplayer
  dev.consolekit.canvas.input    KeyEvent, Key, Modifiers
  dev.consolekit.canvas.internal NOT exported. TerminalPort, LiveRegion,
                                 ScrollbackWriter, StreamCapture,
                                 AnimationLoop, EventQueue, KeyListener,
                                 CanvasBuffer, Layout, Blitter,
                                 VirtualTerminal (test support)
```

## 2. The allowed edges (CR-43)

```
core  ←  text
core  ←  bitmap
core  ←  canvas
bitmap ← canvas        (AsciiSprite wraps AsciiAnimation — the only one)
```

Enforced by the reactor and by `module-info.java` (NFR-10). A `canvas`
import inside `bitmap` is a compile error, not a test failure. The scan
tests that remain are the ones the compiler cannot express: the CR-21 /
CR-22 / CR-23 single-class monopolies and CV-22.

`Ansi` is the only escape emitter (CR-21), `TerminalPort` the only JLine
toucher
(CR-22), `Glyphs` the only non-ASCII literal holder (CR-23).

## 3. How output crosses the seams

Two doors. `StyledText` cannot carry per-channel absence, so overlay
paint is not a `Renderable` blit.

**Print door** — `Renderable` → `StyledText` → `String` (CR-44):

```
       ┌──────────────┐  Renderable    ┌───────────────┐
       │ widget.Table │───────────────►│ core: render  │──► String  (text mode)
       │ AsciiBitmap  │                │ + Ansi        │──► stream  (println / CV-10)
       └──────────────┘                └───────────────┘
```

`Surface.blit(Renderable)` is this door written into cells (catalogue
`Table` / `Panel`). Absent channels become space + default style.

**Paint door** — `cellsAt(elapsed)` → core `Cell[]` → composite (CV-94):

```
       AsciiAnimation.cellsAt(elapsed)  ──►  Cell[]  ──►  Surface composite
                                                              │  per-channel absence
                                                              ▼
                                                    Cell[] back buffer ──► LiveRegion
```

`Cell` is the shared type (CR-41). Stored bitmaps are slots (AF-3); paint
resolves, then composites. That is not a memcpy and not a new part-to-part
bridge — both sides already depend on core.

## 4. How time crosses the seams

```
AnimationLoop (canvas)  ──Tick(elapsed)──►  AsciiSprite (canvas)
                                                  │ cellsAt(elapsed)   (BM-13)
                                                  ▼
                                          AsciiAnimation (bitmap)  ── pure
                                                  │
                                                  ▼ Cell[]
                                          Surface composite (CV-94)
```

Core has no clock. Bitmap has no clock — it has arithmetic on a
`Duration` handed to it. Canvas has the only clock, in one class
(`AnimationLoop`), and one thread (CV-57). Any future time-based widget
uses the same shape: pure function in a value, elapsed time from the
tick. Overlay paint is the Cell composite door, not `blit(Renderable)`.

## 5. Build order

1. Build skeleton and the NFR-10 structural scan tests (M0). They are
   cheap while there is nothing to scan, and they are what makes every
   later "done" objective.
2. Core model, capabilities, `Text` (M1); then `Color` named constants,
   `Styler` and `Snippets` (M1b); then the static catalogue (M2).
3. Introduce `canvas.Widget`, `Placement`, `Layout`, `CanvasBuffer`,
   `Surface`, `Blitter`, and `AsciiCanvas.dump()` with tests against the
   dump only. Create `core.Cell` in this step, not later (CR-41), so the
   buffer and the bitmap never diverge.
4. `LiveRegion` as a cell-diff behind the "print above region" path;
   `VirtualTerminal`; the scrollback/capture tests against a canvas.
5. `FancyConsole.place` with `update` / `focus` / `remove` only — no
   `send` yet. A docked `Clock` is the first demo that exercises it.
6. `EventQueue`, `WidgetId`, `WidgetEvent`, `handle.send`,
   `EventRecorder/Replayer`, then the event-driven catalogue (M4).
7. Bitmap part (M4b): `AsciiBitmap` + `Palette` + `ArtText` first — it is
   testable with nothing but core — then `AsciiAnimation` including
   `cellsAt` (BM-13), then `ArtFormat` against byte goldens. Then
   `AsciiSprite` (M4c): a clock plus CV-94 composite. Do not start M4c
   until the two-door story is in the docs (it is).
8. Content-side encoding (M5) — before keys, because CR-37 needs it.
   The CR-39 fixture lives in canvas tests.
9. **NFR-19 start-gate (not a milestone):** the conhost rows of NFR-14
   MUST be verified for canvas mode **before M6 starts**. conhost
   scrolling under `println`-above-region is the risk.
10. `KeyListener`, focus slot, single-consumer forwarding (M6). Built-ins
    are `Ctrl-C` and `Ctrl-L` only. Do not start until step 9 passed.
11. Remaining NFR-14 rows of `SUPPORTED-TERMINALS.md` (M7), including
    macOS. Filling those rows does not relax the NFR-19 conhost gate.

## 6. Prior art: what the v2 prototype taught us

There is **no v2 code in this repository** (requirements map §5). The
prototype is gone; what survives is the list of things it got right,
which the greenfield build should reproduce rather than rediscover, and
the things it got wrong, which are already designed out above.

Worth reproducing:

| Idea | Where it now lives |
|---|---|
| One escape emitter, one JLine toucher, one non-ASCII literal holder | CR-21…CR-23, enforced by the M0 scan |
| "Erase region, print the scrollback line, repaint region" | `LiveRegion` (§4) — the trick the whole canvas rests on |
| Stream capture that chains onto a pre-existing replacement and restores the exact prior instances | `ScrollbackWriter` / `StreamCapture` (§5), CV-12, CV-14…CV-21 |
| One render thread, fixed fps, coalesced off-cycle redraws | `AnimationLoop` (§8) |
| A virtual terminal in tests, asserting what the user sees | NFR-3 |

Designed out on purpose:

| v2 shape | Why it is gone |
|---|---|
| Three "tiers" as a user-facing concept | replaced by four parts, one artefact each (CR-43, NFR-9b) |
| `pin` / `AsciiWidget` / `PinHandle` / `PinContext` / `PinStack` | replaced by `place` / `canvas.Widget` / `WidgetHandle` |
| Line-diff repaint of `List<StyledText>` frames | cell-diff over two `Cell` buffers (CV-54) |
| Widgets mutated by direct method calls from any thread | `WidgetEvent` on one queue, drained by the render thread (CV-39) |

`Probe` and `SUPPORTED-TERMINALS.md` are carried forward as concepts, not
as files (CR-15, NFR-17). The demos to write are a `Showcase`, a
`ReportPage`, and a docked `Clock`.
