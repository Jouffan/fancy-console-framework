# ConsoleKit — Architecture map (v5, four parts)

The structure that implements [the requirements map](requirements.md).
One document per part; this one holds only the seams.

| Part | Document | Owns |
|---|---|---|
| Core | [core/architecture.md](core/architecture.md) | values, `Cell` + `CellBuffer` + the compositor, encoding, width, capabilities, the escape emitter, `Rendering` |
| Text | [text/architecture.md](text/architecture.md) | `Text`, `Styler`, `Snippets` |
| Bitmap | [bitmap/architecture.md](bitmap/architecture.md) | `Palette`, `AsciiBitmap`, `AsciiAnimation`, the `.art` codec |
| Canvas | [canvas/architecture.md](canvas/architecture.md) | `FancyConsole`, the canvas, widgets, events, keys, the render thread, JLine |

---

## 1. One module, a package prefix per part

There is exactly one JPMS module, `dev.consolekit`, with
`module-info.java`. No `*.internal` package is exported.
`module-info.java` exports a package only once that package contains a
class; javac rejects empty packages. Add the export in the same commit
as the first class.

```
dev.consolekit.core              Color, Style, Attr, Cell, CellBuffer,
                                 Capabilities, GlyphTier, StyledText, Theme,
                                 RenderContext, ConsoleOptions, Renderable,
                                 Rendering, ConsoleRuntime
dev.consolekit.core.widget       Table, Box, KeyValueBlock, Tree, Sparkline,
                                 Rule, PatternHighlighter, Border
dev.consolekit.core.internal     Ansi, Glyphs, TextWidth, Encoding,
                                 GraphemeTable

dev.consolekit.text              Text, Styler, Snippets

dev.consolekit.bitmap            AsciiBitmap, Palette, AsciiAnimation,
                                 Anchor, Art
dev.consolekit.bitmap.internal   ArtFormat, ArtText

dev.consolekit.canvas            FancyConsole, Widget, WidgetHandle,
                                 WidgetId, Placement, Dock, Size, Rect,
                                 Surface, TaskHandle
dev.consolekit.canvas.widget     Label, StatusBar, Clock, ProgressBar,
                                 Spinner, MultiProgress, StatusList, Graph,
                                 LogPane, Panel, AsciiSprite
dev.consolekit.canvas.event      WidgetEvent + records, Coalesce, Envelope,
                                 EventRecorder, EventReplayer
dev.consolekit.canvas.input      KeyEvent, Key, Modifiers
dev.consolekit.canvas.internal   TerminalPort, CanvasSession, AsciiCanvas,
                                 LiveRegion, ScrollbackWriter, StreamCapture,
                                 AnimationLoop, EventQueue, KeyListener,
                                 Layout, Blitter

dev.consolekit.tools             Probe, demos        (application-level)
```

v4 had a mixed root package and one shared `internal`; both made the
layering unenforceable by scan and hid two real violations (`Probe` →
bitmap, `ConsoleRuntime` → canvas). v5 removes the mixing: `Probe` is an
application, and `ConsoleRuntime` offers canvas an opaque session slot.

## 2. The allowed edges (CR-43)

```
core  ←  text
core  ←  bitmap
core  ←  canvas
bitmap ← canvas        (AsciiSprite wraps AsciiAnimation — the only one)
tools →  everything    (and nothing → tools)
```

One package-scan rule over prefixes (NFR-10), shipped with M1:

- a class under `dev.consolekit.X` may **reference** (import or
  fully-qualified name) only `dev.consolekit.X..`,
  `dev.consolekit.core..`, and — canvas only — `dev.consolekit.bitmap`
  (not `bitmap.internal`);
- `org.jline` appears only in `canvas.internal.TerminalPort` (CR-22).

The scan inspects type references in the compilation unit, not merely
`import` lines, so a fully-qualified name cannot bypass it. Comments
and string literals are not type references. What the CR-21 / CR-23
literal scans count, and that all scans cover `src/main` only, is in
NFR-10.

