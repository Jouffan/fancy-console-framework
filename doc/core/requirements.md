# ConsoleKit Core — Requirements (`CR`, `NFR`)

Part 1 of 4. See [the map](../requirements.md) for how the parts fit
together.

Core is the **commons**: the model every other part is written against.
It renders nothing on its own and owns no terminal state machine.

| Part | Depends on | Prefix |
|---|---|---|
| **core** (this document) | — | `CR`, `NFR` |
| [text](../text/requirements.md) | core | `TX` |
| [bitmap](../bitmap/requirements.md) | core | `BM`, `AF` |
| [canvas](../canvas/requirements.md) | core, bitmap | `CV` |

This document is written **for** those three consumers. Core MUST NOT
*depend* on them (CR-43): no import, no package reference, no compile-time
edge. §4 is allowed knowledge of what they need. Consumer IDs MUST NOT
appear in a core MUST as if they were core requirements.

`MUST` / `SHOULD` / `MAY` are RFC 2119.

---

## 1. Terminology

**Style** — foreground colour, background colour, attribute flags. A
value.
**Cell** — one character position: glyph plus style, each channel
optionally absent (CR-41).
**StyledText** — a sequence of `(text, style)` spans making up one line.
**Renderable** — anything that produces `List<StyledText>` from a
`RenderContext`.
**Glyph tier** — `FULL` / `CP437` / `ASCII`, the character repertoire the
current terminal can actually encode.
**Capabilities** — the once-per-process resolved environment: colour
depth, glyph tier, TTY-ness, charsets, supported attributes.
**Grapheme** — a user-perceived character; the unit of measurement,
truncation and wrapping.

---

## 2. Requirements

### 2.1 Colour and style

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
  info, muted, highlight, border, selection) to styles, so nothing
  downstream hard-codes colours.
- **CR-40** There is exactly **one** colour type across the library:
  `dev.consolekit.core.Color`, with `Color.named(Named)`,
  `Color.index(0..255)`, `Color.rgb(r,g,b)`, and the sixteen named
  constants exposed as static fields (`Color.RED`, `Color.BRIGHT_CYAN`,
  …) so that `line(Color.RED)` reads naturally. `AsciiColor` MUST NOT be
  introduced as a second class.

### 2.2 Characters and width

- **CR-6** Display width MUST be computed correctly for wide (CJK, emoji),
  zero-width and combining characters, and MUST ignore embedded escape
  sequences. `String.length()` in layout code is a defect.
- **CR-7** Three glyph tiers MUST be supported — `FULL` (Unicode box
  drawing, blocks, marks), `CP437`, and pure `ASCII` — selected by probing
  the console charset's encoder per glyph, not by guessing from `TERM`.
- **CR-8** Glyph tier MUST be overridable by configuration and by
  environment variable.
- **CR-9** Control characters (`\t`, `\r`, `\n`, `ESC`) appearing in
  content MUST be sanitised or expanded, never passed through raw.
- **CR-10** `příliš žluťoučký kůň` is the standard fixture string; every
  alignment and width test MUST include it alongside CJK and emoji cases.

### 2.3 Encoding robustness

CR-6 through CR-10 govern the library's *own* glyphs. This section
governs *caller-supplied content*, which is a different problem: the
library chooses its box-drawing characters and can downgrade them, but it
does not choose the strings an application prints.

The failure this section exists to prevent: something measured as 20
columns wide whose content the console encoder turns into a different
number of characters — on the platforms least likely to produce a bug
report. On a cell grid this failure is worse, not better: one
mis-measured string shifts every cell to its right.

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
  another. Substitution MUST happen **before** text is written into
  cells, never at flush.
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
  combining mark, or a severed ZWJ emoji sequence. A cell grid MUST store
  a wide grapheme as one cell of width 2 plus a continuation marker,
  never split it across two independently-styled cells.
- **CR-36** Malformed input — unpaired surrogates, invalid byte sequences,
  a BOM mid-stream — MUST NOT throw and MUST NOT abort a render. Replace
  and continue, per CR-24.
- **CR-37** Keyboard input MUST be decoded using the resolved input
  charset, so a key such as a letter with a diacritic is one key event on
  a non-UTF-8 console.
- **CR-38** `Probe` MUST report the resolved output charset, the raw
  `stdout.encoding` / `native.encoding` / `Console.charset()` values it was
  derived from, and on Windows the active code page — since when these
  disagree, that disagreement is the bug.
- **CR-39** *(moved)* The CR-10 fixture on a non-UTF-8 console, printed
  and blitted, asserted through the virtual terminal, lives in the canvas
  part. Core keeps the fixture string (CR-10) and the encoding rules; it
  does not own a canvas.

### 2.4 Capability detection

