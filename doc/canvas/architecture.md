# ConsoleKit Canvas — Architecture

Part 4 of 4. Requirements: [canvas/requirements.md](requirements.md).
See [the map](../architecture.md).

Three things enter `FancyConsole`: println commands, widget events, and
keys. One thing leaves: a byte stream through `TerminalPort`. Nothing
else in the process is allowed to write to the terminal while the canvas
is live; that is what "protected output" means.

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

## 1. Packages

Artefact `consolekit-canvas`, module `dev.consolekit.canvas`. This is the
**only** artefact with a third-party dependency: JLine (NFR-8).

```
dev.consolekit.canvas          FancyConsole                 (entry point)
                               Widget, WidgetHandle, Placement, Dock, Size,
                               Rect, Surface, Focus
dev.consolekit.canvas.widget   Label, StatusBar, Clock, ProgressBar, Spinner,
                               MultiProgress, StatusList, Graph, LogPane,
                               Panel, AsciiSprite
dev.consolekit.canvas.event    WidgetEvent (sealed) and its records,
                               EventRecorder / EventReplayer
dev.consolekit.canvas.input    KeyEvent, Key, Modifiers
dev.consolekit.canvas.internal NOT exported. TerminalPort, LiveRegion,
                               ScrollbackWriter, StreamCapture, AnimationLoop,
                               EventQueue, KeyListener, CanvasBuffer, Layout,
                               Blitter, VirtualTerminal (test support)
```

`TerminalPort` is the CR-22 single JLine toucher and it lives here, which
is what keeps JLine off the classpath of anyone using core, text or
bitmap alone.

`Cell`, `StyledText`, `Renderable` and `Color` come from core (CR-41,
CR-42). `AsciiAnimation` comes from bitmap (BM-8); that is the only
inbound dependency this part has on another part (CR-43).

There is no `pin` API and no `AsciiWidget` / `PinHandle` / `PinContext` /
`PinStack`. Those were the v2 prototype's shape; `place` returning a
`WidgetHandle` replaces all of them. Do not reintroduce them.

## 2. `FancyConsole`

A thin per-instance facade over the process-wide session, which is held
in the opaque `ConsoleRuntime` slot (NFR-12) so that core never names a
canvas type. Required shape:

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

- `place(Widget, Placement)` returning
  `WidgetHandle { WidgetId id(); update(); send(WidgetEvent); focus();
  remove(); }`. `place` mints a process-unique `WidgetId` that is never
  reused after `remove`. `update`/`focus`/`remove` ship in M3; `send`
  ships in M4 and stamps `target()` onto the event.
- `logTo(LogPane)` additionally delivers each `println` as a
  `LogMessage` (CV-13). It MUST NOT suppress the underlying stream
  emission.
- `onKey(KeyHandler)` to register the application-level handler (CV-47).
- `canvasHeight(int)` on `ConsoleOptions` (CV-6). Height is never a
  percentage of the viewport at this option; fill-remaining is a
  *widget* size, resolved after `h` is known.

## 3. `AsciiCanvas`

One tick body, four steps, all on the render thread:

1. **Drain** — take queued `WidgetEvent`s and hand each to its target
   widget's `onEvent`. Then deliver each queued `KeyEvent` by the CV-47
   rule.
2. **Measure** — read terminal size from `TerminalPort` (never cached;
   CV-27) and put it on this frame's `RenderContext`. Do **not** read
   `Capabilities.size()` for layout — that value is probe-time (CR-12).
   Canvas width **is** the terminal width (CV-7). Resolve canvas height
   `h` per CV-6 (config, else sum of definite docked vertical sizes, else
   full viewport) and clamp to the terminal height. Then run `Layout`:
   dock in declaration order, then free-placed overlays. Percentages use
   `floor`; leftover cells go to the last percentage-or-fill widget on
   that axis (CV-26). Negotiate any widget below its `minSize` (shrink or
   hide).
