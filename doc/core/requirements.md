# ConsoleKit Core — Requirements (`CR`, `NFR`)

Part 1 of 4. See [the map](../requirements.md) for how the parts fit
together.

Core is the **commons**: the model every other part is written against.
It renders nothing on its own, owns no terminal and no terminal library.

| Part | Package prefix | Depends on | Prefix |
|---|---|---|---|
| **core** (this document) | `dev.consolekit.core` | — | `CR`, `NFR` |
| [text](../text/requirements.md) | `dev.consolekit.text` | core | `TX` |
| [bitmap](../bitmap/requirements.md) | `dev.consolekit.bitmap` | core | `BM`, `AF` |
| [canvas](../canvas/requirements.md) | `dev.consolekit.canvas` | core, bitmap | `CV` |

This document is written **for** those three consumers. Core MUST NOT
*depend* on them (CR-43): no import, no package reference, no compile-time
edge. §4 is allowed knowledge of what they need. Consumer IDs MUST NOT
appear in a core MUST as if they were core requirements.

`MUST` / `SHOULD` / `MAY` are RFC 2119.

---

## 1. Terminology

**Style** — foreground colour, background colour, attribute flags. A
value. Each colour channel may be **unset**.
**Cell** — one character position as a value: one grapheme plus a style,
with glyph, foreground and background each optionally absent (CR-41).
**CellBuffer** — a mutable `w × h` grid of cells, and the one place
cells are composited (CR-46).
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
  attribute flags (bold, dim, italic, underline, strike, reverse). Each
  colour channel MAY be unset. Style values MUST be immutable.
  `Style.NONE` is the style with both channels unset and no attributes.
- **CR-2** Colour depth MUST be one of `NONE`, `ANSI16`, `ANSI256`,
  `TRUECOLOR`, and every colour MUST know how to downgrade itself to a
  lower depth — cube quantisation truecolor→256, nearest perceptual match
  256→16. `Color.DEFAULT` downgrades to itself.
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
  `Color.index(0..255)`, `Color.rgb(r,g,b)`, the sixteen named constants
  exposed as static fields (`Color.RED`, `Color.BRIGHT_CYAN`, …) so that
  `line(Color.RED)` reads naturally, and **`Color.DEFAULT`** — the
  terminal's own default foreground or background, emitted explicitly
  (SGR 39 / 49). `DEFAULT` is a colour; *unset* is the absence of one
  (CR-41). `AsciiColor` MUST NOT be introduced as a second class.

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
  a wide grapheme as one cell of width 2 plus a continuation marker
  (CR-46), never split it across two independently-styled cells.
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
  `WT_SESSION`, `COLORTERM`. Resolution MUST use the JDK and the
  environment only — never a terminal library (CR-22).
- **CR-12** Capabilities MUST carry colour depth, glyph tier, TTY-ness,
  supported attributes, a **probe-time** terminal size (from `COLUMNS` /
  `LINES` when they parse, else unknown; diagnostic only — MUST NOT be
  used for layout; see CR-17), output and input charset, and a
  human-readable reason when anything was degraded.
- **CR-13** Non-TTY output (pipe, file, CI) MUST be detected.
- **CR-14** Explicit configuration MUST be settable at most once, before
  any output; a later attempt MUST fail loudly rather than silently
  produce inconsistent output.
- **CR-15** A standalone diagnostic (`Probe`) MUST print the full resolved
  environment plus a colour and glyph test card, in pure ASCII so it
  survives any terminal. `Probe` is an *application* of the library
  (`dev.consolekit.tools`, CR-43), not a core class, so it may also dump
  `.art` files and report a live terminal size.

### 2.5 The shared render contract

- **CR-16** `Renderable` MUST be a pure function from a render context to
  a list of styled lines: same context, same state, same output. No
  cursor access, no global state, no I/O.
- **CR-17** A `RenderContext` MUST carry the resolved capabilities, the
  theme, and the available width. It MAY also carry an optional size that
  the *caller* fills when it has a current viewport. `render(ctx)` MUST
  be given its context and MUST NOT reach for process-wide state: reading
  `Capabilities.size()` for layout after a resize is a defect, because the
  context is the only size that is current. (The ambient context of
  CR-44 is built *outside* `render`, by `Rendering`.)
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

