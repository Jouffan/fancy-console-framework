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
 │  │ router       │   │ (envelopes,bounded)│   │ raw mode + decoder    │ │
 │  └──────┬───────┘   └─────────┬──────────┘   └───────────┬───────────┘ │
 │         │                     │  drained on render thread │            │
 │         │   AnimationLoop ──Tick──┐                       │            │
 │         │                     ▼   ▼                       ▼            │
 │         │            ┌──────────────────────────────────────────┐      │
 │         │            │ AsciiCanvas                              │      │
 │         │            │  layout ─► widgets paint ─► back buffer  │      │
 │         │            │  focus, z-order, clipping, blit,         │      │
 │         │            │  composite (→ core CellBuffer)           │      │
 │         │            └──────────────────┬───────────────────────┘      │
 │         │                               │ diff back vs front           │
 │         ▼                               ▼                              │
 │  ┌──────────────────────────────────────────────────────────────┐      │
 │  │ LiveRegion   — the only class that moves the cursor          │      │
 │  │   "print line above region, repaint region" + cell flush     │      │
 │  └──────────────────────────────┬───────────────────────────────┘      │
 │                                 ▼                                      │
 │  ┌──────────────────────────────────────────────────────────────┐      │
 │  │ TerminalPort — the only class that touches JLine (CR-22)     │      │
 │  └──────────────────────────────────────────────────────────────┘      │
 └────────────────────────────────────────────────────────────────────────┘
        ▲
        │  System.out / System.err (captured while canvas is live)
   third-party code
```

## 1. Packages

```
dev.consolekit.canvas            FancyConsole (entry point), Widget,
                                 WidgetHandle, WidgetId, Placement, Dock,
                                 Size, Rect, Surface, TaskHandle
dev.consolekit.canvas.widget     Label, StatusBar, Clock, ProgressBar,
                                 Spinner, MultiProgress, StatusList, Graph,
                                 LogPane, Panel, AsciiSprite
dev.consolekit.canvas.event      WidgetEvent (sealed) and its records,
                                 Coalesce, Envelope,
                                 EventRecorder / EventReplayer
dev.consolekit.canvas.input      KeyEvent, Key, Modifiers
dev.consolekit.canvas.internal   NOT exported. TerminalPort, CanvasSession,
                                 AsciiCanvas, LiveRegion, ScrollbackWriter,
                                 StreamCapture, AnimationLoop, EventQueue,
                                 KeyListener, Layout, Blitter
