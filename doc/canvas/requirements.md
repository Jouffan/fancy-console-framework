# ConsoleKit Canvas — Requirements (`CV`)

Part 4 of 4. Package prefix `dev.consolekit.canvas`. Depends on
[core](../core/requirements.md) and, for sprites only, on
[bitmap](../bitmap/requirements.md). See [the map](../requirements.md).

**Own the terminal.** One `FancyConsole` instance owns the terminal for
the process. It holds an `AsciiCanvas` on which widgets are placed at
explicit or docked positions. The application drives widgets by sending
**widget events** and prints ordinary log lines with **direct `println`
commands**; it never touches the terminal itself. Keyboard input is
decoded and **forwarded on a single path until handled** (CV-47). Forms
and smart input are out of scope (CV-67, CV-69).

```
                    ┌──────────────────────────────────────┐
     ┌─────────────►│ FancyConsole                          │
     │              │  ┌────────────┐   ┌───────────────┐  │
     │              │  │KeyListener │   │  AsciiCanvas  │  │
     │              │  └─────┬──────┘   └───────┬───────┘  │
     │              └────────┼──────────────────┼──────────┘
     │ direct                │ offer until        │ rendered
     │ println               │ handled            │ on canvas
     │ commands              ▼                   ▼
     │                  ┌─────────┐        ┌─────────┐
     │                  │ WidgetA │        │ WidgetB │
     │                  └────▲────┘        └────▲────┘
     │                       │ widget events    │ widget events
     │                       └────────┬─────────┘
  ┌──┴──────────────┐                 │
  │ program runtime ├─────────────────┘
  └─────────────────┘
```

`MUST` / `SHOULD` / `MAY` are RFC 2119.

**IDs `CV-70…CV-89` have moved** to the bitmap part (`BM-*`, `AF-*`);
see the map's ID table. They MUST NOT be reused here. The canvas side of
sprites is CV-90…CV-94; CV-95 and CV-96 are new in v5.

---

## 1. Target picture

Canvas **80×12** at the bottom of an 80×24 terminal. Canvas width **is**
the terminal width (CV-7). Clock docked top-right, status bar docked
bottom, progress docked top-left. The three lines above the box are
`println` into scrollback.

```
$ myjob run                                                                     
scanning input/                                                                 
found 1 204 files                                                               
┌───────────────────────────────────────────────────────────────┬──────────────┐
│ ████████████░░░░░░░░ 61%  encode.mp4                          │   14:32:07   │
│                                                               └──────────────┤
│  (main content area, fills remaining)                                        │
│                                                                              │
│                                                                              │
│                                                                              │
│                                                                              │
│                                                                              │
├──────────────────────────────────────────────────────────────────────────────┤
│ status: connected   jobs: 3   mem: 42MB                         [q] quit     │
└──────────────────────────────────────────────────────────────────────────────┘
```

## 2. Terminology

**Canvas** — a `width × height` core `CellBuffer` (CR-46) owned by
`FancyConsole`, occupying the bottom `height` rows of the viewport.
**Buffer** — a `CellBuffer` used for double-buffering (front = on
screen, back = being drawn).
**Rect** — `(x, y, w, h)` in integer character units, canvas-relative.
**Widget** — a placed, stateful thing on the canvas that paints cells,
receives widget events, and may receive key events.
**Widget event** — an immutable payload from the application to one
widget, carried in an **envelope** that names the target, drained by the
render thread (CV-39).
**Tick** — the engine's per-iteration time signal to every widget
(CV-95). Not a queued event.
**Key event** — a decoded keypress delivered by `KeyListener`, offered to
one listener at a time until handled (CV-47).
**Focus** — the at-most-one widget that receives the first offer of a key
event. A slot, not a form navigator (CV-48).
**Scrollback** — the terminal's normal scrolling output, above the canvas.
**Layout pass** — computing each widget's Rect from its placement rules
and the canvas size.
**Paint pass** — widgets writing cells into the back buffer.
**Flush** — emitting the minimal escape stream that makes the terminal
match the back buffer.

---

## 3. Ownership model

- **CV-1** Construction MUST require no arguments and no setup
  (`new FancyConsole()`).