- **CR-21** Only one class MAY emit escape sequences
  (`core.internal.Ansi`).
- **CR-22** Only one class MAY touch the underlying terminal library
  (JLine), and it MUST live in the canvas part. Core, text and bitmap
  MUST NOT reference JLine at all.
- **CR-23** Only one class MAY contain non-ASCII character literals
  (`core.internal.Glyphs`).
- **CR-24** No render path MAY throw. A failure in rendering MUST be
  caught, MUST disable further animation, MUST warn once, and MUST fall
  back to plain output. A broken renderable MUST NOT crash the caller's
  job. Parsing, loading and construction are **not** render paths.
- **CR-25** The library MUST NOT log through SLF4J or any logging
  framework.
- **CR-26** Whatever a part changes about terminal state MUST be restored
  on normal exit, on exception, on `SIGINT`, and on `SIGTERM`. Restore
  MUST be idempotent, because more than one path can fire.
- **CR-27** Leaving the user's shell with a hidden cursor, an active SGR
  state, raw mode, or a replaced `System.out` is the library's most severe
  defect class. Each restore path MUST have a test.

### 2.7 Layering and interchange

This is the section that exists because there are four parts. It is the
whole of what core owes them, and the whole of what they may assume about
each other.

- **CR-41** `Cell` MUST be a **core** value: one grapheme plus one
  `Style`.
  - The glyph, the foreground and the background are each independently
    **absent**. Absence MUST mean *leave what is underneath*.
  - A colour channel therefore has **three** states: absent (unset in
    the `Style`), `Color.DEFAULT` (opaque, terminal default), or an
    explicit colour. "Opaque with the default background" and
    "transparent background" are different cells.
  - **Attributes travel with the glyph.** Where a source glyph is
    present, the destination takes the source's glyph *and* attributes;
    where it is absent, the destination keeps both. Attributes on a cell
    whose glyph is absent have no effect.
  - `Cell.TRANSPARENT` is absent glyph + `Style.NONE`.
  - Bitmap and canvas both use this type; neither MAY define its own.
- **CR-46** `CellBuffer` MUST be a **core** type: a mutable, fixed-size
  `w × h` grid of cells, and the **only** implementation of compositing.
  - `composite(src, x, y, clip…)` applies CR-41 per channel, clipped. No
    other class MAY implement the absence rule.
  - A wide grapheme occupies its cell plus a continuation marker in the
    next column (CR-35). Writing over either half of a wide cell MUST
    blank the other half.
  - Steady-state `put`, `fill`, `clear` and `composite` MUST NOT
    allocate; the representation is therefore packed, not an array of
    `Cell` objects. `get(x, y)` returning a `Cell` value MAY allocate
    and is for tests and dumps.
  - A `CellBuffer` is single-owner and not thread-safe (NFR-7).
  - `dump()` MUST render the grid as plain text for assertions.
  - A canvas back buffer *is* a `CellBuffer`; a bitmap *resolves into*
    one. Bitmap and canvas MUST NOT define another grid of cells.
- **CR-42** `StyledText` and `Renderable` are the **print** interchange.
  A part that can produce `List<StyledText>` is thereby printable as a
  `String`, printable into scrollback, and blittable as opaque catalogue
  content, without knowing that any of those consumers exist. On the
  print door an unset channel resolves to `Color.DEFAULT` and an absent
  glyph to a space. Overlay paint is not this contract: it composites
  `CellBuffer`s (CR-46). That is allowed because both sides already
  depend on core — it is not a new bridge. New *print* plumbing is a
  design defect: add a `Renderable`.
- **CR-43** The dependency graph MUST stay acyclic and MUST be exactly:
  core ← text, core ← bitmap, core ← canvas, bitmap ← canvas.
  - **A part is a package prefix**: `dev.consolekit.core`, `.text`,
    `.bitmap`, `.canvas`, each including its sub-packages. A class under
    one prefix MUST import only its own prefix, `dev.consolekit.core`,
    and — for canvas only — `dev.consolekit.bitmap`.
  - `X.internal` packages are importable only from within `X`, except
    `dev.consolekit.core.internal`, which every part may use. No
    `internal` package is exported (NFR-9b).
  - `dev.consolekit.tools` (`Probe`, demos) is an *application*: it may
    reference every part; nothing MUST reference it. Nothing MUST
    reference text except an application.
  - A test MUST enforce all of this by package scan (NFR-10).