(test sources)                   VirtualTerminal
```

`Cell`, `CellBuffer`, `StyledText`, `Renderable` and `Color` come from
core (CR-41, CR-42, CR-46). `AsciiAnimation` comes from bitmap (BM-8);
that is the only inbound dependency this part has on another part
(CR-43). JLine is referenced here and nowhere else in the module
(CR-22).

There is no `CanvasBuffer`: front and back are two core `CellBuffer`s.

## 2. `FancyConsole`

A thin per-instance facade over the process-wide `CanvasSession`, which
is opened once through core's opaque slot —
`ConsoleRuntime.session(CanvasSession.class, CanvasSession::open)` — so
the one static holder stays in core without core naming a canvas type
(NFR-12).

- `new FancyConsole()` with no setup; lazy session open.
- Multiple instances share one engine; each tracks and removes only what
  it placed; the terminal is restored once, by whichever of last-close,
  shutdown hook or signal path fires first.
- `print`/`println`/`printErr`/`blankLine`/`print(Renderable)` with
  per-line buffering and routing: straight to the writer when nothing is
  placed, through `ScrollbackWriter` when the canvas is live.
- `place(Widget, Placement)` returning
  `WidgetHandle { WidgetId id(); void send(WidgetEvent); void focus();
  void invalidate(); void remove(); }`. `place` mints a process-unique
  `WidgetId`, never reused, and calls `widget.attached(handle)` on the
  render thread. A widget already attached throws.
- `logTo(LogPane)` additionally delivers each `println` as a
  `LogMessage` (CV-13). `println` already holds the render lock, so the
  delivery is a direct `onEvent` under that lock, not a trip through the
  queue. It MUST NOT suppress the underlying stream emission.
- `onKey(KeyHandler)` registers the application-level handler (CV-47).
- `onCancel(Runnable)` registers the one SIGINT-path hook (CV-49).
- `close()` idempotent.
- `canvasHeight(int)` on `ConsoleOptions` (CV-6). Height is never a
  percentage of the viewport at this option; fill-remaining is a
  *widget* size, resolved after `h` is known.

## 3. The loop iteration

`AnimationLoop` owns the one render thread (CV-57) and the only clocks.
One iteration, under the render lock:

1. **Tick** — read the monotonic and wall clocks once; build
   `Tick(frame, delta, sinceStart, wallClock)`; hand it to every placed
   widget's `onEvent` (CV-95). Not queued, so never coalesced or
   dropped.
2. **Drain** — take queued envelopes and hand each event to its target
   widget's `onEvent` (unknown or removed target: drop silently, CV-44).
   Then deliver each queued `KeyEvent` by the CV-47 rule.
3. **Measure** — read terminal size from `TerminalPort` (never cached,
   CV-27). A change invalidates: front buffer discarded, buffers
   resized.
4. **Gate** (CV-37) — if no widget `isDirty()` and no invalidation is
   pending, the iteration ends here: nothing painted, nothing written.
5. **Layout** — put the size on this frame's `RenderContext` (never
   `Capabilities.size()`, CR-12). Canvas width **is** the terminal width
   (CV-7). Resolve canvas height `h` per CV-6 and clamp. Dock in
   declaration order, then free-placed overlays. Percentages use
   `floor`; leftover cells go to the last percentage-or-fill widget on
   that axis (CV-26). Negotiate any widget below its `minSize`. Layout
   results are cached and recomputed only on a size or widget-set
   change, which is what keeps this step allocation-free.
6. **Paint** — fill the back buffer with blank (CV-53); for each widget
   in z-order, hand it a `Surface` clipped to its content area. Every
   widget paints, dirty or not; `paint` clears dirty.
7. **Flush** — `LiveRegion.flush(front, back)`, then swap.

The stale-partial-line flush of `StreamCapture` also runs once per
iteration.

`Surface` is a clipped view onto the back `CellBuffer`:

- `put`, `putText`, `fill` — direct cell writes, CR-29/30 substitution
  applied *before* the write so measured width is emitted width.
- `blit(Renderable)` (CV-36) — render to `List<StyledText>` at the
  content width, then `blit(List<StyledText>)`. The first allocates, the
  second does not; widgets that blit every frame keep the list.
- `composite(CellBuffer src, x, y)` (CV-94) — one call to
  `back.composite(src, x, y, clip…)`. The absence rule lives in core
  (CR-46); `Blitter` contains no transparency logic.

The canvas is dumpable to a plain `String` grid (NFR-3,
`CellBuffer.dump()`); every layout test uses this, not a terminal.

## 4. `LiveRegion`

The only class that moves the cursor, and the home of the key trick of
this whole design: *erase the region, print the scrollback line, repaint
the region* so log lines flow above a live block without smearing.

Flush diffs two `CellBuffer`s and emits per-row runs (`CUP` + text,
style escapes only on change) through `core.internal.Ansi`, into a
reused byte buffer, one write per frame. Colour downgrade to the
resolved depth happens here, at emission. A wide cell and its
continuation are emitted as one grapheme. The "print above region" path
forces a full region repaint (front buffer invalidated) because the
terminal has scrolled under it.

## 5. `ScrollbackWriter`, `StreamCapture`

`System.out`/`System.err` are wrapped while the canvas is live; partial
lines are buffered with a stale-flush timeout; capture chains onto a
pre-existing replacement; the library's own writes bypass capture;
restore returns the exact prior instances from `finally` and from the
shutdown hook; capture starts/stops on the widget-count 0↔1 transition.
These are CV-12 and CV-14…CV-21 and they are the reason the part exists
— do not "simplify" them.

## 6. `EventQueue` and `WidgetEvent`

```java
public sealed interface WidgetEvent permits
    ProgressUpdate, Indeterminate, Completed, GraphValues, GraphAppend,
    LogMessage, SetText, SetStatus, SetContent,
    TaskProgress, TaskLog, TaskCompleted,
    PlaySprite, PauseSprite, SetFrame, SetSpriteSpeed, SetCycleOffset,
    Tick, Custom {

    Coalesce coalesce();                 // LATEST or NEVER (CV-43)
    default Object coalesceKey() { return getClass(); }
}