- **CV-2** Multiple `FancyConsole` instances MUST be permitted — library
  code cannot be expected to coordinate — and MUST share one underlying
  terminal, one canvas, one key listener, one event queue and one render
  loop, so they cannot corrupt each other's frames.
- **CV-3** Close MUST be idempotent and MUST remove only the widgets that
  instance placed. The shared terminal MUST be restored exactly once, by
  whichever fires first of the last close, the shutdown hook, or the
  signal path (CR-26).
- **CV-4** There MUST be exactly one canvas per process.
- **CV-5** Everything rendered MUST be either a write into scrollback
  (above the canvas) or a paint of the canvas. There is no third thing.
- **CV-6** The canvas MUST occupy the bottom `h` rows of the viewport in
  the **normal** screen buffer. `h` is resolved in this order, then
  clamped to the terminal height (CV-7):
  1. explicit configuration (`ConsoleOptions.canvasHeight`), if set;
  2. else the layout's **intrinsic height**, if every *docked* widget has
     a definite vertical size (fixed cells or `preferred`). Intrinsic
     height is the sum of those sizes. Free-placed overlays do not
     contribute. A docked widget whose vertical size is percentage or
     fill-remaining makes intrinsic height undefined — fall through;
  3. else the full viewport.
  The last-docked-takes-remaining rule (CV-24) runs **after** `h` is
  known; it MUST NOT feed back into this resolution. The alternate
  screen buffer is NOT used.
- **CV-7** Canvas width MUST be the terminal width, re-read per loop
  iteration (CV-27). Canvas height MUST be clamped to the terminal
  height.
- **CV-8** A minimum canvas size MUST be enforced; below it the library
  MUST render a single "terminal too small" line instead of a layout.
- **CV-9** The render engine MUST be started lazily on the first widget
  placed and stopped when the last widget is removed; while no widget is
  placed, `println` MUST go straight to the terminal with no capture and
  no cursor movement, so an application that only ever calls `println`
  pays nothing.

## 4. Direct println commands

- **CV-10** `FancyConsole` MUST offer `print`, `println`, `printErr`,
  `blankLine`, and `print(Renderable)`; these write into scrollback above
  the canvas. Because a bitmap is a `Renderable` (BM-4), `print` accepts
  one with no extra API.
- **CV-11** An immediate multi-line print MUST be atomic — no other output
  and no canvas repaint may interleave inside it.
- **CV-12** While the canvas is live, scrollback writes MUST be routed so
  that the new line appears above the canvas and the canvas is repainted
  intact. The two MUST NOT smear.
- **CV-13** Scrollback lines MUST always be emitted to the underlying
  stream, including when the canvas fills the whole viewport (they
  scroll off immediately, but a redirected or captured stdout still
  sees them). An application that wants them *visible* MUST attach a
  `LogPane` as the log sink (`console.logTo(logPane)`). Attaching a
  sink **additionally** delivers each `println` to that widget as a
  `LogMessage`; it MUST NOT suppress the stream emission. Exactly one
  sink MAY be attached; attaching `null` stops the extra delivery.

## 5. Protected output

This is the part's reason to exist and the requirements are
correspondingly strict. "Only the console instance can write to the
terminal" means these lines, mechanically.

- **CV-14** While the canvas is live, `System.out` and `System.err` MUST be
  wrapped so that application code and third-party libraries printing
  directly cannot corrupt the display. Captured lines are treated exactly
  as `println` (CV-12, CV-13).
- **CV-15** Stream capture MUST buffer partial lines until a newline, and
  MUST flush a stale partial line after a short timeout so a caller that
  never writes a newline doesn't hang output forever.
- **CV-16** If something else has already replaced `System.out`, capture
  MUST chain onto it rather than the pristine JVM stream, and MUST note
  the situation once.
- **CV-17** The library's own writes MUST NOT recurse back through the
  capture.
- **CV-18** Capture MUST be restorable to the exact instances it replaced,
  from both a `finally` and a shutdown hook, including when the body
  threw.
- **CV-19** Capture MUST start and stop exactly on the widget-count
  transitions to and from zero.
- **CV-20** Stream capture MUST be disableable by configuration.
- **CV-21** Ordinary printing while the canvas is live MUST be redirected,
  never rejected.
