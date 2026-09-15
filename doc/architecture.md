# ConsoleKit — Architecture (v3, two-mode)

This document describes the target structure for the two-mode design in
`doc/requirements.md` (v3), and — honestly — how far the code on this
branch is from it. Sections are marked **[exists]**, **[reinterpret]** or
**[new]** so nobody mistakes a description for a promise.

The code on this branch was written against the three-tier v2 design.
Treat it as a starting point, not as ground truth: the stream capture,
terminal port, capability model and static widgets are sound; the
pin/strip engine is the part being replaced. See requirements §11.2.

## 1. The picture

```
 program runtime
   │
   │  println("...")             WidgetEvent                KeyEvent
   │  print(Renderable)          via handle.send()          (from terminal)
   ▼                                   │                        │
 ┌─────────────────────────────────────┼────────────────────────┼─────────┐
 │ FancyConsole  (per-instance facade) │                        │         │
 │                                     ▼                        ▼         │
 │  ┌──────────────┐   ┌────────────────────┐   ┌───────────────────────┐ │
 │  │ Scrollback   │   │ EventQueue         │   │ KeyListener           │ │
 │  │ router       │   │ (bounded, coalesce)│   │ raw mode + decoder    │ │
 │  └──────┬───────┘   └─────────┬──────────┘   └───────────┬───────────┘ │
 │         │                     │  drained on render thread │            │
 │         │                     ▼                           ▼            │
 │         │            ┌──────────────────────────────────────────┐      │
 │         │            │ AsciiCanvas                              │      │
 │         │            │  layout ─► widgets paint ─► back buffer  │      │
 │         │            │  focus, z-order, clipping, blit          │      │
 │         │            └──────────────────┬───────────────────────┘      │
 │         │                               │ diff back vs front           │
 │         ▼                               ▼                              │
 │  ┌──────────────────────────────────────────────────────────────┐      │
 │  │ LiveRegion   — the only class that moves the cursor          │      │
 │  │   "print line above region, repaint region" + cell flush     │      │
 │  └──────────────────────────────┬───────────────────────────────┘      │
 │                                 ▼                                      │
 │  ┌──────────────────────────────────────────────────────────────┐      │
 │  │ TerminalPort — the only class that touches JLine             │      │
 │  └──────────────────────────────────────────────────────────────┘      │
 └────────────────────────────────────────────────────────────────────────┘
        ▲
        │  System.out / System.err (captured while canvas is live)
   third-party code
```

Three things enter `FancyConsole`: println commands, widget events, and
keys. One thing leaves: a byte stream through `TerminalPort`. Nothing
else in the process is allowed to write to the terminal while the canvas
is live; that is what "protected output" means.

## 2. Module layout

One Java module, `dev.consolekit`. Package split:

```
dev.consolekit            Text, Styler, Snippets      (mode 1)
                          FancyConsole, Probe          (mode 2 entry, diagnostics)
                          ConsoleRuntime               (the one static holder, NFR-12)
dev.consolekit.core       Color, Style, Attr, Capabilities, GlyphTier,
                          StyledText, Theme, RenderContext, ConsoleOptions
dev.consolekit.render     Renderable                   (the static contract)
dev.consolekit.widget     Table, Box, KeyValueBlock, Tree, Sparkline,
                          Rule, PatternHighlighter, Border   (static catalogue)
dev.consolekit.canvas     Widget, WidgetHandle, Placement, Dock, Size, Rect,
                          Surface, Cell, Focus               [new]
dev.consolekit.canvas.widget
                          Label, StatusBar, Clock, ProgressBar, Spinner,
                          MultiProgress, StatusList, Graph, LogPane, Panel,
                          SelectMenu, MultiSelectMenu, Confirm, TextInput  [new]
dev.consolekit.event      WidgetEvent (sealed) and its records,
                          EventRecorder / EventReplayer                    [new]
dev.consolekit.input      KeyEvent, Key, Modifiers                         [new]
dev.consolekit.internal   NOT exported. Ansi, Glyphs, TextWidth, TerminalPort,
                          LiveRegion, ScrollbackWriter, StreamCapture,
                          AnimationLoop, EventQueue, KeyListener, CanvasBuffer,
                          Layout, Blitter, VirtualTerminal (test support)
```

