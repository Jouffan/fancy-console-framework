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

## 1. One module, four package groups

There is still exactly one JPMS module, `dev.consolekit`, with
`module-info.java`. `dev.consolekit.internal` is not exported.

```
dev.consolekit            Text, Styler, Snippets        → text
                          FancyConsole                  → canvas
                          Probe, ConsoleRuntime         → core
dev.consolekit.core       Color, Style, Attr, Cell, Capabilities, GlyphTier,
                          StyledText, Theme, RenderContext, ConsoleOptions
dev.consolekit.render     Renderable
dev.consolekit.widget     Table, Box, KeyValueBlock, Tree, Sparkline,
                          Rule, PatternHighlighter, Border
dev.consolekit.bitmap     AsciiBitmap, Palette, AsciiAnimation, Anchor, Art
dev.consolekit.canvas     Widget, WidgetHandle, Placement, Dock, Size, Rect,
                          Surface, Focus
dev.consolekit.canvas.widget
                          Label, StatusBar, Clock, ProgressBar, Spinner,
                          MultiProgress, StatusList, Graph, LogPane, Panel,
                          AsciiSprite
dev.consolekit.event      WidgetEvent (sealed) and its records,
                          EventRecorder / EventReplayer
dev.consolekit.input      KeyEvent, Key, Modifiers
dev.consolekit.internal   NOT exported. Ansi, Glyphs, TextWidth, Encoding,
                          TerminalPort, LiveRegion, ScrollbackWriter,
                          StreamCapture, AnimationLoop, EventQueue,
                          KeyListener, CanvasBuffer, Layout, Blitter,
                          ArtFormat, ArtText, VirtualTerminal (test support)
```

## 2. The allowed edges (CR-43)

```
core  ←  text
core  ←  bitmap
core  ←  canvas
bitmap ← canvas        (AsciiSprite wraps AsciiAnimation — the only one)
```

Enforced by a package-scan test, not by convention (NFR-10): no
`canvas` import inside `bitmap` or `text`, no anything-else import inside
`core`.

The three single-class monopolies are core's and unchanged: `Ansi` is the
only escape emitter (CR-21), `TerminalPort` the only JLine toucher
(CR-22), `Glyphs` the only non-ASCII literal holder (CR-23).

## 3. How output crosses the seams

```
       ┌──────────────┐  Renderable    ┌───────────────┐
       │ widget.Table │───────────────►│ core: render  │──► String  (text mode)
       │ AsciiBitmap  │                │ + Ansi        │──► stream  (println)
       └──────────────┘                └───────────────┘
                │ Renderable
                ▼
       ┌───────────────────┐
       │ canvas: Surface   │──► Cell[] back buffer ──► LiveRegion ──► TerminalPort
       │ .blit(Renderable) │
       └───────────────────┘
```

There is exactly one producer contract (`Renderable`) and two consumers.
Adding a third consumer must not add a producer contract. `Cell` being a
core type is what keeps the lower arrow a copy rather than a conversion.

## 4. How time crosses the seams

```
AnimationLoop (canvas)  ──Tick(elapsed)──►  AsciiSprite (canvas)
                                                  │ frameAt(elapsed)
                                                  ▼
                                          AsciiAnimation (bitmap)  ── pure
```

Core has no clock. Bitmap has no clock — it has arithmetic on a
`Duration` handed to it. Canvas has the only clock, in one class
(`AnimationLoop`), and one thread (CV-57). Any future time-based widget
uses the same shape: pure function in a value, elapsed time from the
tick.

## 5. Migration order

1. Add `Color` named constants; add `Styler` and `Snippets` (M1b). No
   engine change; ships value immediately.
2. Introduce `canvas.Widget`, `Placement`, `Layout`, `CanvasBuffer`,
   `Surface`, `Blitter`, and `AsciiCanvas.dump()` with tests against the
   dump only. Create `core.Cell` in this step, not later (CR-41), so the
   buffer and the bitmap never diverge.
3. Retarget `LiveRegion` from line-diff to cell-diff behind the existing
   "print above region" path; extend `VirtualTerminal`; port the existing
   scrollback/capture tests to assert the same thing against a canvas.
4. Replace `FancyConsole.pin` with `place` (`update`/`focus`/`remove`
   only — no `send` yet). Then delete `AsciiWidget`, `PinHandle`,
   `PinContext`, `PinStack`; rewrite `PinnedClock` as a docked `Clock`.
   Keep the obsolete types compiling until this step.
5. Add the package-scan test for CR-43 (M3b). Doing it here, while there
   are only three packages to check, is cheap; doing it after the bitmap
   part exists is archaeology.
6. `EventQueue`, `WidgetId`, `WidgetEvent`, `handle.send`,
   `EventRecorder/Replayer`, then the event-driven catalogue (M4).
7. Bitmap part (M4b): `AsciiBitmap` + `Palette` + `ArtText` first — it is
   testable with nothing but core — then `AsciiAnimation`, then
   `ArtFormat` against byte goldens. Then `AsciiSprite` (M4c), which
   should be a dozen lines if the previous step was done right.
8. Content-side encoding (M5) — before keys, because CR-37 needs it.
9. **NFR-19 start-gate (not a milestone):** the conhost rows of NFR-14
   MUST be verified for canvas mode **before M6 starts**. conhost
   scrolling under `println`-above-region is the risk.
10. `KeyListener`, focus slot, single-consumer forwarding (M6). Built-ins
    are `Ctrl-C` and `Ctrl-L` only. Do not start until step 9 passed.
11. Remaining NFR-14 rows of `SUPPORTED-TERMINALS.md` (M7), including
    macOS. Filling those rows does not relax the NFR-19 conhost gate.

## 6. What the v2 code means under v4

| Code | Verdict |
|---|---|
| `core.*` (`Color`, `Style`, `Attr`, `Capabilities`, `GlyphTier`, `StyledText`, `Theme`, `RenderContext`, `ConsoleOptions`) | **keep** — add `Color` constants (CR-40) and `Cell` (CR-41) |
| `internal.Ansi`, `internal.Glyphs`, `internal.TextWidth`, `internal.TerminalPort` | **keep** — the CR-21/22/23 single-class homes |
| `Text` | **keep** — complete for TX-8; `Styler`/`Snippets` are new siblings |
| `render.Renderable`, `widget.*` (static catalogue) | **keep** — now printed or blitted (CR-42) |
| `render.AsciiWidget`, `render.PinHandle`, `render.PinContext`, `internal.PinStack`, `FancyConsole.pin` | **obsolete** — replaced by `canvas.Widget` / `place` |
| `internal.LiveRegion` | **reinterpret** — keeps "bottom N rows repainted above scrollback"; line-diff becomes cell-diff (CV-54) |
| `internal.VirtualTerminal` | **reinterpret** — extend from line frames to a cell grid (NFR-3) |
| `internal.ScrollbackWriter`, stream capture | **keep** — CV-12, CV-14…CV-21 unchanged |
| `internal.AnimationLoop` | **reinterpret** — same thread and fps rules; its tick drains the event and key queues and carries elapsed time (CV-90) |
| `FancyConsole` println family, routing, multi-instance sharing, close semantics | **keep** |
| `ConsoleRuntime.Mode2Session` | **keep** the holder; its contents change |
| `Probe`, `SUPPORTED-TERMINALS.md` | **keep** — `Probe` gains the `.art` dump (AF-7) |
| Demos `PinnedClock`, `Showcase`, `ReportPage` | `Showcase`/`ReportPage` keep; `PinnedClock` rewrite as a docked `Clock` |