public record Envelope(WidgetId target, WidgetEvent event) {}
```

Records are payload only; the target is on the envelope (CV-39).
`SetStatus`, `TaskProgress` and `SetCycleOffset` override `coalesceKey`
to include their segment / task / group, so "latest wins" is per
segment, not per widget. All records except `SetContent` and `Custom`
are `Serializable`-shaped (CV-41).

The queue is a bounded MPSC structure: any thread — platform or virtual —
may `offer`; only the render thread drains. Overflow, in order: coalesce
`LATEST`; drop oldest `LATEST`; otherwise block the producer. A `send`
from the render thread (a widget reacting inside `onEvent`) bypasses the
bound so the loop cannot deadlock on itself. NFR-5 does not apply to
this queue.

The object API is a facade on the widget (CV-96): `ProgressBar.step()`
is `handle.send(new ProgressUpdate(current + 1, total, label))` when
attached, and `onEvent(sameEvent)` when not yet placed. There is one
implementation.

`EventRecorder` taps envelopes *and* ticks with their virtual
timestamps; `EventReplayer` feeds a recorded or hand-built list straight
into widgets, advancing virtual time only — it never sleeps. M3 ships the
queue, `send` and `SetText`; M4 adds the rest of the vocabulary, the
overflow policy, the recorder and the replayer.

## 7. `KeyListener`

`KeyListener` **decodes**. It does **not** route, and it does **not**
call widgets. One daemon thread, started when raw mode is entered
(CV-45) and stopped when it is exited. It reads bytes from
`TerminalPort`, decodes them with the resolved *input* charset (CR-37)
into `KeyEvent(Key key, Modifiers mods, OptionalInt codePoint)`, and
enqueues them for the render thread, so a widget never sees a key and an
event concurrently (CV-51). The decoder table is the only place escape
sequences for keys are known; widgets get `Key.UP`, not `ESC [ A`.

**Routing** (CV-47) runs on the render thread at drain. A key is a
**single-consumer** message; it is never broadcast. Exact order:

1. the focused widget, if any — `onKey(e)`; stop if handled
2. the application key handler, if any — stop if handled
3. built-in bindings — `Ctrl-L` full repaint, and nothing else

**`Ctrl-C` is not in that list because it is never a key** (CV-49).
`TerminalPort` enters raw mode with signal generation left on (`ISIG` on
POSIX; `ENABLE_PROCESSED_INPUT` on Windows). `Ctrl-C` therefore raises
`SIGINT`, the JVM runs shutdown hooks, and the session's hook runs the
`onCancel` runnable (best effort, once), then the same idempotent
restore every other path uses (CR-26). No exception crosses a thread;
the exit status is the JVM's default for the signal. `TerminalPort` has
a test asserting the flag survives `enterRawMode`.

Focus is a slot: at most one widget is focused; placing a key-accepting
widget fills the slot if empty; `handle.focus()` sets it; removing the
focused widget clears it. There is no focus navigator, and no `Tab` /
caret / selection-chrome bindings (CV-48).

Raw-mode lifetime: entered when the canvas is live **and** at least one
of (a placed widget declares `acceptsKeys()`, an application handler is
registered); exited in a `finally` when neither holds or the canvas goes
down. Exiting raw mode is one of the CR-27 restore paths and needs its
own test on every platform row.

## 8. `Widget` contract

```java
public interface Widget {
    Size preferredSize(SizeConstraints c);   // may be content-derived
    Size minSize();
    void paint(Surface s, RenderContext ctx);        // clears dirty
    boolean isDirty();
    default void onEvent(WidgetEvent e) {}           // includes Tick
    default boolean onKey(KeyEvent e) { return false; }
    default boolean acceptsKeys() { return false; }
    default void attached(WidgetHandle h) {}         // CV-96
    default void detached() {}
    default void onEventPlain(WidgetEvent e,         // CV-61, non-TTY
                              Consumer<StyledText> lines) {}
}
```

`Surface` is clipped to the content area (border and padding are drawn by
the framework from the `Placement`, not by the widget). Widgets never see
the canvas, the terminal, the cursor, or other widgets. Shipped widgets
extend a small package-private base that stores the handle and implements
"send if attached, else apply directly".

## 9. `AsciiSprite`

The canvas half of the bitmap part, and deliberately thin. State (CV-90):
the `AsciiAnimation`, an accumulator, speed, playing, loop, optional
pinned frame, optional pinned offsets, a scratch `CellBuffer` of the
animation's bounds, an `int[] offsets`, and the last painted
`(frame, offsets)`.

```
onEvent(Tick t):      if playing: elapsed += t.delta × speed
                      compute (frame, offsets) per CV-90
                      dirty = differs from last painted
onEvent(PlaySprite):  clear pins; playing = true; loop = e.loop
onEvent(SetFrame i):  pin frame                     … and so on (CV-91)

paint(surface, ctx):  if (frame, offsets) changed since scratch was filled:
                          animation.cells(frame, offsets, ctx, scratch)   // BM-15
                      surface.composite(scratch, ax, ay)                  // CV-94
```

`(ax, ay)` places the animation's bounds in the content area per its
anchor. The sprite reads no clock, allocates nothing per frame, and
contains no transparency logic. `EventReplayer` with synthetic tick
deltas plus the NFR-3 dump is the whole test strategy (CV-93). No test
sleeps.

## 10. `Panel`, `Clock`, `MultiProgress` — the three with a wrinkle

- **`Panel`** holds a `Renderable` set by `SetContent` and a cached
  `List<StyledText>` keyed by content identity and width; `paint` is
  `surface.blit(cachedLines)`. `Renderable`s are pure (CR-16), so content
  that changes means a new `SetContent`.
- **`Clock`** formats `tick.wallClock()`; it never calls
  `Instant.now()`. It is dirty when the formatted string changes.
- **`MultiProgress`** keeps tasks as internal rows keyed by an `int`
  task id; `TaskHandle` is a facade sending task events to the one
  widget (CV-66). Tasks are not widgets, so CV-32 stays deferred.

## 11. Concurrency (NFR-7)

Exactly one render thread. Producers: application threads (`println`,
`send`), third-party code (captured streams), the key listener thread.
Consumer: the render thread, which is the only thread that calls
`onEvent`, `onKey`, `paint`, `attached`, `detached`, or touches
`LiveRegion` and the two `CellBuffer`s. `println` is the one producer
that also *writes*, and it does so by taking the render lock for exactly
the duration of "erase, print, repaint" — the same lock the iteration
holds. No widget field needs to be volatile because no widget method
runs on two threads. (A facade call on a not-yet-placed widget runs on
the caller's thread, but nothing else can reach the widget then.)

## 12. Degradation when not a TTY

Detected once by `Capabilities`. Under it: `LiveRegion` never moves the
cursor, `KeyListener` never starts, `StreamCapture` still runs (so
captured lines are ordered with `println`), and each event goes to the
widget's `onEventPlain` instead of `onEvent` + paint — a sprite's policy
is its first frame, once, through the print door (CV-92). Piping any
demo to a file yields zero `0x1B` bytes; that test exists and stays.

## 13. Testing surface

- `VirtualTerminal` (test sources): consumes the library's own bytes,
  interprets `CUP`, `EL`, `ED`, SGR, scrolling, and maintains a cell
  grid. Tests assert *what the user sees* — including that a `println`
  during a live canvas lands above it and the canvas is intact
  afterwards.
- `CellBuffer.dump()` on the back buffer for layout tests with no
  terminal.
- `EventReplayer` for widget and sprite tests with no threads and no
  sleeping.
- Source-scan tests: no `\u001b` outside `core.internal.Ansi`, no
  `org.jline` outside `canvas.internal.TerminalPort`, no non-ASCII
  literal outside `core.internal.Glyphs`, the CR-43 prefix rule, and no
  public method under `dev.consolekit.canvas` returning a `Writer`,
  `OutputStream` or terminal handle (CV-22).