- **CR-11** Capabilities MUST be resolved once per process from, in
  precedence order: explicit configuration, `NO_COLOR`,
  `FORCE_COLOR`/`CLICOLOR_FORCE`, TTY-ness, `TERM`, `TERM_PROGRAM`,
  `WT_SESSION`, `COLORTERM`.
- **CR-12** Capabilities MUST carry colour depth, glyph tier, TTY-ness,
  supported attributes, a **probe-time** terminal size (diagnostic only
  — MUST NOT be used for layout; see CR-17), output and input
  charset, and a human-readable reason when anything was degraded.
- **CR-13** Non-TTY output (pipe, file, CI) MUST be detected.
- **CR-14** Explicit configuration MUST be settable at most once, before
  any output; a later attempt MUST fail loudly rather than silently
  produce inconsistent output.
- **CR-15** A standalone diagnostic (`Probe`) MUST print the full resolved
  environment plus a colour and glyph test card, in pure ASCII so it
  survives any terminal. It MUST report only what core resolved. Dumping
  an `.art` file is a *bitmap* diagnostic (AF-7) and MUST NOT be reached
  from here, because core cannot depend on the bitmap part (CR-43).

### 2.5 The shared render contract

- **CR-16** `Renderable` MUST be a pure function from a render context to
  a list of styled lines: same context, same state, same output. No
  cursor access, no global state, no I/O.
- **CR-17** A `RenderContext` MUST carry the resolved capabilities, the
  theme, and the available width. It MAY also carry an optional size that
  the *caller* fills when it has a current viewport. Renderables MUST be
  given their context and MUST NOT reach for process-wide state: reading
  `Capabilities.size()` for layout after a resize is a defect, because the
  context is the only size that is current. Canvas fills the optional size
  per frame (see CV-27).
- **CR-18** The static widget catalogue (`Table`, `Box`, `KeyValueBlock`,
  `Tree`, `Sparkline`, `Rule`, `PatternHighlighter`) MUST be usable by
  every part that can consume a `Renderable`: as a `String`, printed into
  scrollback, or blitted into a Rect. The catalogue MUST NOT know which.
- **CR-19** Text placed into a bounded area MUST support left/centre/right
  alignment and a truncation policy of clip or ellipsis, with the ellipsis
  glyph chosen by tier.
- **CR-20** Optional word wrapping MUST be available for multi-line
  content areas.

### 2.6 Terminal safety

- **CR-21** Only one class MAY emit escape sequences.
- **CR-22** Only one class MAY touch the underlying terminal library
  (JLine).
- **CR-23** Only one class MAY contain non-ASCII character literals.
- **CR-24** No render path MAY throw. A failure in rendering MUST be
  caught, MUST disable further animation, MUST warn once, and MUST fall
  back to plain output. A broken renderable MUST NOT crash the caller's
  job. Parsing and loading are **not** render paths.
- **CR-25** The library MUST NOT log through SLF4J or any logging
  framework.
- **CR-26** Whatever a part changes about terminal state MUST be restored
  on normal exit, on exception, on `SIGINT`, and on `SIGTERM`. Restore
  MUST be idempotent, because more than one path can fire.
- **CR-27** Leaving the user's shell with a hidden cursor, an active SGR
  state, raw mode, or a replaced `System.out` is the library's most severe
  defect class. Each restore path MUST have a test.

### 2.7 Layering and interchange — *new in v4*

This is the section that exists because there are now four parts. It is
the whole of what core owes them, and the whole of what they may assume
about each other.

- **CR-41** `Cell` MUST be a **core** type: one glyph plus one `Style`,
  with the glyph, the foreground and the background each independently
  **absent**. Absence MUST mean leave what is underneath. Bitmap and
  canvas both use this type; neither MAY define its own. A stored bitmap
  is slots that *resolve to* cells; a canvas back buffer *is* cells. That
  is a shared type, not a claim that paint is a copy of the same array.
- **CR-42** `StyledText` and `Renderable` are the **print** interchange.
  A part that can produce `List<StyledText>` is thereby printable as a
  `String`, printable into scrollback, and blittable as opaque catalogue
  content, without knowing that any of those consumers exist. Overlay
  paint is not this contract: it composites core `Cell`s. That is allowed
  because both sides already depend on core — it is not a new bridge.
  New *print* plumbing is a design defect: add a `Renderable`.
- **CR-43** The dependency graph MUST stay acyclic and MUST be exactly:
  core ← text, core ← bitmap, core ← canvas, bitmap ← canvas. Core MUST
  NOT reference text, bitmap or canvas. Text and bitmap MUST NOT
  reference canvas. Nothing MUST reference text except an application.
  A test MUST enforce this by package scan (NFR-10).