- **CV-22** Application code MUST NEVER write escape sequences, move the
  cursor, or obtain the terminal writer. The only public paths to the
  terminal are `println`-family calls and widget events. A test MUST
  enforce that no public method of this part returns a `Writer`,
  `OutputStream` or terminal handle (NFR-10).

## 6. Placement and layout

- **CV-23** Origin `(0,0)` is the canvas's top-left, `x` is column, `y` is
  row, both 0-based. Widget-local coordinates are relative to the widget's
  content area.
- **CV-24** Placement MUST be *docking* for the common case: each widget
  docks to an edge of the remaining rectangle, in declaration order, and
  the last widget takes what is left.
- **CV-25** Free placement at an explicit Rect MUST also be available, for
  overlays that float above the docked layout rather than consuming space
  from it.
- **CV-26** Per-axis size MUST be expressible as fixed cells, a percentage
  of the canvas, or fill-remaining, with optional min/max clamps. A widget
  MAY additionally declare a content-derived preferred size, which the
  layout uses when the placement says `preferred`. Percentages convert
  with `floor(percent × available / 100)`. After fixed and percentage
  sizes are taken, fill-remaining widgets share what is left, again with
  floor. Leftover cells from rounding go to the last widget on that axis
  that is percentage or fill. The same inputs MUST produce the same
  leftover assignment.
- **CV-27** Terminal size MUST be re-read every loop iteration, never
  cached. On resize the canvas MUST be recomputed, all widgets
  re-laid-out, the front buffer discarded, and a full repaint issued.
- **CV-28** Layout MUST be deterministic: the same canvas size and the
  same widget set and state MUST produce the same Rects.
- **CV-29** Overlap MUST be allowed and resolved by z-index, ties broken
  by declaration order.
- **CV-30** Widgets MUST be clipped to the canvas; a Rect extending past
  an edge is truncated, never wrapped, and never an error.
- **CV-31** A widget whose Rect falls below its declared minimum MUST be
  handled by negotiation — the widget is asked to shrink to its minimum,
  or is hidden with a one-cell indicator — not by blind clipping.
- **CV-32** Nested child widgets are deferred.

## 7. Widget model

- **CV-33** A widget is a Rect with optional border, optional title drawn
  into the top border, and optional padding. Content area = Rect minus
  border minus padding.
- **CV-34** Widgets MUST NOT be able to write outside their content area;
  the framework clips every write.
- **CV-35** The widget interface MUST stay minimal: `preferredSize`,
  `minSize`, `paint(Surface, RenderContext)`, `isDirty`,
  `onEvent(WidgetEvent)`, `onKey(KeyEvent)` returning
  handled/not-handled, `acceptsKeys` (CV-68), the `attached` / `detached`
  lifecycle (CV-96), and the plain-line policy hook (CV-61). `Surface`
  offers `put(x, y, Cell)`, `putText` (with alignment and truncation per
  CR-19), `fill`, `blit(Renderable)`, `blit(List<StyledText>)` for
  cached lines, and `composite(CellBuffer, x, y)` (CV-94).
- **CV-36** Any `Renderable`'s output MUST be blittable into a Rect with
  clipping, so the static catalogue (`Table`, `Panel`, …) works on the
  canvas unchanged. This is the print door written into cells: absent
  channels become space + default style. It is **not** the sprite path.
  A widget that blits the same content every frame MUST cache the
  rendered lines and re-render only when the content or the width
  changes (NFR-5).
- **CV-37** **Dirty gates frames, not widgets.** A widget MUST be able to
  mark itself dirty. A frame is painted only when at least one widget is
  dirty or an invalidation is pending (CV-58); when nothing changed, the
  loop iteration paints nothing and writes nothing. When a frame *is*
  painted, every widget paints (CV-53) and the cell diff (CV-54) is what
  makes one moving bar out of eight cost one bar's worth of bytes.
  `paint` clears the widget's dirty state.
- **CV-38** `place(Widget, Placement)` MUST return a `WidgetHandle`
  offering `id()`, `send(WidgetEvent)`, `focus()`, `invalidate()` and
  `remove()`; `remove` MUST be idempotent. `Dock` constants and `Rect`
  are `Placement`s; a builder adds size, border, title, padding and
  z-index. `place` mints a `WidgetId` that is unique for the life of the
  process and MUST NOT be reused after `remove`, so a late event cannot
  hit a replacement widget. A widget instance MUST be placeable at most
  once. There is no `update()`: events are the only mutation path
  (CV-39), so the minimal event path ships **with** `place` in M3.