- **CR-44** Core MUST own the path from a `Renderable` to characters on
  an ordinary stream, through one named API, `core.Rendering`:
  `toString(Renderable)`, `toString(Renderable, RenderContext)` and
  `print(Renderable, PrintStream)`, with escapes applied per the resolved
  capabilities by the single escape emitter (CR-21).
  - The one-argument form builds the **ambient context**: resolved
    capabilities for stdout, `Theme.DEFAULT`, width per CR-45.
  - Every shipped `Renderable` MUST implement `toString()` as
    `Rendering.toString(this)`, so `System.out.println(table)` works. A
    test MUST enforce this over all shipped implementations.
  - Other parts may facade this path; they MUST NOT own a second escape
    emitter.
- **CR-45** **Ambient width** — used by `Rendering.toString(Renderable)`
  and by any helper that needs a width and was not given one — MUST be
  the `COLUMNS` environment variable if it parses as a positive integer,
  else 80. It MUST NOT come from a terminal library. Many interactive
  shells will hit the 80 fallback because bash does not export `COLUMNS`
  to child processes; that is not a defect.

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
  mode is stateless; bitmap values are immutable and therefore freely
  shareable; a `CellBuffer` is mutable and confined to its single owner;
  canvas mode is one render thread fed by one queue, with the key
  listener as a producer. Widgets updated from other threads MUST NOT be
  able to tear a frame.
- **NFR-8** Runtime dependencies MUST be limited to JLine. No jansi,
  ever. No ncurses. Source and bytecode target **JDK 21**. The build is
  Maven (`mvn verify`).
- **NFR-9** The build MUST NOT require preview features.
- **NFR-9b** The artefact is a real JPMS module `dev.consolekit` with
  `module-info.java`. No `*.internal` package MUST be exported; the
  NFR-10 source-scan tests back that, they do not replace it.
- **NFR-10** Each structural invariant (CR-21 through CR-23, CR-43,
  CR-44's `toString` rule, and the canvas part's no-public-writer rule)
  MUST be enforced by a test — a source scan or a behavioural test — not
  by convention.
- **NFR-11** No rendered line MAY exceed the available width as measured
  per CR-6, in any part, with any of the CR-10 fixture strings.
- **NFR-12** `\n` line endings. Tests are JUnit 5. Golden files live
  under `src/test/resources/golden/` and MUST NOT be regenerated
  silently: regeneration happens only under
  `-Dconsolekit.golden.update=true`, and the resulting diff is reviewed.
  Process-wide mutable state MUST be confined to one documented runtime
  holder (`core.ConsoleRuntime`). Other parts keep their process-wide
  state in an opaque, typed session slot on that holder; core MUST NOT
  name their types.
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
something not listed here, the change is made here first.

| Part | Needs from core | Requirements |
|---|---|---|
| text | `Color`, `Style`, `Theme`, `Capabilities`, escape emission, the "return the string unchanged when escapes can't render" rule, ambient width | CR-1…CR-5, CR-40, CR-44, CR-45 |
| bitmap | `Cell` with absent channels, `CellBuffer` to resolve into, `Style`, glyph tier + substitution, grapheme width, `Renderable` + `StyledText` | CR-41, CR-46, CR-6…CR-10, CR-29…CR-31, CR-42 |
| bitmap | a print path that needs no terminal ownership | CR-44 |
| canvas | `Cell`, `CellBuffer` and its compositor, `RenderContext` with an optional caller-filled size, `Renderable` to print/blit as catalogue content, capability degradation, restore-path rules, a session slot | CR-41, CR-46, CR-17, CR-42, CR-26, CR-27, NFR-12 |
| canvas | the bitmap model, to wrap as a widget | BM-8…BM-10, BM-13, BM-15 |

Two consequences worth stating outright, because they are the reason the
split is cheap:

1. **A bitmap prints and paints through two doors, both using core
   types.** Print is `Renderable` → `String` (CR-44, BM-4). Overlay paint
   is `cellsAt` → `CellBuffer` → `composite` (BM-13, CR-46). The bitmap
   part imports neither consumer.
2. **Animation is not a core concept.** Core has no clock, no thread and
   no tick. Bitmap supplies pure functions of elapsed time; canvas
   supplies the thing that has a clock.