- **CR-44** Core MUST own the path from a `Renderable` to characters on
  an ordinary stream: rendering a `Renderable` to a `String` with escapes
  applied per the resolved capabilities, using the same single escape
  emitter as everything else (CR-21). Other parts may facade this path;
  they MUST NOT own a second escape emitter.

---

## 3. Non-functional requirements (`NFR`)

Whole library. Each part inherits them; none of them is a part's private
business.

- **NFR-1** Every public type MUST have Javadoc containing a usage snippet.
- **NFR-2** Tests MUST be written before implementation for anything in
  core.
- **NFR-3, NFR-4, NFR-5, NFR-6, NFR-19** live in the canvas part. IDs
  are not reused here.
- **NFR-7** The concurrency model MUST be explicit and documented: text
  mode is stateless; bitmap values are immutable and thread-confined by
  being immutable; canvas mode is one render thread fed by one queue,
  with the key listener as a producer. Widgets updated from other threads
  MUST NOT be able to tear a frame.
- **NFR-8** Runtime dependencies MUST be limited to JLine: exactly
  `jline-terminal` plus the **JNI** provider `jline-terminal-jni`. The
  FFM provider MUST NOT be used — it needs JDK 22+ and this library
  targets JDK 21. The JNA provider MUST NOT be used — it was removed in
  JLine 4.0.0. No jansi, ever. No ncurses. Source and bytecode target
  **JDK 21**. The build is
  Maven (`mvn verify`).
- **NFR-9** The build MUST NOT require preview features.
- **NFR-9b** Each part MUST ship as its own Maven artefact and its own
  JPMS module with a `module-info.java`: `consolekit-core` /
  `dev.consolekit.core`, `consolekit-text` / `dev.consolekit.text`,
  `consolekit-bitmap` / `dev.consolekit.bitmap`, `consolekit-canvas` /
  `dev.consolekit.canvas`. Each module MUST keep one non-exported
  `*.internal` package. There MUST NOT be a package shared by two
  artefacts — split packages are illegal on the module path and would
  also hide CR-43 violations.
- **NFR-10** Structural invariants MUST be enforced, not assumed. CR-43
  is enforced by the reactor and `module-info.java`: an illegal edge
  MUST fail to compile. The invariants the compiler cannot express —
  CR-21, CR-22, CR-23, CV-22 — MUST each be enforced by a source-scan or
  behavioural test. The CR-23 scan MUST be scoped to `src/main`, since
  the CR-10 fixture strings are deliberately non-ASCII test data.
- **NFR-11** No rendered line MAY exceed the available width as measured
  per CR-6, in any part, with any of the CR-10 fixture strings.
- **NFR-12** `\n` line endings. Tests are JUnit 5. Golden files live
  under `src/test/resources/golden/`. Process-wide mutable state MUST be
  confined to one documented runtime holder (`ConsoleRuntime`) in core.
  That holder MUST expose an **opaque slot** rather than a typed field:
  it MUST NOT name a type from any other part, or core would depend on
  a consumer and break CR-43.
- **NFR-13** Golden-file tests MUST cover at least truecolor/FULL,
  ANSI256, ANSI16/CP437, and NONE/redirected, read with explicit UTF-8 and
  normalised line endings.

### 3.1 Platform matrix

- **NFR-14** Every part MUST render correctly, and leave the shell usable
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

---

## 4. What the other parts require of core

Core is designed against this table and nothing else. If a part needs
something not listed here, the table changes first — in this document,
with an ID — and the part follows.

| Consumer | What it needs from core | Pinned by |
|---|---|---|
| text | `Color`, `Style`, `Theme`, `Capabilities`, escape emission, the "return the string unchanged when escapes can't render" rule | CR-1…CR-5, CR-40, CR-44 |
| bitmap | `Cell` with absent channels, `Style`, glyph tier + substitution, grapheme width, `Renderable` + `StyledText` | CR-41, CR-6…CR-10, CR-29…CR-31, CR-42 |
| bitmap | a print path that needs no terminal ownership | CR-44 |
| canvas | `Cell`, `RenderContext` with optional caller-filled size, `Renderable` to print/blit as catalogue content, capability degradation, restore-path rules | CR-41, CR-17, CR-42, CR-26, CR-27 |
| canvas | the bitmap model, to wrap as a widget | BM-8, BM-9, BM-10, BM-13 |

Two consequences worth stating outright, because they are the reason the
split is cheap:

1. **A bitmap prints and paints through two doors, both using core
   `Cell`.** Print is `Renderable` → `String` (CR-44, BM-4). Overlay paint
   is `cellsAt` → `Cell[]` composite. The bitmap part imports neither
   consumer.
2. **Animation is not a core concept.** Core has no clock, no thread and
   no tick. Bitmap supplies pure `frameAt` / `cellsAt(elapsed)`; canvas
   supplies the thing that has a clock. Neither leaks into core.