## 8. Widget events

The event queue is the only way application state reaches the canvas.

- **CV-39** Widget mutation MUST be expressible as immutable events
  (`WidgetEvent`), drained by the render thread under the render lock,
  so application code holds no locks and widgets need no volatile
  fields. **Events are payloads; the queue holds the envelope.**
  `handle.send(event)` enqueues `Envelope(WidgetId target, WidgetEvent
  event)`; the record itself carries no target, so
  `new ProgressUpdate(61, 100, "encode.mp4")` is a complete event. The
  envelope is what is recorded and serialised (CV-41).
- **CV-40** The core event vocabulary MUST include at least:
  `ProgressUpdate(current, total, label)`, `Indeterminate(label)`,
  `Completed(finalLine)`, `GraphValues(double...)` / `GraphAppend(double)`,
  `LogMessage(StyledText)`, `SetText(String)`, `SetStatus(segment,
  StyledText)`, `SetContent(Renderable)`, the task events of CV-66, the
  sprite playback events of CV-91, and an opaque `Custom(payload)` for
  user-defined widgets. Widgets MUST ignore events they don't
  understand. Every event type MUST declare its coalescing class
  (CV-43). `Tick` is part of the sealed hierarchy so widgets receive it
  through `onEvent`, but it is engine-only (CV-95).
- **CV-41** The envelope stream MUST be recordable and replayable, and
  MUST be serialisable well enough that a child process could drive a
  parent's canvas over a pipe. This is a design constraint on the event
  model now, not a feature to be delivered now. `SetContent(Renderable)`
  and `Custom` are in-process only until that feature is built (map,
  open question 7).
- **CV-42** The object API (`bar.step()`, `log.append(...)`) MUST be a
  thin facade over the event stream. Two APIs, one implementation. The
  facade lives on the **widget** (CV-96).
- **CV-43** Events MUST be safe to send concurrently from multiple threads,
  including virtual threads; the queue MUST be bounded, with this
  overflow policy:
  - Each event type is `LATEST` or `NEVER`. `LATEST` (`ProgressUpdate`,
    `Indeterminate`, `SetText`, `SetStatus` per segment, `GraphValues`,
    `SetContent`, `TaskProgress` per task, `SetFrame`, `SetSpriteSpeed`,
    `SetCycleOffset` per group) means a newer event with the same target
    and key supersedes an older one. `NEVER` (`LogMessage`,
    `GraphAppend`, `Completed`, `TaskLog`, `TaskCompleted`, `PlaySprite`,
    `PauseSprite`, `Custom`) means every event is delivered, in order.
  - On overflow: coalesce `LATEST` events; if still full, drop the oldest
    `LATEST` event; if the queue holds only `NEVER` events, `send`
    **blocks the producer** until the render thread drains.
    Back-pressure, not data loss. A `send` made *from* the render thread
    MUST NOT block and bypasses the bound.
  - The numeric bound is open (map, open question 1).
- **CV-44** Sending an event to a removed widget MUST be a no-op, never an
  error. Sending before the engine has started MUST start it (CV-9).
  Sending a `Tick` MUST throw `IllegalArgumentException` (CV-95).
- **CV-95** `Tick(frame, delta, sinceStart, wallClock)` MUST be
  **synthesised by the render loop** and delivered to every placed
  widget at the start of every loop iteration, whether or not a frame is
  then painted. It MUST NOT pass through the queue, so it can be neither
  coalesced nor dropped, and a time-based widget cannot lose time.
  `delta` is the monotonic time since the previous tick, `sinceStart`
  the monotonic time since the engine started, `wallClock` an `Instant`
  — the render loop is the only place either clock is read. The recorder
  MUST capture ticks so a replay is deterministic; only the engine and
  `EventReplayer` may deliver one.

## 9. Keyboard input (`KeyListener`)

A key is a **single-consumer message**: it is offered to one listener at
a time until that listener returns handled. There is one contract,
`onKey`, whose return value is the only taxonomy that matters.