The single-class monopolies: `core.internal.Ansi` is the only escape
emitter (CR-21), `canvas.internal.TerminalPort` the only JLine toucher
(CR-22), `core.internal.Glyphs` the only non-ASCII literal holder
(CR-23), `core.CellBuffer` the only compositor (CR-46).

## 3. How output crosses the seams

Two doors. `StyledText` cannot carry per-channel absence, so overlay
paint is not a `Renderable` blit.

**Print door** — `Renderable` → `StyledText` → `String` (CR-44):

```
       ┌──────────────┐  Renderable    ┌─────────────────┐
       │ widget.Table │───────────────►│ core: Rendering │──► String  (toString)
       │ AsciiBitmap  │                │ + Ansi          │──► stream  (println / CV-10)
       └──────────────┘                └─────────────────┘
```

`Surface.blit(Renderable)` is this door written into cells (catalogue
`Table` / `Panel`). Unset channels become `DEFAULT`, absent glyphs a
space.

**Paint door** — resolve into a `CellBuffer`, then composite (CR-46):

```
  AsciiAnimation.cells(frame, offsets, ctx, scratch)     bitmap: resolve only
                          │  CellBuffer, absence intact
                          ▼
  Surface.composite(scratch, x, y)                       canvas: clip only
                          │
                          ▼
  CellBuffer.composite(src, x, y, clip…)                 core: the absence rule
                          │
                          ▼
                 back CellBuffer ──► LiveRegion diff ──► Ansi ──► TerminalPort
```

Three parts, three jobs, one type. Bitmap never composites, canvas never
decides what absence means, and core never learns what a sprite is.

## 4. How time crosses the seams

```
AnimationLoop (canvas) ── Tick(frame, delta, sinceStart, wallClock) ──► every widget
   the only clocks                                                        │
                                              AsciiSprite: elapsed += delta × speed
                                                                          │
                          frameIndexAt / cycleOffsetAt (bitmap) ── pure arithmetic
                                                                          │
                                                   cells(frame, offsets, ctx, scratch)
```

Core has no clock. Bitmap has no clock — it has arithmetic on a
`Duration` handed to it. Canvas has the only clocks, in one class
(`AnimationLoop`), on one thread (CV-57). `Tick` is synthesised by the
loop and never queued (CV-95), so it cannot be coalesced or dropped and
no widget loses time. Any future time-based widget uses the same shape:
pure function in a value, time from the tick.

## 5. Build order

Each step ends with `mvn verify` green. Steps that change what a user
sees also end with a human running the named demo (NFR-18).

1. **M1 — core.** `Color` (+ constants, `DEFAULT`), `Style`, `Cell`,
   `CellBuffer` + compositor, `StyledText`, `Renderable`, `Rendering`,
   ambient width, `Capabilities` including charset (CR-28),
   `ConsoleRuntime` with the session slot, `Ansi`, `Glyphs`,
   `TextWidth`, the glyph side of `Encoding` (CR-7 against the console
   charset). **The CR-43 scan test (type references, not merely
   imports), the three monopoly scans and the `toString` delegation
   test ship here**, while there is one prefix to check. Tests first
   (NFR-2). Catalogue (CR-18…CR-20) is M2; Probe (CR-15, CR-38) is M2;
   restore paths (CR-26, CR-27) start at the SGR hook in M1b.
   NFR-18 demo: `dev.consolekit.tools.ColourCard` — a colour and glyph
   card printed through `Rendering.toString`. Look for: sixteen named
   colours, `Color.DEFAULT` matching the terminal default, glyph tier
   (box drawing on FULL, ASCII fallback otherwise), the CR-10 fixture,
   no line overflowing the width.
2. **M1b — text.** `Text`, `Styler`, `Snippets` (including TX-14
   `print…` twins). The SGR-reset shutdown hook (TX-7) lives on
   `ConsoleRuntime` (NFR-12) and is the first CR-26/CR-27 restore path.
3. **M2 — static catalogue** under `core.widget` (CR-18…CR-20).
   `Probe` (CR-15, CR-38) ships here.