3. **Paint** — clear the back buffer; for each widget in z-order, hand it
   a `Surface` clipped to its content area. Two writes:
   - `Surface.blit(Renderable)` (CV-36) — catalogue / `Panel`: render to
     `List<StyledText>` at the content width, then write each line's
     spans into cells. Absent channels become space + default style.
     Apply CR-29/30 substitution *before* cell writes so the measured
     width is the emitted width.
   - `Surface` composite of core `Cell[]` (CV-94) — overlays /
     `AsciiSprite`: per-channel absence leaves what is underneath.
4. **Flush** — `LiveRegion.flush(front, back)`.

The canvas is dumpable to a plain `String` grid (NFR-3); every layout
test uses this, not a terminal.

## 4. `LiveRegion`

The only class that moves the cursor, and the home of the key trick of
this whole design: *erase the region, print the scrollback line, repaint
the region*, so log lines flow above a live block without smearing.

The repaint diffs two cell buffers and emits per-row runs (`CUP` + text,
style escapes only on change), with a reused byte buffer and one write
per frame. The "print above region" path forces a full region repaint
(front buffer invalidated) because the terminal has scrolled under it.

## 5. `ScrollbackWriter`, `StreamCapture`

`System.out`/`System.err` are wrapped while the canvas is
live; partial lines are buffered with a stale-flush timeout; capture
chains onto a pre-existing replacement; the library's own writes bypass
capture; restore returns the exact prior instances from `finally` and
from the shutdown hook; capture starts/stops on the widget-count 0↔1
transition. These are CV-12 and CV-14…CV-21 and they are the reason the
part exists — do not "simplify" them.

## 6. `EventQueue` and `WidgetEvent`

```java
public sealed interface WidgetEvent permits
    ProgressUpdate, Indeterminate, Completed, GraphValues, GraphAppend,
    LogMessage, SetText, SetStatus, Tick,
    PlaySprite, PauseSprite, SetFrame, SetSpriteSpeed, SetCycleOffset,
    Custom { WidgetId target(); }
```

All records, all immutable, all `Serializable`-shaped (CV-41: a child
process must be able to stream them over a pipe later). `WidgetId` is
minted at `place`, unique for the process, never reused after `remove`;
`target()` is stamped by `handle.send` (or by the engine for `Tick`).

The queue is a bounded MPSC structure: any thread — platform or virtual —
may `offer`; only the render thread `drain`s, under the render lock.
Overflow policy: coalesce same-type events to the same target (a burst of
`ProgressUpdate` keeps only the newest), then drop oldest. `LogMessage`
is never coalesced. NFR-5 does not apply to this queue: records MAY
allocate; the no-alloc rule is layout/paint/diff/flush only.

The object API is a facade: `ProgressBar.step()` is
`handle.send(new ProgressUpdate(id, current+1, total, label))` and
nothing else. There is one implementation.

`EventRecorder` wraps the queue and appends every event to a list;
`EventReplayer` feeds a recorded list back at recorded timestamps. Both
exist from M4 because they make the event-driven widgets testable
without threads.

## 7. `KeyListener`

`KeyListener` **decodes**. It does **not** route, and it does **not**
call widgets. One daemon thread, started when raw mode is entered
(CV-45) and stopped when it is exited. It reads bytes from
`TerminalPort`, decodes them with the resolved *input* charset (CR-37)
into `KeyEvent(Key key, Modifiers mods, OptionalInt codePoint)`, and
enqueues them on the same render-thread queue as widget events, so a
widget never sees a key and an event concurrently (CV-51). The decoder
table is the only place escape sequences for keys are known; widgets
get `Key.UP`, not `ESC [ A`.

**Routing** (CV-47) runs later, on the render thread, when
`AsciiCanvas` drains the queue. A key is a **single-consumer** message;
it is never broadcast. Exact order, no exceptions:

1. the focused widget, if any — `onKey(e)`; stop if handled
2. the application key handler, if any — stop if handled
3. built-in bindings — `Ctrl-C` cancel, `Ctrl-L` full repaint (CV-49)

Built-ins are **only** `Ctrl-C` and `Ctrl-L`. Do not add `Tab` /
`Shift-Tab`, caret drawing, or selection chrome as bindings (CV-48).
Focus is a slot: at most one widget is focused; placing a key-accepting
widget fills the slot if empty; `handle.focus()` sets it; removing the
focused widget clears it. There is no focus navigator.

Raw-mode lifetime: entered when the canvas is live **and** at least one
of (a placed widget declares `acceptsKeys()`, an application handler is
registered); exited in a `finally` when neither holds or the canvas goes
down. Exiting raw mode is one of the CR-27 restore paths and needs its
own test on every platform row.

## 8. `AnimationLoop`

One platform daemon thread, 12.5 fps default, 1–30 range. Its tick body
is steps 1–4 of §3 plus the stale-partial-line flush. `redrawNow()`
forces an off-cycle frame on resize, scrollback write, completion and
explicit request; multiple requests between ticks coalesce to one frame.

## 9. `Widget` contract

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

## 10. `AsciiSprite`

The canvas half of the bitmap part, and deliberately thin: an
`AsciiAnimation` (BM-8), an elapsed-time accumulator, and play/pause
state. Construct with `new AsciiSprite(animation)` after `Art.load`;
there is no `AsciiSprite.load`. `onEvent(Tick)` advances the accumulator
by the tick's elapsed time; `cellsAt(elapsed)` (BM-13) does the
arithmetic; `isDirty` is true only when the selected frame or the cycle
offset actually changed (CV-92). `paint` composites the `Cell[]` through
CV-94 — **not** `Surface.blit(Renderable)`, which would drop per-channel
absence. It reads no clock.

Everything that could be a pure function is one, in the other part. What
is left here is the clock plus the composite. `EventReplayer` with
synthetic tick timings plus the NFR-3 dump is the whole test strategy
(CV-93). No test sleeps.

## 11. Concurrency (NFR-7)

Exactly one render thread. Producers: application threads (`println`,
`send`), third-party code (captured streams), the key listener thread.
Consumer: the render thread, which is the only thread that calls
`onEvent`, `onKey`, `paint`, or touches `LiveRegion`. `println` is the
one producer that also *writes*, and it does so by taking the render lock
for exactly the duration of "erase, print, repaint" — the same lock the
tick holds. No widget field needs to be volatile because no widget method
runs on two threads.

## 12. Degradation when not a TTY

Detected once by `Capabilities`. Under it: `LiveRegion` never moves the
cursor, `KeyListener` never starts, `StreamCapture` still runs (so
captured lines are ordered with `println`), and every widget is asked for
its plain-line policy on each `onEvent` instead of being painted — a
sprite's policy is its first frame, once (CV-92). Piping any demo to a
file yields zero `0x1B` bytes; that test exists and stays.

## 13. Testing surface

- `canvas.internal.VirtualTerminal`: consumes the library's own bytes,
  interprets `CUP`, `EL`, `ED`, SGR, scrolling, and maintains a cell
  grid. Tests assert *what the user sees* — including that a `println`
  during a live canvas lands above it and the canvas is intact
  afterwards.
- `AsciiCanvas.dump()` for layout tests with no terminal.
- `EventReplayer` for widget and sprite tests with no threads and no
  sleeping.
- Source-scan tests for CR-21/22/23 and CV-22, scoped to `src/main`: no
  `\u001b` outside `Ansi`, no `org.jline` outside `TerminalPort`, no
  non-ASCII literal outside `Glyphs`, no public method on
  `FancyConsole` returning a `Writer`. CR-43 needs no scan — a `canvas`
  import inside `bitmap` or `text` does not compile (NFR-9b).