`dev.consolekit.render.AsciiWidget`, `PinHandle`, `PinContext` and
`dev.consolekit.internal.PinStack` are **obsolete** and are to be deleted
once `canvas.Widget` lands — not kept "for compatibility"; there are no
external users.

## 3. Mode 1 — text mode

### `Text` **[exists]**

Static `String`-returning helpers designed for `import static`: sixteen
named colours, `fg`/`bg`, six attributes, theme roles, `styled(Style, s)`,
each with a `format` overload. Stateless; probes capabilities through the
JDK only (never JLine); returns the input unchanged when escapes can't be
rendered; closes each span with its specific SGR-off code (never `SGR 0`);
registers the SGR-reset shutdown hook on first escape.

### `Styler` **[new]**

An immutable value over a `Theme`. Same role-method names as `Text`, as
instance methods:

```java
Styler s = Styler.of(Theme.DEFAULT)
                 .with(Theme.Role.HIGHLIGHT, Style.fg(Color.BRIGHT_CYAN).bold());
IO.println("found " + s.highlight("42") + " matches");
```

Implementation is a one-line delegation to `Text.styledWith(caps, style,
text)` — the same package-private seam `Text` already exposes for tests.
`Styler` exists so that "the colour set in the style instance" is a value
the application can pass around, inject, or swap per environment, without
`Text` growing mutable state.

### `Snippets` **[new]**

Glyph-tier-aware one-liners, all colourable, all returning `String`:
`line(Color)`, `line(Color, width)`, `rule(Color, title)`, `ok`/`fail`/
`warn`, `bullet`, `banner`, `bar(Color, fraction, width)`. Plus a
`print…` twin for each that writes to `System.out` — the only printing
methods in mode 1, and deliberately trivial. Width, when not given, comes
from `Capabilities.size()` (JDK/env-derived, no terminal opened), falling
back to 80 when not a TTY. Glyphs come from `internal.Glyphs`, so a rule
is `─` on FULL and `-` on ASCII without the caller knowing.

### `Color` **[exists, extend]**

`dev.consolekit.core.Color` is the single colour type. Add the sixteen
named constants as static fields (`Color.RED`, `Color.BRIGHT_WHITE`, …)
so that `Snippets.line(Color.RED)` doesn't need `Color.named(Named.RED)`.
Do not introduce `AsciiColor`.

## 4. Mode 2 — canvas mode

### 4.1 `FancyConsole` **[exists, retarget]**

A thin per-instance facade over the process-wide session in
`ConsoleRuntime.Mode2Session`. Already correct and to keep:

- `new FancyConsole()` with no setup; lazy holder-class session open.
- Multiple instances share one engine; each tracks and removes only what
  it placed; the terminal is restored once by shutdown hook or `Ctrl-C`,
  whichever fires first.
- `print`/`println`/`printErr`/`blankLine`/`print(Renderable)` with
  per-line buffering (`pendingOut`) and routing: straight to the writer
  when nothing is placed, through `ScrollbackWriter` when the canvas is
  live.
- `close()` idempotent.

To change:

- `pin(AsciiWidget)` → `place(Widget, Placement)` returning
  `WidgetHandle { update(); send(WidgetEvent); focus(); remove(); }`.
- `logTo(LogPane)` to redirect println into a widget when the canvas
  fills the screen (CV-13).
- `onKey(KeyHandler)` to register the application-level handler (CV-47).
- `canvasHeight(int)` / `canvasHeight(Fill)` on `ConsoleOptions`.

### 4.2 `AsciiCanvas` **[new]**

Owns the widget list, the layout, the focus, and the two `CanvasBuffer`s.
Per frame, on the render thread:

1. **Drain** the `EventQueue` — deliver each `WidgetEvent` to its target
   widget's `onEvent`. Deliver each queued `KeyEvent` by the CV-47 rule.
2. **Measure** — read terminal size from `TerminalPort` (never cached),
   clamp canvas height, run `Layout` (dock in declaration order, then
   free-placed overlays) to produce a `Rect` per widget. Negotiate any
   widget below its `minSize` (shrink or hide).
