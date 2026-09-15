# ConsoleKit — Requirements (v3, two-mode architecture)

Consolidated requirements for ConsoleKit. This supersedes the three-tier
`doc/requirements.md` (v2). The change in this revision is structural:
the former tier 2 ("own the bottom of the screen") and tier 3 ("own the
whole screen") are merged into a single **canvas mode**, and the
strip-stacking pin model is retired in favour of widgets placed on a
canvas. Everything about colour, glyphs, encoding, and platform coverage
carries over unchanged and is marked as such.

Every requirement has an ID so individual lines can be argued about,
deferred, or cut. Prefixes:

| Prefix | Applies to |
|---|---|
| `CR-n` | Core — shared by both modes |
| `TX-n` | Mode 1, text mode (`Text`, `Styler`, `Snippets`) |
| `CV-n` | Mode 2, canvas mode (`FancyConsole`, `AsciiCanvas`, `KeyListener`) |
| `NFR-n` | Non-functional, whole library |

`MUST` / `SHOULD` / `MAY` are RFC 2119. Where a requirement restates one
from v2 its old ID is noted as `(was FC-n)` / `(was CV-n)`. A full
old→new map is in §11.

**Reading note for anyone touching existing code:** the code in this
repository was written against v2. §11 says which implemented parts
survive, which are reinterpreted, and which are obsolete. Do not assume a
class is correct because it compiles and has tests.

---

## 1. Purpose

Two modes of console output, sharing one model underneath.

1. **Text mode — colour a string.** No lifecycle, no setup, no terminal
   ownership. Three flavours of the same thing: static inline helpers
   (`red("x")`), a configurable style instance (`styler.highlight("x")`),
   and pre-made snippets (`Snippets.line(Color)`).
2. **Canvas mode — own the terminal.** One `FancyConsole` instance owns
   the terminal for the process. It holds an `AsciiCanvas` on which
   widgets are placed at explicit or docked positions. The application
   drives widgets by sending **widget events** and prints ordinary log
   lines with **direct `println` commands**; it never touches the
   terminal itself. Keyboard input is decoded and **forwarded on a
   single path until handled**: the focused widget, then the application
   handler, then built-in bindings (CV-47). Widgets may ignore keys or
   consume a few (for example to cycle a visualisation). Forms and smart
   input are out of scope (CV-67, CV-69).

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

Why one canvas mode and not two ownership tiers: v2 kept "bottom region +
scrollback" and "whole screen, alternate buffer" apart because each has a
different restore path and coordinate system. In practice the thing an
application wants is *both at once* — free placement of widgets **and**
the ability to keep printing log lines. This revision resolves that by
making the canvas a region of configurable height at the bottom of the
normal screen (defaulting to the full viewport) and routing `println`
above it. One lifecycle, one restore path, one coordinate system. The
alternate screen buffer is dropped (see §9).

### 1.1 The two modes

| | Mode 1 — text | Mode 2 — canvas |
|---|---|---|
| Owns | nothing | the terminal, for the process |
| Coordinates | none | `(x, y)` rects on the canvas; docking for the common case |
| Render unit | `String` | cell grid (widgets paint cells; `Renderable`s are blitted) |
| Scrollback | untouched | preserved above the canvas; `println` goes there |
| Overlap | n/a | yes, z-ordered |
| Lifecycle | none | lazy on first `new FancyConsole()`, shared, auto-restoring |
| Input | none | `KeyListener`: offer until handled (focus, then app, then built-ins) |
| Concurrency | stateless | render thread fed by an event queue |
| Module | `dev.consolekit` | `dev.consolekit` |

### 1.2 Canvas mode, target picture

Canvas **80×12** at the bottom of an 80×24 terminal. CV-7: canvas width
**is** the terminal width; there is no narrower-canvas option. Clock
docked top-right, status bar docked bottom, progress docked top-left.
The three lines above the box are `println` into scrollback. The fenced
block is 80 columns; the box is 12 rows. The other 9 viewport rows of
scrollback are omitted.

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

---

## 2. Terminology

**Cell** — one character position: glyph plus style.
**Canvas** — a `width × height` grid of cells owned by `FancyConsole`,
occupying the bottom `height` rows of the viewport.
**Buffer** — a canvas used for double-buffering (front = on screen, back
= being drawn).
**Rect** — `(x, y, w, h)` in integer character units, canvas-relative.
**StyledText** — a sequence of `(text, style)` spans making up one line.
The render unit of text mode and of `Renderable`.
**Renderable** — anything that produces `List<StyledText>` from a
`RenderContext`. The static widget catalogue implements this; the canvas
blits it into a Rect.
**Widget** — a placed, stateful thing on the canvas that paints cells,
receives widget events, and may receive key events.
**Widget event** — an immutable message from the application (or from a
timer) to one widget, drained by the render thread.
**Key event** — a decoded keypress delivered by `KeyListener`. Offered
to one listener at a time until handled (CV-47).
**Focus** — the at-most-one widget that receives the first offer of a
key event. A slot, not a form navigator (CV-48).
**Scrollback** — the terminal's normal scrolling output, above the canvas.
**Layout pass** — computing each widget's Rect from its placement rules
and the canvas size.
**Paint pass** — widgets writing cells into the back buffer.
**Flush** — emitting the minimal escape stream that makes the terminal
match the back buffer.
**Glyph tier** — `FULL` / `CP437` / `ASCII`, the character repertoire the
current terminal can actually encode.
**Bitmap** — an immutable `w × h` grid of cells: X × Y coloured
characters. A value, not a canvas and not an image (§7).
**Frame** — one bitmap in a sprite's sequence, with a duration.
**Sprite** — a canvas widget that animates a sequence of frames, advanced
by `Tick` on the render thread.

---

## 3. Core requirements (`CR`)

Shared by both modes, implemented once. A canvas-specific colour, width,
or encoding routine is a bug.

### 3.1 Colour and style

- **CR-1** Style MUST be foreground colour, background colour, and
  attribute flags (bold, dim, italic, underline, strike, reverse). Style
  values MUST be immutable.
- **CR-2** Colour depth MUST be one of `NONE`, `ANSI16`, `ANSI256`,
  `TRUECOLOR`, and every colour MUST know how to downgrade itself to a
  lower depth — cube quantisation truecolor→256, nearest perceptual match
  256→16.
- **CR-3** `NO_COLOR` MUST suppress all colour. `FORCE_COLOR` and
  `CLICOLOR_FORCE` MUST force it on. Explicit configuration MUST outrank
  both.
- **CR-4** Attributes the terminal doesn't support MUST be dropped
  cleanly, never emitted as visible garbage.
- **CR-5** A `Theme` MUST map semantic roles (success, warning, error,
  info, muted, highlight, border, selection) to styles, so widgets never
  hard-code colours.
- **CR-40** *(new)* There is exactly **one** colour type across the
  library: `dev.consolekit.core.Color`, with `Color.named(Named)`,
  `Color.index(0..255)`, `Color.rgb(r,g,b)`, and the sixteen named
  constants exposed as static fields (`Color.RED`, `Color.BRIGHT_CYAN`,
  …) so that `line(Color.RED)` reads naturally. The `AsciiColor` name
  from the architecture sketch is a placeholder for this type and MUST
  NOT be introduced as a second class.

### 3.2 Characters and width

- **CR-6** Display width MUST be computed correctly for wide (CJK, emoji),
  zero-width and combining characters, and MUST ignore embedded escape
  sequences. `String.length()` in layout code is a defect.
- **CR-7** Three glyph tiers MUST be supported — `FULL` (Unicode box
  drawing, blocks, marks), `CP437`, and pure `ASCII` — selected by probing
  the console charset's encoder per glyph, not by guessing from `TERM`.
- **CR-8** Glyph tier MUST be overridable by configuration and by
  environment variable.
- **CR-9** Control characters (`\t`, `\r`, `\n`, `ESC`) appearing in widget
  content MUST be sanitised or expanded, never passed through raw.
- **CR-10** `příliš žluťoučký kůň` is the standard fixture string; every
  alignment and width test MUST include it alongside CJK and emoji cases.

### 3.3 Encoding robustness — *unchanged from v2, sustained in full*

CR-6 through CR-10 govern the library's *own* glyphs. This section governs
*caller-supplied content*, which is a different problem: the library
chooses its box-drawing characters and can downgrade them, but it does not
choose the strings an application prints, and those strings arrive in
whatever the caller's data contains.

The failure this section exists to prevent: a table measured as 20 columns
wide whose content the console encoder turns into a different number of
characters, so the column rules no longer line up — on the platforms least
likely to produce a bug report. On a cell canvas this failure is worse,
not better: a single mis-measured string shifts every cell to its right.

- **CR-28** The output charset MUST be resolved explicitly, from
  `stdout.encoding`, `Console.charset()` and `native.encoding` in a defined
  precedence order, and MUST be recorded in capabilities. The library MUST
  NOT assume UTF-8. Redirected output MUST be resolved separately from
  console output, since they can differ within one process.
- **CR-29** Every string the library writes MUST be checked as encodable in
  the resolved output charset. Unencodable content MUST be substituted
  before it reaches the writer, never handed to the encoder to mangle.
- **CR-30** Substitution MUST be width-preserving as measured by CR-6:
  either the replacement occupies exactly the display width the original
  was measured at, or the measurement is redone after substitution and
  before layout. Layout MUST NOT be computed on one string and emitted as
  another. On the canvas this means substitution happens **before** a
  string is written into cells, never at flush.
- **CR-31** The substitution policy MUST be transliterate-then-replace —
  an ASCII fold where a reasonable one exists (`ř`→`r`, `ü`→`u`),
  otherwise a single-width replacement character — and MUST be
  configurable.
- **CR-32** Substitution MUST be noted once via the capability degradation
  reason (CR-12), so a user seeing `prilis zlutoucky kun` can find out why
  without reading source.
- **CR-33** The library MUST NOT change the terminal's code page or
  charset. It is process-global state that outlives the JVM's ownership of
  the console, and changing it is a side effect on the user's shell.
- **CR-34** Caller-supplied text MUST be normalised to NFC before
  measurement and emission. macOS supplies NFD in filenames and clipboard
  content; measuring one form and emitting the other is a width defect.
- **CR-35** Truncation and wrapping MUST operate on grapheme cluster
  boundaries. The library MUST NOT emit a lone surrogate, an orphaned
  combining mark, or a severed ZWJ emoji sequence. A cell canvas MUST
  store a wide grapheme as one cell of width 2 plus a continuation marker,
  never split it across two independently-styled cells.
- **CR-36** Malformed input — unpaired surrogates, invalid byte sequences,
  a BOM mid-stream — MUST NOT throw and MUST NOT abort a render. Replace
  and continue, per CR-24.
- **CR-37** Keyboard input MUST be decoded using the resolved input
  charset, so a key such as a letter with a diacritic is one `KeyEvent`
  on a non-UTF-8 console. *(gates CV-46)*
- **CR-38** `Probe` MUST report the resolved output charset, the raw
  `stdout.encoding` / `native.encoding` / `Console.charset()` values it was
  derived from, and on Windows the active code page — since when these
  disagree, that disagreement is the bug.
- **CR-39** The CR-10 fixture strings MUST be rendered inside a bordered
  `Table` on a non-UTF-8 console in the test suite, **both** as an
  immediate print and blitted into a canvas Rect, and the result asserted
  at the byte level through the NFR-3 virtual terminal. "It looked fine"
  is not a passing condition for this requirement.

### 3.4 Capability detection

- **CR-11** Capabilities MUST be resolved once per process from, in
  precedence order: explicit configuration, `NO_COLOR`,
  `FORCE_COLOR`/`CLICOLOR_FORCE`, TTY-ness, `TERM`, `TERM_PROGRAM`,
  `WT_SESSION`, `COLORTERM`.
- **CR-12** Capabilities MUST carry colour depth, glyph tier, TTY-ness,
  supported attributes, a **probe-time** terminal size (diagnostic only
  — MUST NOT be used for layout; see CR-17, CV-27), output and input
  charset, and a human-readable reason when anything was degraded.
- **CR-13** Non-TTY output (pipe, file, CI) MUST be detected.
- **CR-14** Explicit configuration MUST be settable at most once, before
  any output; a later attempt MUST fail loudly rather than silently
  produce inconsistent output.
- **CR-15** A standalone diagnostic (`Probe`) MUST print the full resolved
  environment plus a colour and glyph test card, in pure ASCII so it
  survives any terminal.

### 3.5 The shared render contract

- **CR-16** `Renderable` MUST be a pure function from a render context to
  a list of styled lines: same context, same widget state, same output.
  No cursor access, no global state, no I/O.
- **CR-17** The In canvas mode it MUST also carry the per-frame canvas
  size from CV-27. Renderables and widgets MUST be given their context
  and MUST NOT reach for process-wide state. Reading
  `Capabilities.size()` for layout after a resize is a defect; the
  context is the only size that is current MUST be given their context and
  MUST NOT reach for process-wide state.
- **CR-18** The static widget catalogue (`Table`, `Box`, `KeyValueBlock`,
  `Tree`, `Sparkline`, `Rule`, `PatternHighlighter`) MUST be usable from
  both modes: printed as a `String` (text mode, via `Renderable.toString`
  with the ambient context), printed immediately into scrollback (canvas
  mode `print(Renderable)`), or blitted into a canvas Rect (CV-24).
- **CR-19** Text placed into a bounded area MUST support left/centre/right
  alignment and a truncation policy of clip or ellipsis, with the ellipsis
  glyph chosen by tier.
- **CR-20** Optional word wrapping MUST be available for multi-line
  content areas.

### 3.6 Terminal safety

- **CR-21** Only one class MAY emit escape sequences.
- **CR-22** Only one class MAY touch the underlying terminal library
  (JLine).
- **CR-23** Only one class MAY contain non-ASCII character literals.
  Raising `Cancelled` on `Ctrl-C` (CV-49) is **not** a render path and
  is not a violation of this requirement.
- **CR-24** No render path MAY throw. A failure in rendering MUST be
  caught, MUST disable further animation, MUST warn once, and MUST fall
  back to plain output. A broken widget MUST NOT crash the caller's job.
- **CR-25** The library MUST NOT log through SLF4J or any logging
  framework.
- **CR-26** Whatever a mode changes about terminal state MUST be restored
  on normal exit, on exception, on `SIGINT`, and on `SIGTERM`. Restore
  MUST be idempotent, because more than one path can fire.
- **CR-27** Leaving the user's shell with a hidden cursor, an active SGR
  state, raw mode, or a replaced `System.out` is the library's most severe
  defect class. Each restore path MUST have a test.

---

## 4. Mode 1 — text mode (`TX`)

Three surfaces, one implementation. All three return plain `String`s with
escapes already applied; none of them prints on its own unless the method
name says `print`.

### 4.1 Common rules

- **TX-1** Colouring MUST be available as stateless static helpers
  returning a plain `String` with escapes already applied.
- **TX-2** Text mode MUST NOT open a terminal, load native code, or start
  a thread — ever, under any code path. It MAY read the resolved
  capabilities (colour depth, glyph tier, and the probe-time width from
  TX-15) because those come from the JDK and environment, not from JLine.
  Discovering width via JLine is a defect.
- **TX-3** When the environment can't render escapes, text mode MUST
  return the string unchanged rather than emitting anything.
- **TX-4** Text mode MUST NOT measure, wrap, pad or align caller text, so
  its output is safe to embed inside canvas-mode output. (Snippets that
  need a width take it as an argument or read the detected terminal width
  from capabilities; they do not measure the caller's strings.)
- **TX-5** Each styled span MUST be closed with its specific SGR "off"
  code, never a blanket reset, so surrounding styles survive nesting.
- **TX-6** The known consequence of TX-5 — nested spans of the same
  category are not stack-aware — MUST be documented and pinned by a test
  rather than silently fixed.
- **TX-7** The first time text mode emits an escape it MUST register a
  shutdown hook resetting SGR on the original stdout, disableable by
  environment variable.

### 4.2 Static inline helpers (`Text`)

- **TX-8** `Text` MUST offer, as static methods designed for
  `import static`: the sixteen named colours (`red`, `brightRed`, …),
  `fg(Color, s)`, `bg(Color, s)`, the six attributes (`bold`, `dim`,
  `italic`, `underline`, `strike`, `reverse`), the theme roles (`success`,
  `warning`, `error`, `info`, `muted`, `highlight`), and `styled(Style, s)`.
  Each MUST have a `(String)` and a `(String format, Object... args)`
  overload.
- **TX-9** Theme-role helpers on `Text` use `Theme.DEFAULT`. Changing the
  theme is what `Styler` is for; `Text` MUST NOT grow mutable state.

### 4.3 Dynamic style instances (`Styler`)

- **TX-10** `Styler` MUST be an immutable value holding a `Theme` (or a
  role→`Style` map) and offering the same method names as `Text`'s role
  helpers as **instance** methods: `styler.highlight("x")`,
  `styler.success("x")`, `styler.error("x")`, etc., plus
  `styler.styled(Role, s)`.
- **TX-11** `Styler` MUST be constructible from a `Theme`
  (`Styler.of(Theme.MONOCHROME)`) and MUST offer `with(Role, Style)`
  returning a new instance, so one line of setup can say "highlight is
  bold cyan on this instance".
- **TX-12** `Styler` MUST apply the same capability gating and SGR-close
  rules as `Text` (TX-3, TX-5). It is a facade over the same code, not a
  second implementation.

### 4.4 Pre-made snippets (`Snippets`)

- **TX-13** `Snippets` MUST offer glyph-tier-aware, colourable one-liners
  returning `String`: `line(Color)` and `line(Color, int width)` (a
  horizontal rule), `rule(Color, String title)`, `ok(String)` /
  `fail(String)` / `warn(String)` (mark plus text), `bullet(Color, String)`,
  `banner(Color, String)`, and `bar(Color, double fraction, int width)`
  (a static progress bar). The catalogue MAY grow; each entry MUST
  degrade to `ASCII`.
- **TX-14** For each `String`-returning snippet a `print…` convenience
  MUST exist (`Snippets.printLine(Color)`), writing the snippet plus a
  newline to `System.out`. These are the *only* text-mode methods that
  print, and they MUST go through `System.out` exactly as the caller
  would, never through a terminal port.
- **TX-15** Snippets that need a width and are not given one MUST resolve
  it, in this order: the `COLUMNS` environment variable if it parses as a
  positive integer; else 80. The library MUST NOT open a terminal or call
  JLine to discover width (TX-2). Many interactive shells will hit the
  80 fallback because bash does not export `COLUMNS` to child processes;
  that is not a defect and MUST NOT be "fixed" by reaching for JLine.

### 4.5 Usage sketches (text mode)

Illustrative. Method names that are already required (`Text.red`,
`Styler.of`, `Snippets.line`, …) are frozen; surrounding glue is not.

```java
import static dev.consolekit.Text.red;
import static dev.consolekit.Text.success;

System.out.println(success("done") + " in " + red("12ms"));
```

```java
Styler s = Styler.of(Theme.DEFAULT)
        .with(Theme.Role.HIGHLIGHT, Style.fg(Color.BRIGHT_CYAN).bold());
System.out.println("found " + s.highlight("42") + " matches");
```

```java
System.out.println(Snippets.rule(Color.CYAN, "scan"));
System.out.println(Snippets.ok("indexed 1 204 files"));
System.out.println(Snippets.bar(Color.GREEN, 0.61, 40));
Snippets.printLine(Color.RED);   // TX-14: the only text-mode print
```

Static catalogue — same `Renderable` in both modes (CR-18, CV-36):

```java
Table t = Table.of(
        List.of("file", "size"),
        List.of(List.of("příliš žluťoučký kůň.dat", "12K"),
                List.of("encode.mp4", "1.1G")));
System.out.println(t);                    // text mode, ambient context
console.print(t);                         // canvas mode, into scrollback
surface.blit(t);                          // canvas mode, into a Rect
```

---

## 5. Mode 2 — canvas mode (`CV`)

### 5.1 Ownership model

- **CV-1** Construction MUST require no arguments and no setup
  (`new FancyConsole()`). *(was FC-1)*
- **CV-2** Multiple `FancyConsole` instances MUST be permitted — library
  code cannot be expected to coordinate — and MUST share one underlying
  terminal, one canvas, one key listener, one event queue and one render
  loop, so they cannot corrupt each other's frames. *(was FC-2)*
- **CV-3** Close MUST be idempotent and MUST remove only the widgets that
  instance placed. The shared terminal MUST be restored exactly once, by
  whichever fires first of the last close, the shutdown hook, or the
  interrupt handler. *(was FC-3)*
- **CV-4** There MUST be exactly one canvas per process. *(was FC-5)*
- **CV-5** Everything rendered MUST be either a write into scrollback
  (above the canvas) or a paint of the canvas. There is no third thing.
  *(was FC-4)*
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
  screen buffer is NOT used. *(replaces v2 CV-2; see §9)*
- **CV-7** Canvas width MUST be the terminal width, re-read per frame
  (CV-27). Canvas height MUST be clamped to the terminal height.
- **CV-8** A minimum canvas size MUST be enforced; below it the library
  MUST render a single "terminal too small" line instead of a layout.
  *(was CV-3)*
- **CV-9** The render engine MUST be started lazily on the first widget
  placed and stopped when the last widget is removed; while no widget is
  placed, `println` MUST go straight to the terminal with no capture and
  no cursor movement, so an application that only ever calls `println`
  pays nothing.

### 5.2 Direct println commands

- **CV-10** `FancyConsole` MUST offer `print`, `println`, `printErr`,
  `blankLine`, and `print(Renderable)`; these write into scrollback above
  the canvas. *(was FC-4, FC-8 "immediate")*
- **CV-11** An immediate multi-line print MUST be atomic — no other output
  and no canvas repaint may interleave inside it. *(was FC-9)*
- **CV-12** While the canvas is live, scrollback writes MUST be routed so
  that the new line appears above the canvas and the canvas is repainted
  intact. The two MUST NOT smear. *(was FC-14)*
- **CV-13** Scrollback lines MUST always be emitted to the underlying
  stream, including when the canvas fills the whole viewport (they
  scroll off immediately, but a redirected or captured stdout still
  sees them). An application that wants them *visible* MUST attach a
  `LogPane` as the log sink (`console.logTo(logPane)`). Attaching a
  sink **additionally** delivers each `println` to that widget as a
  `LogMessage`; it MUST NOT suppress the stream emission. Exactly one
  sink MAY be attached; attaching `null` stops the extra delivery.

### 5.3 Protected output — *unchanged from v2 §5.3*

This is the mode's reason to exist and the requirements are
correspondingly strict. "Only the console instance can write to the
terminal" means these lines, mechanically.

- **CV-14** While the canvas is live, `System.out` and `System.err` MUST be
  wrapped so that application code and third-party libraries printing
  directly cannot corrupt the display. Captured lines are treated exactly
  as `println` (CV-12, CV-13). *(was FC-15)*
- **CV-15** Stream capture MUST buffer partial lines until a newline, and
  MUST flush a stale partial line after a short timeout so a caller that
  never writes a newline doesn't hang output forever. *(was FC-16)*
- **CV-16** If something else has already replaced `System.out`, capture
  MUST chain onto it rather than the pristine JVM stream, and MUST note
  the situation once. *(was FC-17)*
- **CV-17** The library's own writes MUST NOT recurse back through the
  capture. *(was FC-18)*
- **CV-18** Capture MUST be restorable to the exact instances it replaced,
  from both a `finally` and a shutdown hook, including when the body
  threw. *(was FC-19)*
- **CV-19** Capture MUST start and stop exactly on the widget-count
  transitions to and from zero. *(was FC-20)*
- **CV-20** Stream capture MUST be disableable by configuration.
  *(was FC-21)*
- **CV-21** Ordinary printing while the canvas is live MUST be redirected,
  never rejected. *(was FC-22)*
- **CV-22** Application code MUST NEVER write escape sequences, move the
  cursor, or obtain the terminal writer. The only public paths to the
  terminal are `println`-family calls and widget events. *(was FC-28,
  first half)*

### 5.4 Placement and layout

- **CV-23** Origin `(0,0)` is the canvas's top-left, `x` is column, `y` is
  row, both 0-based. Widget-local coordinates are relative to the widget's
  content area. *(was CV-6)*
- **CV-24** Placement MUST be *docking* for the common case: each widget
  docks to an edge of the remaining rectangle, in declaration order, and
  the last widget takes what is left. *(was CV-7)*
- **CV-25** Free placement at an explicit Rect MUST also be available, for
  overlays that float above the docked layout rather than consuming space
  from it. *(was CV-8)*
- **CV-26** Per-axis size MUST be expressible as fixed cells, a percentage
  of the canvas, or fill-remaining, with optional min/max clamps. A widget
  MAY additionally declare a content-derived preferred size, which the
  layout uses when the placement says `preferred`. Percentages convert
  with `floor(percent × available / 100)`. After fixed and percentage
  sizes are taken, fill-remaining widgets share what is left, again with
  floor. Leftover cells from rounding go to the last widget on that axis
  that is percentage or fill. The same inputs MUST produce the same
  leftover assignment (CV-28). *(was CV-9; the deferred option is now
  in — `LogPane` and `ProgressBar` both need it)*
- **CV-27** Terminal size MUST be re-read per frame, never cached. On
  resize the canvas MUST be recomputed, all widgets re-laid-out, the front
  buffer discarded, and a full repaint issued. *(was FC-27, CV-4, CV-5)*
- **CV-28** Layout MUST be deterministic: the same canvas size and the
  same widget set and state MUST produce the same Rects. *(was CV-10)*
- **CV-29** Overlap MUST be allowed and resolved by z-index, ties broken
  by declaration order. *(was CV-11)*
- **CV-30** Widgets MUST be clipped to the canvas; a Rect extending past
  an edge is truncated, never wrapped, and never an error. *(was CV-12)*
- **CV-31** A widget whose Rect falls below its declared minimum MUST be
  handled by negotiation — the widget is asked to shrink to its minimum,
  or is hidden with a one-cell indicator — not by blind clipping.
  *(was CV-13, FC-10)*
- **CV-32** Nested child widgets are deferred — see §9. *(was CV-14)*

### 5.5 Widget model

- **CV-33** A widget is a Rect with optional border, optional title drawn
  into the top border, and optional padding. Content area = Rect minus
  border minus padding. *(was CV-15)*
- **CV-34** Widgets MUST NOT be able to write outside their content area;
  the framework clips every write. *(was CV-16)*
- **CV-35** The widget interface MUST stay minimal: `preferredSize`,
  `minSize`, `paint(Surface)`, `isDirty`, `onEvent(WidgetEvent)`,
  `onKey(KeyEvent)` returning handled/not-handled, and `acceptsKeys`
  (CV-68). `Surface` offers put-cell, put-text (with alignment and
  truncation per CR-19), fill-rect, and `blit(Renderable)`.
  *(was CV-17, FC-11)*
- **CV-36** Any `Renderable`'s output MUST be blittable into a Rect with
  clipping, so the static catalogue works on the canvas unchanged.
  *(was CV-24)*
- **CV-37** A widget MUST be able to mark itself dirty so that one moving
  bar out of eight repaints alone. *(was CV-18, FC-11)*
- **CV-38** Placing a widget MUST return a handle offering `update`,
  `send(WidgetEvent)`, `focus`, and `remove`; `remove` MUST be idempotent.
  `place` mints a `WidgetId` that is unique for the life of the process
  and MUST NOT be reused after `remove`, so a late event cannot hit a
  replacement widget. The handle exposes that id. `handle.send` stamps
  it onto the event; each `WidgetEvent` record carries `target()` as a
  field (CV-39, CV-41). `update` / `focus` / `remove` ship in M3;
  `send` ships in M4. *(was FC-12)*

### 5.6 Widget events

The event queue is the only way application state reaches the canvas.

- **CV-39** Widget mutation MUST be expressible as immutable events
  (`WidgetEvent`), addressed to a widget id, drained by the render thread
  under the render lock, so application code holds no locks and widgets
  need no volatile fields. *(was FC-28, second half)*
- **CV-40** The core event vocabulary MUST include at least:
  `ProgressUpdate(current, total, label)`, `Indeterminate(label)`,
  `Completed(finalLine)`, `GraphValues(double...)` / `GraphAppend(double)`,
  `LogMessage(StyledText)`, `SetText(String)`, `SetStatus(segment,
  StyledText)`, `Tick(frame)`, the sprite playback events of CV-78
  (`PlaySprite`, `PauseSprite`, `SetFrame`, `SetSpriteSpeed`), and an
  opaque `Custom(payload)` for user-defined widgets. Widgets MUST ignore
  events they don't understand.
- **CV-41** The event stream MUST be recordable and replayable, and MUST
  be serialisable well enough that a child process could drive a parent's
  canvas over a pipe. This is a design constraint on the event model now,
  not a feature to be delivered now. *(was FC-29)*
- **CV-42** The object API (`bar.step()`, `log.append(...)`) MUST be a
  thin facade over the event stream. Two APIs, one implementation.
  *(was FC-30)*
- **CV-43** Events MUST be safe to send concurrently from multiple threads,
  including virtual threads; the queue MUST be bounded with a documented
  overflow policy (coalesce same-type events to the same widget first,
  then drop-oldest). *(was FC-31)*
- **CV-44** Sending an event to a removed widget MUST be a no-op, never an
  error. Sending before the engine has started MUST start it (CV-9).

### 5.7 Keyboard input (`KeyListener`)

This resolves v2 open question 1 ("application-level key delivery") with
**yes**. A key is a **single-consumer message**: it is offered to one
listener at a time until that listener returns handled. There are not
three widget types. There is one contract, `onKey`, whose return value
is the only taxonomy that matters. What a widget does inside that method
(ignore, consume a few keys, or later consume almost all keys while
focused) is its own business and MUST NOT grow a second bus.

- **CV-45** Raw mode MUST be entered when the canvas becomes live **and**
  at least one placed widget declares `acceptsKeys()`, or the application
  has registered a key handler; it MUST be exited in a `finally` on every
  path when neither holds. Widgets that don't need keys don't pay for
  raw mode. *(was FC-32, FC-36)*
- **CV-46** `KeyListener` MUST decode raw input into a semantic `KeyEvent`
  (key, modifiers, printable char if any, decoded per CR-37); widgets MUST
  NOT parse escape sequences themselves. *(was FC-33)*
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
  out of scope of this library (CV-69). The slot exists so that a later
  input widget can take first refusal without a second routing rule.
- **CV-49** Built-in bindings MUST include: `Ctrl-C` → cancel (raises
  `Cancelled`, restores the terminal, and re-raises `SIGINT` behaviour to
  the application); `Ctrl-L` → force full repaint. The library MUST NOT
  ship widget-level form bindings (arrows, `Enter`, `Esc`, `Backspace`
  editing, printable-character filters). A shipped widget MAY consume a
  specific key to cycle its own visualisation (for example `g` toggling
  a graph between line and bar) by returning handled from `onKey`.
  *(was FC-34)*
- **CV-50** *(retired)* v2 required one scrollback line recording an
  interactive widget's choice or cancellation. That applied to forms.
  Forms are out of scope (CV-67, CV-69); this ID MUST NOT be reopened as
  a form requirement. Cancel via `Ctrl-C` still restores the terminal
  (CV-49, CR-26).
- **CV-51** Keys MUST be delivered on the render thread, interleaved with
  event draining, so a widget never sees a key and an event concurrently.
- **CV-52** Key reading MUST NOT block rendering: the listener runs on its
  own daemon thread and hands decoded events to the render queue.
- **CV-68** *(new)* Every widget MUST implement one `onKey` contract:
  `boolean onKey(KeyEvent)` returning handled / not-handled, defaulting
  to not-handled, plus `boolean acceptsKeys()` defaulting to false.
  Returning not-handled (or never being offered the event) is how a
  widget ignores keys. Returning handled for a subset of keys is how a
  widget cycles a visualisation. Returning handled for almost every key
  while focused is how a later input widget would work; this library MUST
  NOT special-case that path. The library MUST NOT classify widgets into
  ignore / some-keys / all-keys types.
- **CV-69** *(new)* This library MUST NOT ship menus, confirm dialogs,
  text-input widgets, validation, masks, numeric-only filters, or
  completion. Those MAY appear in a later library that reuses CV-47 and
  CV-68 unchanged. Their absence MUST NOT be filled by stretching a
  canvas widget into a form.

### 5.8 Rendering

- **CV-53** Double buffering is REQUIRED; widgets paint into the back
  buffer only, and the back buffer is cleared each frame. *(was CV-19,
  CV-20)*
- **CV-54** Flush MUST diff back against front and emit only changed
  cells, coalescing runs on a row into one cursor-move plus write,
  suppressing redundant style escapes by tracking the current style.
  Identical consecutive frames MUST be a no-op. *(was CV-21, CV-22, FC-26)*
- **CV-55** A frame MUST be written in one flush, into a reused byte
  buffer. *(was CV-23)*
- **CV-56** The cursor MUST be hidden while the canvas is live and parked
  at a defined position after each flush. Showing the cursor at a caret
  is out of scope of this library (CV-69). *(was CV-25)*
- **CV-57** One platform daemon render thread per process — not virtual —
  started lazily on the first placed widget and stopped when the last is
  removed. Default refresh ~12.5 fps, configurable within 1–30, never
  faster. *(was FC-23, FC-24)*
- **CV-58** An immediate off-cycle redraw MUST be forced on resize, on
  any scrollback write, on widget completion, and on request; multiple
  invalidations MUST coalesce into one frame. *(was FC-25, CV-26, CV-27)*
- **CV-59** The library owns the loop. *(was CV-28)*

### 5.9 Degradation

- **CV-60** When not a TTY, the cursor MUST NOT be moved at all and no
  canvas MUST be painted. *(was FC-38)*
- **CV-61** When not a TTY, every widget MUST degrade to plain lines by a
  per-widget policy: a progress bar emits a periodic line at most every
  5 s or every 10 %, whichever is rarer; a spinner emits one start and one
  result line; a log pane passes lines straight through; static widgets
  print once. *(was FC-39, FC-40, CV-29)*
- **CV-62** *(retired as a prompt rule)* This library ships no prompts
  (CV-69). When not a TTY, `KeyListener` MUST NOT start and a registered
  key handler MUST NOT be invoked. A later forms library MAY reuse this
  ID for "prompt MUST NOT hang"; this library MUST NOT.
- **CV-63** Piping any demo to a file MUST produce zero escape bytes.
  *(was FC-42)*

### 5.10 Widget catalogue

- **CV-64** Static (`Renderable`, printable or blittable): `Table`, `Box`,
  `KeyValueBlock`, `Tree`, `Sparkline`, `Rule`, `PatternHighlighter`,
  `AsciiBitmap` (CV-70). *(was FC-43)*
- **CV-65** Canvas widgets, all event-driven: `Label`, `StatusBar` (N
  segments, independent alignment), `Clock` (self-updating on `Tick`),
  `ProgressBar` (determinate and indeterminate), `Spinner`, `MultiProgress`,
  `StatusList`, `Graph` (line/bar over a value window, fed by
  `GraphValues`/`GraphAppend`), `LogPane` (ring buffer, auto-scroll,
  attachable as log sink per CV-13), `Panel` (border and title, as a
  container for a blitted `Renderable`), `AsciiSprite` (CV-76).
  *(was FC-44, CV-32…CV-37)*
- **CV-66** `MultiProgress` MUST offer four policies for log lines
  attributed to a running task — drop, attributed, buffered-per-task,
  and promote — with promote as the default and buffered capped
  oldest-dropped. *(was FC-45…FC-47)*
- **CV-67** *(retired)* v2 listed interactive form widgets (`SelectMenu`,
  `MultiSelectMenu`, `Confirm`, `TextInput`). They are out of scope of
  this library (CV-69). Canvas widgets from CV-65 MAY implement `onKey`
  to cycle their own visualisation; they MUST NOT become forms.
  *(was FC-48)*

### 5.11 ASCII bitmaps and sprites

A bitmap is the static value; a sprite is the canvas widget that
animates a sequence of them. Both are ordinary members of the two
catalogues above — a bitmap is a `Renderable` (CV-64) and a sprite is an
event-driven widget (CV-65) — so nothing here introduces a second render
path, a second event bus, or a second thread.

- **CV-70** *(new)* `AsciiBitmap` MUST be an immutable `w × h` grid of
  cells — X × Y coloured characters — each cell carrying a glyph and a
  `Style` (CR-1, CR-40). Dimensions MUST be fixed at construction. A
  bitmap is a value, not a mutable framebuffer; an edit produces a new
  bitmap.
- **CV-71** *(new)* Every bitmap cell MUST occupy exactly one display
  column as measured by CR-6. A wide, combining or zero-width grapheme in
  bitmap source MUST be rejected **at construction**, not at paint: the
  grid is positional, and a two-column glyph at `(x, y)` shifts every
  cell to its right.
- **CV-72** *(new)* Cells MUST support per-channel transparency — glyph,
  foreground and background independently absent — so a bitmap drawn over
  other content leaves what is underneath instead of punching a
  rectangular hole. Blitting a cell with an absent glyph MUST leave the
  destination glyph untouched; likewise per colour channel.
- **CV-73** *(new)* `AsciiBitmap` MUST be a `Renderable` (CR-16, CR-18):
  returnable as a `String` in text mode, printable into scrollback, and
  blittable into a Rect with clipping (CV-36). Where there is nothing
  underneath, absent channels MUST resolve to a space and the default
  style.
- **CV-74** *(new)* Bitmaps MUST be **authorable** as text: a block of
  rows plus a palette, constructible from a `String`. A palette entry
  maps one source character to a `Style` and, optionally, to a different
  emitted glyph — which is what lets a pattern be written as digits that
  all paint `#`, and what keeps the source one character per cell
  (CV-71). Rows shorter than the declared width MUST be padded with fully
  transparent cells; rows longer MUST be an error. Text is the authoring
  and review surface only: the **stored** form is the binary `.art` of
  CV-81…CV-86, with `toSource()` as the inverse view (CV-89). No image
  decoding (§7).
- **CV-75** *(new)* Bitmap glyphs MUST pass through the same tier and
  encoding path as every other glyph (CR-7, CR-29…CR-32). An author MAY
  supply per-tier variants of one bitmap (`FULL` / `CP437` / `ASCII`);
  when none is supplied the substitution policy applies, and because
  CR-30 substitution is width-preserving the grid still lines up.
- **CV-76** *(new)* `AsciiSprite` MUST be a canvas widget holding an
  ordered, non-empty list of `AsciiBitmap` frames, each with a duration.
  Frames MAY differ in size; the sprite's bounds are the maximum over its
  frames, with each frame anchored per a declared anchor and padded with
  fully transparent cells (CV-72).
- **CV-77** *(new)* Sprite animation MUST be driven by `Tick` (CV-40) on
  the render thread. A sprite MUST NOT own a thread, sleep, or read the
  clock inside `paint`. Frame selection MUST derive from the elapsed time
  carried by the tick, so playback speed is independent of the refresh
  rate (CV-57) and frames are **skipped**, never queued, when the loop
  runs slower than the frame durations.
- **CV-78** *(new)* Playback MUST be expressible as widget events
  (CV-39): `PlaySprite(loop)`, `PauseSprite`, `SetFrame(index)`,
  `SetSpriteSpeed(factor)`, with the object API (`sprite.play()`,
  `sprite.pause()`, `sprite.frame(i)`) as a thin facade over them
  (CV-42). A sprite that is paused, or has one frame and no running
  palette cycle (CV-88), MUST report itself not dirty (CV-37), so a still
  sprite costs nothing per frame.
- **CV-79** *(new)* Degradation: when not a TTY (CV-60, CV-61) a sprite
  MUST NOT animate; its plain-line policy is to print its first frame
  once, or nothing when configured silent. At colour depth `NONE` a
  bitmap MUST still emit its glyphs. A piped run MUST still produce zero
  escape bytes (CV-63).
- **CV-80** *(new)* Bitmap and sprite output MUST be assertable without a
  terminal and without sleeping: feeding a sequence of `Tick`s with
  synthetic elapsed times MUST select frames deterministically, and the
  result MUST be asserted against the NFR-3 canvas dump.

**Persistence.** Art is authored as text (CV-74) but **stored as bytes**.
The stored unit is the cell, and a cell is two bytes: one selecting the
glyph, one selecting the style. Both tables ride in the file, so a frame
is a flat `2 × w × h` block with no parsing, no escapes and no ambiguity
about where row `y` starts. This is the VGA text-mode / `XBin` shape and
it is chosen for the same reason: the data is a grid of `(char, colour)`
pairs, and any format that is not a grid of `(char, colour)` pairs has to
re-derive one at load.

- **CV-81** *(new)* There MUST be exactly one persistence format, and it
  MUST cover both: a bitmap is a one-frame animation. One reader, one
  writer, one extension (`.art`). A separate "sprite format" is a defect.
- **CV-82** *(new)* An `.art` file MUST be binary, MUST be readable in a
  single forward pass with no seeking (so it streams from a jar entry or
  a pipe, CV-41), MUST NOT be compressed (jar and transport layers
  already do that), and MUST use fixed-width big-endian integers. The
  writer MUST be deterministic: equal values produce byte-identical
  files, which is what makes goldens possible. Layout:

  ```
  magic    "CKART"  u8[5]
  version  u8 major, u8 minor          major bump = refuse to load
  header   u16 width, u16 height, u16 frames,
           u8 anchor, u32 defaultDurationMillis,
           u8 cycleCount, then per cycle:
               u8 length, u8[length] styleSlots, u32 periodMillis
  glyphs   u8 count, then per entry:
               u32 codePoint (FULL), u32 cp437Alt, u32 asciiAlt
  styles   u8 count, then per entry:
               u8 flags (fg kind, bg kind, attrs present),
               fg spec, bg spec, u8 attrBits
  frames   per frame: u32 durationMillis (0 = use default),
               then width*height cells, row-major, each:
               u8 glyphSlot, u8 styleSlot
  ```

- **CV-83** *(new)* A cell MUST be exactly two bytes — glyph slot, style
  slot — with slot `0` in **both** tables meaning transparent. That
  leaves up to **255 glyphs and 255 styles per file** and makes CV-72
  transparency part of the encoding rather than a flag bolted onto it.
  Frames MUST be row-major with no padding, so frame `i` starts at a
  computable offset and a 10 × 10 frame is exactly 200 bytes.
- **CV-84** *(new)* The glyph table MUST store Unicode code points, not
  bytes, with the `CP437` and `ASCII` alternates of CV-75 in the same
  entry. The indirection exists to make CV-71 cheap and total: single-
  column validation runs once per table entry at load — at most 255
  checks — instead of `w × h` per frame, and a file whose table is valid
  cannot contain a mis-measured cell. Tier alternates MUST change glyphs
  only, never geometry.
- **CV-85** *(new)* The style table MUST store full `Style` values in the
  CR-1 / CR-40 model: foreground and background each absent, named,
  `index:n` or 24-bit rgb, plus the attribute flags. A style byte is an
  **index into that table**, not a packed VGA attribute — the format MUST
  NOT be limited to 16 colours, and per-channel absence (CV-72) MUST be
  expressible in an entry.
- **CV-86** *(new)* Reading MUST accept a classpath resource, a `Path`
  and an `InputStream`, so a jar can ship an art set and a child process
  can stream one. Every limit MUST be enforced **before** allocating:
  documented caps on width, height, frame count and total file size; a
  declared size that exceeds them, a truncated frame, a slot outside its
  table, a bad magic or a newer major version MUST throw with the byte
  offset. There are no external references, no includes and nothing
  executable in the format. `.art` files MAY be user-supplied, so a
  hostile one MUST fail fast rather than allocate what its header claims.
- **CV-89** *(new)* Because the stored form is not reviewable, the text
  form MUST stay first-class as its **view**: `toSource()` MUST render
  any bitmap or sprite as the CV-74 rows-plus-palette text, and
  `parse(text) → write → read` MUST produce an equal value. The text form
  is a projection of the value, never a second on-disk format, and
  `Probe` MUST be able to dump an `.art` file to it so a broken file can
  be read without a hex editor.

**Filled grids and colour cycling.** The worked case is a 10 × 10 field
of `#` with colour stripes travelling diagonally. Spelling that as ten
hand-written frames of a hundred cells would be a failure of the format,
not a use of it. Two mechanisms keep it one-liner-sized, and neither is
specific to stripes.

- **CV-87** *(new)* `AsciiBitmap` MUST be constructible from a function
  of position: `generate(w, h, (x, y) -> Cell)`. The function MUST be
  evaluated exactly once per cell **at construction** and MUST NOT be
  retained, so the result stays an immutable value (CV-70) and paint
  stays a pure blit (CV-77). A per-paint cell callback is the thing this
  requirement exists to prevent.
- **CV-88** *(new)* A sprite MUST support **palette cycling**: an
  ordered group of style slots (CV-85) rotated by one position every
  declared period, so an N-colour pattern animates from **one** frame
  instead of N frames. Groups and periods are part of the file header
  (CV-82). Cycling MUST be driven by the same `Tick` elapsed-time
  accumulator as frame advance (CV-77), with its own period, and MUST be
  settable as an event (`SetCycleOffset(int)`) with `sprite.cycle(n)` as
  its facade (CV-42). Because a cell stores a *slot* and the slot carries
  the style (CV-83), the grid is written once and only the colours move.
  A sprite with an active cycle is dirty on each rotation and only then
  (CV-37, CV-78).
  A sprite with an active cycle is dirty on each rotation and only then
  (CV-37, CV-78).

### 5.12 Usage sketches (canvas mode)

Illustrative of CV-10, CV-13, CV-24, CV-38, CV-40, CV-42, CV-47, CV-65,
CV-70, CV-78. `place` / docking / `println` ship in M3; `send` and the
object-API facade ship in M4. Surrounding glue is not an API freeze.

The §1.2 picture — progress docked top-left, clock top-right, status
bottom, last widget fills remaining, log lines above the canvas:

```java
try (FancyConsole console = new FancyConsole()) {
    WidgetHandle bar = console.place(new ProgressBar(), Dock.TOP);
    WidgetHandle clock = console.place(new Clock(), Dock.RIGHT);
    WidgetHandle status = console.place(new StatusBar(4), Dock.BOTTOM);
    WidgetHandle body = console.place(new Panel("main"), Dock.FILL);

    console.println("scanning input/");
    console.println("found 1 204 files");

    bar.send(new ProgressUpdate(bar.id(), 61, 100, "encode.mp4"));
    status.send(new SetStatus(status.id(), 0, StyledText.of("connected")));
    // Clock paints itself from Tick; the application does not drive it.
}
```

Object API is the same events (CV-42):

```java
bar.step();                                 // ProgressUpdate
bar.indeterminate("waiting");               // Indeterminate
bar.complete("done");                       // Completed
log.append(StyledText.of("hello"));         // LogMessage
graph.append(0.42);                         // GraphAppend
label.setText("ready");                     // SetText
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
is `onKey`, not a form (CV-69):

```java
Graph graph = new Graph();                  // onKey('g') → line/bar, handled
console.place(graph, Dock.FILL);
graph.send(new GraphValues(graph.id(), 0.1, 0.4, 0.3, 0.8));
console.onKey(e -> {
    if (e.key() == Key.Q) { console.close(); return true; }
    return false;                           // unhandled → built-ins
});
```

`Panel` blits a static `Renderable` into its content area (CV-36):

```java
Panel panel = new Panel("files");
console.place(panel, Dock.FILL);
panel.blit(Table.of(headers, rows));
```

An `AsciiBitmap` is X × Y coloured characters authored as text plus a
palette (CV-70, CV-74); an `AsciiSprite` animates a list of them from
`Tick` (CV-76, CV-77):

```java
AsciiBitmap logo = AsciiBitmap.parse("""
        .###.
        #.o.#
        .###.
        """,
        Palette.of('#', Style.fg(Color.BRIGHT_CYAN),
                   'o', Style.fg(Color.RED).bold(),
                   '.', Palette.TRANSPARENT));

console.println(logo.toString());           // text mode / scrollback
panel.blit(logo);                           // or blitted into a Rect

AsciiSprite spinner = AsciiSprite.of(Duration.ofMillis(120), f0, f1, f2, f3);
console.place(spinner, Rect.of(2, 1, 5, 3));
spinner.play();                             // PlaySprite(loop = true)
spinner.pause();                            // PauseSprite → not dirty
```

Authored as text, stored as bytes: the same `.art` file loads as a bitmap
or as a sprite, from a jar or from disk, and round-trips (CV-81…CV-86,
CV-89):

```java
AsciiBitmap.write(Path.of("art/logo.art"), logo);   // 2 bytes per cell + tables
AsciiBitmap logo2    = AsciiBitmap.load("/art/logo.art");        // classpath
AsciiSprite spinner2 = AsciiSprite.load(Path.of("art/spinner.art"));
System.out.println(logo2.toSource());               // text view of the bytes
```

A 10 × 10 field of `#` with colour stripes travelling diagonally is **one
frame** plus one cycle group (CV-74, CV-88): the digits are palette
characters that all emit `#`, and rotating the group of style slots moves
the stripes. Stored, that is 200 cell bytes plus a 1-entry glyph table
and a 4-entry style table — not ten frames:

```java
Palette stripes = Palette.builder()
        .entry('1', '#', Style.fg(Color.RED))
        .entry('2', '#', Style.fg(Color.YELLOW))
        .entry('3', '#', Style.fg(Color.GREEN))
        .entry('4', '#', Style.fg(Color.CYAN))
        .cycle(Duration.ofMillis(80), '1', '2', '3', '4')
        .build();

AsciiSprite band = AsciiSprite.of(AsciiBitmap.parse("""
        1234123412
        2341234123
        3412341234
        4123412341
        1234123412
        2341234123
        3412341234
        4123412341
        1234123412
        2341234123
        """, stripes));

console.place(band, Rect.of(4, 2, 10, 10));
band.play();
AsciiSprite.write(Path.of("art/stripes.art"), band);
```

The same field built in code is `generate` plus a cell function (CV-87);
no grid is typed out by hand either way:

```java
Style[] ramp = { Style.fg(Color.RED),   Style.fg(Color.YELLOW),
                 Style.fg(Color.GREEN), Style.fg(Color.CYAN) };

AsciiBitmap field = AsciiBitmap.generate(10, 10,
        (x, y) -> Cell.of('#', ramp[(x + y) % ramp.length]));
```

`MultiProgress` (CV-66), promote default:

```java
MultiProgress mp = new MultiProgress();
console.place(mp, Dock.TOP);
WidgetHandle encode = mp.task("encode.mp4");
encode.send(new ProgressUpdate(encode.id(), 61, 100, "encode.mp4"));
mp.onTaskLog(encode.id(), "frame 1200");    // promote policy: line goes up
```

Application keys after the focused widget declines (CV-47). Built-ins
are only `Ctrl-C` and `Ctrl-L`. No Tab-cycle.

---

## 6. Non-functional requirements (`NFR`)

- **NFR-1** Every public type MUST have Javadoc containing a usage snippet.
- **NFR-2** Tests MUST be written before implementation for anything in
  the core and render layers.
- **NFR-3** The canvas MUST be dumpable to a plain string so layouts can
  be asserted in unit tests with no terminal involved, **and** a virtual
  terminal emulator MUST consume the library's own output, interpret
  cursor and erase sequences, and maintain a cell grid, so tests assert
  *what the user sees* including the scrollback/canvas boundary. Both
  MUST exist before any canvas milestone is called done.
- **NFR-4** A frame at 200×50 MUST lay out, paint, diff and flush in well
  under 16 ms.
- **NFR-5** Steady-state **layout, paint, diff and flush** MUST NOT
  allocate per frame. The event queue, `WidgetEvent` records, and key
  decoding MAY allocate; this requirement does not apply to them.
- **NFR-6** No flicker, and no scrolling of the terminal other than by
  `println`, during normal operation of canvas mode.
- **NFR-7** The concurrency model MUST be explicit and documented: text
  mode is stateless; canvas mode is one render thread fed by one queue,
  with the key listener as a producer. Widgets updated from other threads
  MUST NOT be able to tear a frame.
- **NFR-8** Runtime dependencies MUST be limited to JLin Source and
  bytecode target **JDK 21**. The build is Maven (`mvn verify`).
- **NFR-9b** The artefact is a real JPMS module `dev.consolekit` with
  `module-info.java`. `dev.consolekit.internal` MUST NOT be exported;
  NFR-10 source-scan tests back that, they do not replace it.e. No jansi, ever.
  No ncurses.
- **NFR-9** The build MUST NOT require preview features.
- **NFR-10** Each structural invariant (CR-21 through CR-23, CV-22) MUST
  be enforced by a test — a source scan or a behavioural test — not by
  convention.
- **NFR-11** No rendered line MAY exceed the available width as measured
  per CR-6, in either mode, with any of the CR-10 fixture strings.
- **NFR-12** `\n` line endings. Tests are JUnit 5. Golden files live
  under `src/test/resources/golden/`le state MUST be confined to one documented
  runtime holder (`ConsoleRuntime`).
- **NFR-13** Golden-file tests MUST cover at least truecolor/FULL,
  ANSI256, ANSI16/CP437, and NONE/redirected, read with explicit UTF-8 and
  normalised line endings.

### 6.1 Platform matrix — *unchanged from v2, sustained in full*

- **NFR-14** Both modes MUST render correctly, and leave the shell usable
  afterwards, on: Linux (gnome-terminal, konsole, xterm, alacritty),
  tmux/screen, over SSH; macOS Terminal.app (256 colour max), iTerm2, VS
  Code's integrated terminal; Windows Terminal, PowerShell 5.1 and 7 in
  conhost, `cmd.exe`; any of those redirected to a file or pipe; and CI.
- **NFR-15** `cmd.exe` on a Czech Windows install is the deliberate worst
  case. If the demos look right there they look right everywhere.
- **NFR-16** Windows console and ConPTY support is required, not optional.
- **NFR-17** Results per target MUST be recorded in
  `SUPPORTED-TERMINALS.md`, and a row MUST NOT be considered passing until
  a human has actually run the probe or a demo on that platform.
- **NFR-18** A milestone is not done until `mvn verify` is green *and* the
  relevant demo has been run and visually checked by a human on a real
  terminal.
- **NFR-19** *(new)* **Start-gate (not a milestone):** the conhost rows
  of NFR-14 MUST be verified for canvas mode **before M6 starts**. This
  is not a done-criterion of M6 and is not deferred to M7. conhost
  scrolling under `println`-above-region is the risk; macOS rows wait
  for M7. Same rule as architecture §9 step 7.

---

## 7. Out of scope

- Mouse support.
- Image or sixel rendering, and decoding an image file into characters.
  An `AsciiBitmap` is a grid of characters an author writes, not a
  picture the library converts (CV-70, CV-74).
- Sprite collision detection, scene graphs, tweening and game loops. A
  sprite is a widget that advances frames on `Tick` (CV-77); moving it
  is free placement (CV-25).
- Command-line argument or option parsing.
- Constraint-solver or flexbox-style content-reflowing layout.
- The alternate screen buffer (§9).
- Nested widgets (CV-32), scrollbars, dialogs, modal windows.
- Forms and smart input: menus, confirm, text-input widgets, validation,
  masks, numeric-only filters, completion, Tab-cycle, caret (CV-67,
  CV-69). The `onKey` hook and CV-47 routing stay; the widgets do not.
- Multiple canvases per process.

---

## 8. Milestones

Renumbered for the two-mode plan. Milestones already delivered under v2
are listed with what survives.

| # | Deliverable | State |
|---|---|---|
| M1 | Core model, capabilities, `Text` | done (v2 M0–M1); `Text` complete |
| M1b | `Styler`, `Snippets`, `Color` named constants (TX-10…TX-15, CR-40) | not started |
| M2 | Static `Renderable` catalogue | done (v2 M2) |
| M3 | Canvas engine: buffers, layout, blit, flush diff, canvas-above-scrollback routing, stream capture, restore paths, virtual terminal, `place`/`update`/`focus`/`remove` (CV-1…CV-38 except `handle.send`, CV-53…CV-63). CV-9 and CV-13 are new in this range. | partially reusable from v2 M3 — see §11 |
| M4 | Event core (`WidgetId`, `WidgetEvent`, `handle.send`) + event-driven widget catalogue (CV-38 `send`, CV-39…CV-44, CV-65, CV-66) | not started |
| M4b | `AsciiBitmap` + `AsciiSprite` (CV-70…CV-89). Needs M4's `Tick` and event queue; the bitmap half (CV-70…CV-75, CV-87) and the `.art` codec (CV-81…CV-86, CV-89) only need M3. | not started |
| M5 | Content-side encoding robustness (CR-29…CR-36, CR-39) | not started |
| M6 | `KeyListener`, single-consumer forwarding, focus slot (CV-45…CV-52, CV-68, CV-69). No form widgets. Do not start until NFR-19 has passed. | not started |
| M7 | Remaining NFR-14 platform rows (`SUPPORTED-TERMINALS.md`), including macOS. Not a substitute for the NFR-19 conhost gate. | 1 of 10 |

M5 is deliberately before M6: keyboard decoding (CR-37) depends on the
charset resolution M5 introduces.

**NFR-19 start-gate (not a milestone):** the conhost rows of NFR-14 MUST
be verified for canvas mode **before M6 starts**. This is not a
done-criterion of M6 and is not deferred to M7. conhost scrolling under
`println`-above-region is the risk; macOS rows wait for M7. Same rule
as architecture §9 step 7.

---

## 9. Decisions and open questions

Decided in this revision, recorded so they aren't reopened by accident:

- **Two modes, not three tiers.** See §1.
- **No alternate screen buffer.** The canvas lives in the normal screen
  buffer at the bottom of the viewport. This keeps one restore path
  (the same one v2 M3 already implements and tests), keeps `println`
  meaningful, and keeps the last frame visible in scrollback after exit —
  which is what a build tool or job runner wants. The cost is that a
  full-height canvas makes `println` lines invisible unless a `LogPane`
  sink is attached (CV-13). If a genuine full-screen TUI use-case appears,
  it is a new mode, not a flag on this one.
- **Application-level key delivery: yes**, with the single-consumer
  routing rule in CV-47, the `onKey` contract in CV-68, and the raw-mode
  lifetime in CV-45. A key is offered until handled; it is never
  broadcast. Widgets may ignore keys or consume a few. Forms are a later
  library (CV-69), not a second bus.
- **Focus is a slot**, not a form navigator (CV-48). No Tab-cycle, caret,
  or selection chrome in this library.
- **Content-derived sizing: yes**, optional per widget (CV-26).
- **One colour type**, `Color` (CR-40). `AsciiColor` is not a class.
- **One JPMS module**, `dev.consolekit`, with `module-info.java`.
  `dev.consolekit.internal` is not exported. No `consolekit-canvas`
  split; the canvas depends on the same core and there is no longer a
  second lifecycle to isolate.
- **The event model is shared** by all widgets; there is no separate
  "runtime widget" vs "canvas widget" distinction any more.
- **JDK 21, Maven, no preview.** `IO.println` is not used.
- **Terminal size for layout is per-frame** (CV-27), on `RenderContext`.
  `Capabilities.size()` is probe-time only (CR-12).
- **Text-mode width** is `COLUMNS` then 80. Never JLine (TX-2, TX-15).
- **Canvas height** (CV-6) is config, else sum of definite docked
  vertical sizes, else full viewport. Fill/percent do not feed back.
- **`logTo` is additional**, not instead-of (CV-13). Stream emission
  always happens.
- **`WidgetId`** is minted at `place`, process-unique, never reused.
  It is a field on every `WidgetEvent`. `send` is M4.
- **NFR-5** applies to layout/paint/diff/flush, not to the event queue.
- **Percentage leftover** goes to the last percentage-or-fill widget
  on that axis (CV-26).
- **Ctrl-C `Cancelled`** is not a render-path throw (CR-24).
- **Obsolete pin types stay compiling** until `place` lands; then they
  are deleted. Do not break the branch up front.
- **Tests:** JUnit 5; goldens at `src/test/resources/golden/`.

Still open:

1. **Event queue overflow policy detail (CV-43).** Coalesce-then-drop is
   specified; the bound and whether `LogMessage` is ever droppable
   (probably not — drop `Tick`/`GraphAppend` first) need deciding when M4
   starts.
2. **Nested widgets (CV-32).** Flat docking plus `Panestrip engine covers most; **CV-9 (lazy start) and CV-13 (`logTo`) are new** and not implemented
   §1.2. Add composition when a widget actually needs it.
3. **Should `Snippets.print…` exist at all** (TX-14), given that every
   other text-mode method returns a `String`? Kept because the sketch
   asked for it; it is one line of sugar and easy to remove.

---

## 10. Status

| Area | Requirements | State |
|---|---|---|
| Core model | CR-1 … CR-15 | implemented |
| One colour type | CR-40 | `Color` exists; named constants missing |
| Encoding robustness | CR-28 … CR-39 | glyph side done, content side not started |
| Shared render contract | CR-16 … CR-20 | implemented |
| Terminal safety | CR-21 … CR-27 | implemented for current code |
| Text mode, static | TX-1 … TX-9 | implemented |
| Text mode, `Styler` / `Snippets` | TX-10 … TX-15 | **not started** |
| Canvas ownership, println, capture | CV-1 … CV-22 | implemented for the strip engine; needs canvas retarget |
| Canvas layout, widget model | CV-23 … CV-38 | **not started** |
| Widget events | CV-39 … CV-44 | **not started** |
| KeyListener | CV-45 … CV-52, CV-68, CV-69 | **not started** |
| Rendering | CV-53 … CV-59 | strip-based version implemented; cell diff not started |
| Degradation | CV-60 … CV-63 | implemented |
| Widget catalogue | CV-64 … CV-67 | static done; canvas widgets not started; forms retired (CV-67, CV-69) |
| Bitmaps and sprites | CV-70 … CV-89 | **not started** |
| Platform verification | NFR-14 … NFR-19 | 1 of 10 rows |

---

## 11. Migration from v2

### 11.1 ID map

| v2 | v3 | Note |
|---|---|---|
| CR-1 … CR-39 | same | unchanged; CR-12 gains charsets, CR-18 and CR-35 and CR-39 extended for the canvas |
| — | CR-40 | new: one `Color` type |
| TX-1 … TX-7 | same | TX-2 and TX-4 clarified for snippets |
| — | TX-8 … TX-15 | new: `Text` inventory, `Styler`, `Snippets` |
| FC-1, FC-2, FC-3, FC-5, FC-4 | CV-1, CV-2, CV-3, CV-4, CV-5 | ownership |
| FC-6, FC-7 | *dropped* | strip stacking replaced by docking (CV-24) |
| FC-8, FC-9 | CV-10, CV-11 | "immediate" lifetime is now just `println`/`print(Renderable)` |
| FC-10, FC-11, FC-12, FC-13 | CV-31, CV-37, CV-38, CV-48 | FC-13 "one interactive" becomes "one focused slot", not a form |
| FC-14 … FC-22 | CV-12, CV-14 … CV-21 | unchanged wording |
| FC-23 … FC-27 | CV-57, CV-58, CV-54, CV-27 | animation |
| FC-28 | CV-22 + CV-39 | split: "no escapes" and "events" |
| FC-29, FC-30, FC-31 | CV-41, CV-42, CV-43 | events |
| FC-32 … FC-36 | CV-45 … CV-49, CV-51, CV-52 | keys; FC-37 decided yes as forwarding, not forms |
| FC-35 | CV-50 retired | form choice/cancel line; forms out of scope |
| — | CV-68, CV-69 | new: one `onKey` contract; no forms in this library |
| FC-38 … FC-42 | CV-60 … CV-63 | degradation; CV-62 retired as a prompt rule |
| FC-43 … FC-47 | CV-64 … CV-66 | catalogue |
| FC-48 | CV-67 retired | form widgets out of scope (CV-69) |
| — | CV-70 … CV-89 | new: `AsciiBitmap`, `AsciiSprite`, binary `.art` format, palette cycling |
| CV-1 | CV-6, CV-7 | height resolution changed |
| CV-2 | CV-6 | alternate screen dropped |
| CV-3 … CV-6 | CV-8, CV-27, CV-27, CV-23 | |
| CV-7 … CV-14 | CV-24 … CV-32 | CV-9 content sizing now in |
| CV-15 … CV-18 | CV-33 … CV-37 | |
| CV-19 … CV-29 | CV-53 … CV-59, CV-36, CV-61 | |
| CV-30, CV-31 | *superseded* by CV-45 … CV-52 | keys are in scope now |
| CV-32 … CV-37 | CV-65 | |
| NFR-1 … NFR-18 | same | NFR-3, NFR-6, NFR-7, NFR-10, NFR-12 reworded |
| — | NFR-19 | new: conhost gate |

### 11.2 What the v2 code means under v3

| Code | Verdict |
|---|---|
| `core.*` (`Color`, `Style`, `Attr`, `Capabilities`, `GlyphTier`, `StyledText`, `Theme`, `RenderContext`, `ConsoleOptions`) | **keep** — add `Color` named constants (CR-40) |
| `internal.Ansi`, `internal.Glyphs`, `internal.TextWidth`, `internal.TerminalPort` | **keep** — these are the CR-21/22/23 single-class homes |
| `Text` | **keep** — complete for TX-8; `Styler`/`Snippets` are new siblings |
| `render.Renderable`, `widget.*` (static catalogue) | **keep** — now printed or blitted (CV-36) |
| `render.AsciiWidget`, `render.PinHandle`, `render.PinContext` | **obsolete** — replaced by `canvas.Widget` and `canvas.WidgetHandle` |
| `internal.PinStack` | **obsolete** — strip stacking is replaced by layout (CV-24/25) |
| `internal.LiveRegion` | **reinterpret** — it already owns "bottom N rows repainted in place above scrollback"; it becomes the canvas's flush target, but must be changed from line-diffing to cell-diffing (CV-54) |
| `internal.VirtualTerminal` | **reinterpret** — keep; extend from line frames to a cell grid so NFR-3 asserts what the user sees after cell flush |
| `internal.ScrollbackWriter`, stream capture | **keep** — CV-12, CV-14…CV-21 are unchanged |
| `internal.AnimationLoop` | **reinterpret** — same thread and fps rules (CV-57), but its tick must drain the event queue (CV-39) and the key queue (CV-51) before painting |
| `FancyConsole.pin(AsciiWidget)` | **obsolete** — replaced by `place(Widget, Placement)` |
| `FancyConsole` println family, routing, multi-instance sharing, close semantics | **keep** |
| `ConsoleRuntime.Mode2Session` | **keep** the holder; its contents change |
| `Probe`, `SUPPORTED-TERMINALS.md` | **keep** |
| Demos `PinnedClock`, `Showcase`, `ReportPage` | `Showcase`/`ReportPage` keep; `PinnedClock` rewrite as a docked `Clock` |