- **CV-45** Raw mode MUST be entered when the canvas becomes live **and**
  at least one placed widget declares `acceptsKeys()`, or the application
  has registered a key handler; it MUST be exited in a `finally` on every
  path when neither holds. Widgets that don't need keys don't pay for
  raw mode. Raw mode MUST leave terminal **signal generation enabled**
  (`ISIG` on POSIX, processed input on Windows) — see CV-49.
- **CV-46** `KeyListener` MUST decode raw input into a semantic `KeyEvent`
  (key, modifiers, printable char if any, decoded per CR-37 — this is
  where CR-37 is applied); widgets MUST NOT parse escape sequences
  themselves.
- **CV-47** Routing rule: a key event is offered to the **focused widget
  first** (if any); if it returns not-handled or there is no focused
  widget, to the **application key handler** (if any); if still not
  handled, to the **built-in bindings**. Exactly this order, no
  exceptions. The first listener that returns handled consumes the event;
  later listeners MUST NOT see it. Broadcasting a key to every widget is
  a defect.
- **CV-48** Focus is a slot, not a form navigator. At most one widget is
  focused. Placing a widget that `acceptsKeys()` focuses it if nothing is
  focused. `handle.focus()` sets the slot explicitly. Removing the
  focused widget MUST clear the slot. `Tab` / `Shift-Tab` cycling, caret
  drawing, and a visual "selection" chrome for the focused widget are
  out of scope (CV-69).
- **CV-49** **`Ctrl-C` stays a signal.** Because raw mode keeps signal
  generation on (CV-45), `Ctrl-C` MUST NOT arrive as a `KeyEvent`; it
  raises `SIGINT`, the JVM runs its shutdown hooks, the CR-26 restore
  path restores the terminal, and the process exits as it would without
  the library (status 130 by default). There is no `Cancelled` exception
  and nothing is thrown across threads. `console.onCancel(Runnable)` MAY
  register one best-effort hook that runs on that path before restore.
  The only built-in *key* binding is `Ctrl-L` → force full repaint. The
  library MUST NOT ship widget-level form bindings (arrows, `Enter`,
  `Esc`, `Backspace` editing, printable-character filters). A shipped
  widget MAY consume a specific key to cycle its own visualisation by
  returning handled from `onKey`.
- **CV-50** *(retired)* v2 required one scrollback line recording an
  interactive widget's choice or cancellation. That applied to forms,
  which are out of scope (CV-67, CV-69); this ID MUST NOT be reopened as
  a form requirement.
- **CV-51** Keys MUST be delivered on the render thread, interleaved with
  event draining, so a widget never sees a key and an event concurrently.
- **CV-52** Key reading MUST NOT block rendering: the listener runs on its
  own daemon thread and hands decoded events to the render queue.
- **CV-68** Every widget MUST implement one `onKey` contract:
  `boolean onKey(KeyEvent)` returning handled / not-handled, defaulting
  to not-handled, plus `boolean acceptsKeys()` defaulting to false.
  Returning not-handled (or never being offered the event) is how a
  widget ignores keys. Returning handled for a subset of keys is how a
  widget cycles a visualisation. The library MUST NOT classify widgets
  into ignore / some-keys / all-keys types.
- **CV-69** This library MUST NOT ship menus, confirm dialogs,
  text-input widgets, validation, masks, numeric-only filters, or
  completion. Those MAY appear in a later library that reuses CV-47 and
  CV-68 unchanged. Their absence MUST NOT be filled by stretching a
  canvas widget into a form.

## 10. Rendering

- **CV-53** Double buffering is REQUIRED; widgets paint into the back
  buffer only. Both buffers are core `CellBuffer`s (CR-46). When a frame
  is painted (CV-37) the back buffer MUST first be filled with **blank**
  — space, `Color.DEFAULT`, `Color.DEFAULT` — and every widget MUST
  paint, in z-order, so after the paint pass no cell is absent and
  overlays composite over whatever is actually beneath them.
- **CV-54** Flush MUST diff back against front and emit only changed
  cells, coalescing runs on a row into one cursor-move plus write,
  suppressing redundant style escapes by tracking the current style.
  Identical consecutive frames MUST be a no-op.