4. **M2b — bitmap**, in the order of bitmap/architecture §8. Needs core
   only; may proceed in parallel with step 5.
5. **M3 — canvas engine.** `TerminalPort`, `CanvasSession`, `Layout`,
   `Surface` / `Blitter`, `LiveRegion` cell diff, `ScrollbackWriter`,
   `StreamCapture`, `AnimationLoop` with `Tick` and the dirty gate,
   restore paths including SIGINT + `onCancel`, `VirtualTerminal`,
   `place` / `focus` / `invalidate` / `remove`, and the minimal event
   path (`EventQueue`, `send`, `SetText`, `Label`).
6. **M4 — events and widgets.** Full vocabulary, `Coalesce`, overflow
   policy, recorder / replayer, the widget catalogue, `TaskHandle`.
7. **M4c — `AsciiSprite`.** Needs steps 4 and 6.
8. **M5 — content-side encoding** — before keys, because CR-37 needs it.
   The CR-39 fixture lives in canvas tests; bitmap glyph resolution
   picks up substitution through the seam it already has.
9. **NFR-19 start-gate (not a milestone):** the conhost rows of NFR-14
   MUST be verified for canvas mode **before M6 starts**. conhost
   scrolling under `println`-above-region is the risk.
10. **M6 — keys.** `KeyListener`, focus slot, single-consumer
    forwarding. Built-in key binding is `Ctrl-L` only; `Ctrl-C` was
    already handled as a signal in step 5. Do not start until step 9
    passed.
11. **M7 — remaining NFR-14 rows** of `SUPPORTED-TERMINALS.md`,
    including macOS. Filling those rows does not relax the NFR-19 gate.

## 6. If the v2 tree is present

The v2 branch is a reference, not a constraint. If its sources are in
the working tree, this is what each piece means under v5. If they are
not, ignore this section and build from §5.

| v2 code | Verdict |
|---|---|
| `core.*` (`Color`, `Style`, `Attr`, `Capabilities`, `GlyphTier`, `StyledText`, `Theme`, `RenderContext`, `ConsoleOptions`) | **keep** — add `Color` constants and `DEFAULT` (CR-40), unset channels on `Style`, `Cell`, `CellBuffer` |
| `render.Renderable`, `widget.*` | **move** to `core` / `core.widget`; add the `toString` delegation |
| `internal.Ansi`, `Glyphs`, `TextWidth`, `Encoding` | **move** to `core.internal` |
| `internal.TerminalPort` | **move** to `canvas.internal`; keep signals on in raw mode |
| `Text` | **move** to `dev.consolekit.text`; `Styler` / `Snippets` are new siblings |
| `Probe` | **move** to `dev.consolekit.tools`; gains the `.art` dump (AF-7) |
| `ConsoleRuntime` | **move** to `core`; `Mode2Session` becomes canvas's `CanvasSession` behind the opaque slot |
| `render.AsciiWidget`, `PinHandle`, `PinContext`, `internal.PinStack`, `FancyConsole.pin` | **obsolete** — delete when `canvas.Widget` / `place` land; no compatibility shims, there are no external users |
| `internal.LiveRegion` | **reinterpret** — keeps "print above region, repaint region"; line-diff becomes `CellBuffer` diff (CV-54) |
| `internal.VirtualTerminal` | **reinterpret** — move to test sources; extend from line frames to a cell grid (NFR-3) |
| `internal.ScrollbackWriter`, stream capture | **keep** — CV-12, CV-14…CV-21 unchanged |
| `internal.AnimationLoop` | **reinterpret** — same thread and fps rules; iteration becomes canvas/architecture §3 |
| `FancyConsole` println family, routing, multi-instance sharing, close semantics | **keep**, under `dev.consolekit.canvas` |
| `SUPPORTED-TERMINALS.md` | **keep** |
| Demos `PinnedClock`, `Showcase`, `ReportPage` | **move** to `tools`; `PinnedClock` is rewritten as a docked `Clock` |