3. **Paint** — clear the back buffer; for each widget in z-order, hand it
   a `Surface` clipped to its content area. `Surface.blit(Renderable)`
   is how the static catalogue gets onto the canvas: render to
   `List<StyledText>` at the content width, then write each line's spans
   into cells, applying CR-29/30 substitution *before* cell writes so
   the measured width is the emitted width.
4. **Flush** — `LiveRegion.flush(front, back)`.

The canvas is dumpable to a plain `String` grid (NFR-3); every layout
test uses this, not a terminal.

### 4.3 `LiveRegion` **[reinterpret]**

Already the only class that moves the cursor, and already implements the
key trick of this whole design: *erase the region, print the scrollback
line, repaint the region* so log lines flow above a live block without
smearing. That stays exactly as it is.

What changes is the repaint: today it diffs a `List<StyledText>` frame
line-by-line; it must instead diff two cell buffers and emit per-row runs
(`CUP` + text, style escapes only on change), with a reused byte buffer
and one write per frame. The "print above region" path forces a full
region repaint (front buffer invalidated) because the terminal has
scrolled under it.

### 4.4 `ScrollbackWriter`, `StreamCapture` **[exists]**

Unchanged. `System.out`/`System.err` are wrapped while the canvas is
live; partial lines are buffered with a stale-flush timeout; capture
chains onto a pre-existing replacement; the library's own writes bypass
capture; restore returns the exact prior instances from `finally` and
from the shutdown hook; capture starts/stops on the widget-count 0↔1
transition. These are requirements CV-12 and CV-14…CV-21 and they are
the reason the mode exists — do not "simplify" them while retargeting.

### 4.5 `EventQueue` and `WidgetEvent` **[new]**

```java
public sealed interface WidgetEvent permits
    ProgressUpdate, Indeterminate, Completed, GraphValues, GraphAppend,
    LogMessage, SetText, SetStatus, Tick, Custom { WidgetId target(); }
```

All records, all immutable, all `Serializable`-shaped (CV-41: a child
process must be able to stream them over a pipe later). The queue is a
bounded MPSC structure: any thread — platform or virtual — may `offer`;
only the render thread `drain`s, under the render lock. Overflow policy:
coalesce same-type events to the same target (a burst of `ProgressUpdate`
keeps only the newest), then drop oldest. `LogMessage` is never
coalesced.

The object API is a facade: `ProgressBar.step()` is
`handle.send(new ProgressUpdate(id, current+1, total, label))` and
nothing else. There is one implementation.

`EventRecorder` wraps the queue and appends every event to a list;
`EventReplayer` feeds a recorded list back at recorded timestamps. Both
exist from M4 because they make the event-driven widgets testable
without threads.

### 4.6 `KeyListener` **[new]**

Its own daemon thread, started when raw mode is entered (CV-45) and
stopped when it is exited. Reads bytes from `TerminalPort`, decodes them
with the resolved *input* charset (CR-37) into `KeyEvent(Key key,
Modifiers mods, OptionalInt codePoint)`, and offers them to the same
render-thread queue as widget events, so a widget never sees a key and an
event concurrently (CV-51). The decoder table is the only place escape
sequences for keys are known; widgets get `Key.UP`, not `ESC [ A`.

Routing (CV-47), executed by `AsciiCanvas` during drain:

```
focused widget .onKey(e)  → handled? stop
application handler       → handled? stop
built-in: Ctrl-C cancel, Ctrl-L repaint, Tab/Shift-Tab focus cycle
```

Raw-mode lifetime: entered on the first of (a key-accepting widget is
placed) or (an application handler is registered) while the canvas is
live; exited in a `finally` when neither holds or the canvas goes down.
Exiting raw mode is one of the CR-27 restore paths and needs its own
test on every platform row.

### 4.7 `AnimationLoop` **[reinterpret]**

Same thread and timing rules (one platform daemon thread, 12.5 fps
default, 1–30 range). Its tick body becomes steps 1–4 of §4.2 plus the
existing stale-partial-line flush. `redrawNow()` keeps its role of forcing
an off-cycle frame on resize, scrollback write, completion and explicit
request; multiple requests between ticks coalesce to one frame.

### 4.8 `Widget` contract **[new]**