- **CV-55** A frame MUST be written in one flush, into a reused byte
  buffer.
- **CV-56** The cursor MUST be hidden while the canvas is live and parked
  at a defined position after each flush. Showing the cursor at a caret
  is out of scope (CV-69).
- **CV-57** One platform daemon render thread per process — not virtual —
  started lazily on the first placed widget and stopped when the last is
  removed. Default refresh ~12.5 fps, configurable within 1–30, never
  faster.
- **CV-58** An immediate off-cycle redraw MUST be forced on resize, on
  any scrollback write, on widget completion, on `handle.invalidate()`
  and on `Ctrl-L`; multiple invalidations MUST coalesce into one frame.
- **CV-59** The library owns the loop.
- **CV-94** `Surface.composite(CellBuffer src, x, y)` MUST composite onto
  the back buffer with **per-channel absence** by delegating to core's
  `CellBuffer.composite` with the widget's clip (CR-41, CR-46, BM-3).
  This part MUST NOT implement the absence rule itself. This is the
  overlay paint door. `AsciiSprite.paint` MUST use it.
  `blit(Renderable)` (CV-36) MUST NOT be used for sprites: a round-trip
  through `StyledText` would punch rectangular holes.

## 11. Degradation

- **CV-60** When not a TTY, the cursor MUST NOT be moved at all and no
  canvas MUST be painted.
- **CV-61** When not a TTY, every widget MUST degrade to plain lines by a
  per-widget policy: a progress bar emits a periodic line at most every
  5 s or every 10 %, whichever is rarer; a spinner emits one start and one
  result line; a log pane passes lines straight through; static widgets
  print once. The policy is a hook on the widget contract
  (`onEventPlain(event, lineSink)`), so it is unit-testable with no
  terminal.
- **CV-62** *(retired as a prompt rule)* This library ships no prompts
  (CV-69). When not a TTY, `KeyListener` MUST NOT start and a registered
  key handler MUST NOT be invoked.
- **CV-63** Piping any demo to a file MUST produce zero escape bytes.

## 12. Widget catalogue

- **CV-64** Static (`Renderable`, printable or blittable): `Table`, `Box`,
  `KeyValueBlock`, `Tree`, `Sparkline`, `Rule`, `PatternHighlighter`.
  Anything else implementing the core contract — `AsciiBitmap` (BM-4) —
  is usable here without being listed.
- **CV-65** Canvas widgets, all event-driven: `Label`, `StatusBar` (N
  segments, independent alignment), `Clock` (self-updating from the
  `wallClock` on `Tick`), `ProgressBar` (determinate and indeterminate),
  `Spinner`, `MultiProgress`, `StatusList`, `Graph` (line/bar over a
  value window, fed by `GraphValues`/`GraphAppend`), `LogPane` (ring
  buffer, auto-scroll, attachable as log sink per CV-13), `Panel` (border
  and title, as a container for a `Renderable` set with `SetContent` and
  cached per CV-36), `AsciiSprite` (CV-90). That shipped list is
  **closed**. `Widget` is public; applications that need something else
  implement `Custom` (CV-40) or their own `Widget`.
- **CV-66** `MultiProgress` MUST offer four policies for log lines
  attributed to a running task — drop, attributed, buffered-per-task,
  and promote — with promote as the default and buffered capped
  oldest-dropped. Tasks are **not** widgets and have no `WidgetId`:
  `mp.task(label)` returns a `TaskHandle` whose methods send
  `TaskProgress(taskId, current, total, label)`, `TaskLog(taskId,
  StyledText)` and `TaskCompleted(taskId, finalLine)` to the one
  `MultiProgress` widget.
- **CV-67** *(retired)* v2 listed interactive form widgets (`SelectMenu`,
  `MultiSelectMenu`, `Confirm`, `TextInput`). They are out of scope
  (CV-69). Canvas widgets MAY implement `onKey` to cycle their own
  visualisation; they MUST NOT become forms.
- **CV-96** A widget MUST learn its handle through a lifecycle callback:
  `attached(WidgetHandle)` on the render thread when placed,
  `detached()` when removed. The object API (CV-42) lives on the widget
  and sends through that handle. Called **before** placement, a facade
  method MUST apply the same event synchronously to the widget — nothing
  else can see it yet — so there is still one implementation.

## 13. Sprites — the canvas side of the bitmap part

The bitmap part owns the value and the timing *maths* (BM-8…BM-15); this
part owns the only thing that has a clock. The split is the reason a
sprite has no thread of its own.

- **CV-90** `AsciiSprite` MUST be a canvas widget wrapping an
  `AsciiAnimation` (BM-8) and nothing more. Its state is: an
  elapsed-time accumulator, a speed factor, playing / paused, loop /
  once, an optional pinned frame, optional pinned cycle offsets, one
  scratch `CellBuffer` of the animation's bounds and one `int[]` of
  offsets, both allocated at construction.
  - On `Tick` (CV-95), while playing, the accumulator advances by
    `delta × speed`.
  - The frame is the pinned frame if set; else `frameIndexAt(elapsed)`
    when looping; else, when playing once, the last frame as soon as
    `elapsed ≥ totalDuration()`. Cycle offsets are the pinned value if
    set, else `cycleOffsetAt(group, elapsed)` — cycles keep running
    while a play-once sprite holds its last frame.
  - `paint` re-resolves the scratch buffer with the index-addressed
    primitive (BM-15) only when frame or offsets changed, then
    composites it (CV-94), positioned in its Rect per the animation's
    anchor.
  - It MUST NOT own a thread, sleep, or read a clock. Because selection
    is by absolute elapsed time, a slow loop **skips** frames rather
    than queueing them, and playback speed is independent of the refresh
    rate (CV-57).
  - Construct with `new AsciiSprite(Art.load(...))` or
    `new AsciiSprite(animation)`. There is no `AsciiSprite.load`.
- **CV-91** Playback MUST be expressible as widget events (CV-39):
  `PlaySprite(loop)` — which also clears any pins — `PauseSprite`,
  `SetFrame(index)`, `SetSpriteSpeed(factor)`,
  `SetCycleOffset(group, n)`, with the object API (`sprite.play()`,
  `sprite.playOnce()`, `sprite.pause()`, `sprite.frame(i)`) as a thin
  facade over them (CV-42, CV-96).
- **CV-92** A sprite MUST report itself dirty only when the selected
  frame or any cycle offset actually changed (CV-37). A paused sprite,
  and a one-frame sprite with no cycle group, never make a frame happen
  on their own. When not a TTY (CV-60, CV-61) a sprite MUST NOT animate:
  its plain-line policy is to print its first frame once through the
  print door, or nothing when configured silent.
- **CV-93** Sprite behaviour MUST be assertable without a terminal and
  without sleeping: feeding a sequence of `Tick`s with synthetic deltas
  through `EventReplayer` MUST select frames and cycle offsets
  deterministically (BM-11), and the result MUST be asserted against the
  NFR-3 canvas dump.

---

## 14. Usage sketches

Illustrative of CV-10, CV-13, CV-24, CV-38…CV-40, CV-47, CV-65, CV-90,
CV-96. Surrounding glue is not an API freeze.

```java
try (FancyConsole console = new FancyConsole()) {
    ProgressBar bar   = new ProgressBar();
    StatusBar  status = new StatusBar(4);
    console.place(bar, Dock.TOP);
    console.place(new Clock(), Dock.RIGHT);
    WidgetHandle statusHandle = console.place(status, Dock.BOTTOM);
    console.place(new Panel("main"), Dock.FILL);

    console.println("scanning input/");
    console.println("found 1 204 files");

    bar.update(61, 100, "encode.mp4");                       // object API …
    statusHandle.send(new SetStatus(0, StyledText.of("connected")));  // … or events
    // Clock paints itself from Tick; the application does not drive it.
}
```

Object API is the same events, on the widget (CV-42, CV-96):

```java
bar.step();                                 // ProgressUpdate
bar.indeterminate("waiting");               // Indeterminate
bar.complete("done");                       // Completed
log.append(StyledText.of("hello"));         // LogMessage
graph.append(0.42);                         // GraphAppend
label.setText("ready");                     // SetText
panel.setContent(Table.of(headers, rows));  // SetContent → cached blit (CV-36)
```

`logTo` is additional, never instead-of (CV-13):