```java
public interface Widget {
    Size preferredSize(SizeConstraints c);   // may be content-derived
    Size minSize();
    void paint(Surface s, RenderContext ctx);
    boolean isDirty();
    default void onEvent(WidgetEvent e) {}
    default boolean onKey(KeyEvent e) { return false; }
    default boolean acceptsKeys() { return false; }
}
```

`Surface` is clipped to the content area (border and padding are drawn by
the framework from the `Placement`, not by the widget). Widgets never see
the canvas, the terminal, the cursor, or other widgets.

## 5. Concurrency model (NFR-7)

- **Text mode:** stateless; nothing to say.
- **Canvas mode:** exactly one render thread. Producers: application
  threads (`println`, `send`), third-party code (captured streams), the
  key listener thread. Consumer: the render thread, which is the only
  thread that calls `onEvent`, `onKey`, `paint`, or touches
  `LiveRegion`. `println` is the one producer that also *writes*, and it
  does so by taking the render lock for exactly the duration of "erase,
  print, repaint" — the same lock the tick holds. No widget field needs
  to be volatile because no widget method runs on two threads.

## 6. Degradation when not a TTY

Detected once by `Capabilities`. Under it: `LiveRegion` never moves the
cursor, `KeyListener` never starts, `StreamCapture` still runs (so
captured lines are ordered with `println`), and every widget is asked for
its plain-line policy on each `onEvent` instead of being painted. Piping
any demo to a file yields zero `0x1B` bytes; that test exists and stays.

## 7. Encoding robustness in this architecture

The glyph side (the library's own box characters, marks, ramps) is done:
`Glyphs.candidateGlyphs(tier)` is tested against the console encoder and
the tier is chosen accordingly. The content side is not, and the canvas
makes it more urgent, because a mis-measured string now shifts every cell
to its right rather than one column rule.

The rule is: **substitute, then measure, then place.** `Surface.putText`
and `Blitter` call `Encoding.forOutput(caps).substitute(text)` (NFC
normalise → check encodable → transliterate-or-replace, width-preserving)
before `TextWidth.of` and before any cell write. `LiveRegion.flush` may
assert (test builds) that every cell it emits is encodable; it never
substitutes itself, because by then layout has already been computed.

`KeyListener` decodes with the *input* charset, resolved separately; on
Windows that is the console input code page, not the output one.

## 8. Testing surface

- `internal.VirtualTerminal` (exists for the strip engine; extend for
  cell flush): consumes the library's own bytes, interprets `CUP`, `EL`,
  `ED`, SGR, scrolling, and maintains a cell grid. Tests assert *what the
  user sees* — including that a `println` during a live canvas lands
  above it and the canvas is intact afterwards.
- `AsciiCanvas.dump()` for layout tests with no terminal.
- `EventReplayer` for widget tests with no threads.
- Golden files for truecolor/FULL, ANSI256, ANSI16/CP437, NONE/redirected.
- Source-scan tests for CR-21/22/23 and CV-22 (no `\u001b` outside `Ansi`,
  no `org.jline` outside `TerminalPort`, no non-ASCII literal outside
  `Glyphs`, no public method on `FancyConsole` returning a `Writer`).

## 9. Migration order

1. Add `Color` named constants; add `Styler` and `Snippets` (M1b). No
   engine change; ships value immediately.
2. Introduce `canvas.Widget`, `Placement`, `Layout`, `CanvasBuffer`,
   `Surface`, `Blitter`, and `AsciiCanvas.dump()` with tests against the
   dump only.
3. Retarget `LiveRegion` from line-diff to cell-diff behind the existing
   "print above region" path; extend `VirtualTerminal`; port the existing
   scrollback/capture tests to assert the same thing against a canvas.
4. Replace `FancyConsole.pin` with `place`; delete `AsciiWidget`,
   `PinHandle`, `PinContext`, `PinStack`; rewrite `PinnedClock` as a
   docked `Clock`.
5. `EventQueue`, `WidgetEvent`, `EventRecorder/Replayer`, then the
   event-driven catalogue (M4).
6. Content-side encoding (M5) — before keys, because CR-37 needs it.
7. `KeyListener`, focus, interactive widgets (M6).
8. Fill the conhost and macOS rows of `SUPPORTED-TERMINALS.md` before
   step 7 is called done (NFR-19).