```java
LogPane log = new LogPane();
console.place(log, Dock.FILL);
console.logTo(log);
console.println("still on the stream, and in the pane");
console.logTo(null);                        // extra delivery off; stream stays
```

Free placement overlays the docked layout; it does not consume space
(CV-25):

```java
console.place(new Label("toast"), Rect.of(2, 1, 24, 3));
```

`Graph` may consume a key to cycle its own visualisation (CV-68). That
is `onKey`, not a form (CV-69). `Ctrl-C` needs no handler (CV-49):

```java
Graph graph = new Graph();                  // onKey('g') → line/bar, handled
console.place(graph, Dock.FILL);
graph.values(0.1, 0.4, 0.3, 0.8);           // GraphValues
console.onKey(e -> {
    if (e.key() == Key.Q) { console.close(); return true; }
    return false;                           // unhandled → built-ins
});
console.onCancel(() -> journal.flush());    // optional, runs on SIGINT path
```

A sprite is an animation plus this part's clock (CV-90, CV-91, CV-94):

```java
AsciiSprite spinner = new AsciiSprite(Art.loadResource("/art/spinner.art"));
console.place(spinner, Rect.of(2, 1, 5, 3));
spinner.play();                             // PlaySprite(loop = true)
spinner.pause();                            // PauseSprite → not dirty (CV-92)
spinner.frame(2);                           // SetFrame → pinned until play()
```

`MultiProgress` (CV-66), promote default:

```java
MultiProgress mp = new MultiProgress();
console.place(mp, Dock.TOP);
TaskHandle encode = mp.task("encode.mp4");
encode.update(61, 100);                     // TaskProgress
encode.log("frame 1200");                   // TaskLog — promote: line goes up
encode.complete("encoded");                 // TaskCompleted
```

---

## 15. Canvas NFRs and the CR-39 fixture

IDs kept from the v3 / core list. They live here because they are about
the canvas engine, not the commons.

- **NFR-3** The canvas MUST be dumpable to a plain string so layouts can
  be asserted in unit tests with no terminal involved, **and** a virtual
  terminal emulator MUST consume the library's own output, interpret
  cursor and erase sequences, and maintain a cell grid, so tests assert
  *what the user sees* including the scrollback/canvas boundary. Both
  MUST exist before any canvas milestone is called done.
- **NFR-4** A frame at 200×50 MUST lay out, paint, diff and flush in well
  under 16 ms.
- **NFR-5** A steady-state loop iteration MUST NOT allocate in **tick
  delivery, layout, paint, diff or flush**. That holds because buffers
  are packed `CellBuffer`s (CR-46), sprites resolve into a scratch
  buffer through the non-allocating primitive (BM-15), and blitting
  widgets cache their rendered lines (CV-36). Re-rendering a
  `Renderable` after `SetContent` or a width change MAY allocate. The
  event queue, envelopes, `WidgetEvent` records and key decoding MAY
  allocate; this requirement does not apply to them.
- **NFR-6** No flicker, and no scrolling of the terminal other than by
  `println`, during normal operation of canvas mode.
- **NFR-19** **Start-gate (not a milestone):** the conhost rows of NFR-14
  MUST be verified for canvas mode **before M6 starts**. This is not a
  done-criterion of M6 and is not deferred to M7. conhost scrolling under
  `println`-above-region is the risk; macOS rows wait for M7.
- **CR-39** The CR-10 fixture strings MUST be rendered inside a bordered
  `Table` on a non-UTF-8 console in the test suite, **both** as an
  immediate print and blitted into a canvas Rect (CV-36), and the result
  asserted at the byte level through the NFR-3 virtual terminal. "It
  looked fine" is not a passing condition. This is a canvas-test
  requirement; core keeps CR-10 and the encoding rules.

---

## 16. Out of scope for this part

- Mouse support.
- Constraint-solver or flexbox-style content-reflowing layout.
- The alternate screen buffer.
- Nested widgets (CV-32), scrollbars, dialogs, modal windows.
- Forms and smart input (CV-67, CV-69). The `onKey` hook and CV-47
  routing stay; the widgets do not.
- Multiple canvases per process.
- Defining bitmap or animation semantics, or the compositing rule. This
  part consumes BM-8…BM-15 and CR-46 and MUST NOT restate them.
