# ConsoleKit — agent notes

Ground truth is **four requirement documents**, one per part, mapped by
`doc/requirements.md`. Architecture mirrors them, mapped by
`doc/architecture.md`. If a requirement and an architecture document
disagree, requirements win; then stop and ask — do not invent a third
reading.

| Part | Requirements | Architecture | Prefix | May depend on |
|---|---|---|---|---|
| core | `doc/core/requirements.md` | `doc/core/architecture.md` | `CR`, `NFR` | — |
| text | `doc/text/requirements.md` | `doc/text/architecture.md` | `TX` | core |
| bitmap | `doc/bitmap/requirements.md` + `doc/bitmap/art-format.md` | `doc/bitmap/architecture.md` | `BM`, `AF` | core |
| canvas | `doc/canvas/requirements.md` | `doc/canvas/architecture.md` | `CV` | core, bitmap |

**Work in one part at a time.** A change that seems to need a new edge
in that table is almost always a missing `Renderable` (CR-42), not a
missing dependency. Cross-part rules live in core §2.7 (CR-41…CR-44) and
nowhere else.

This branch was written against **v2** (three tiers, pin/strip). A class
that compiles and has tests is not therefore correct. Keep / reinterpret /
obsolete is `doc/architecture.md` §6. The pin/strip engine is being
replaced; stream capture, `TerminalPort`, capabilities and the static
widgets are the parts to keep.

This workspace may contain **docs only**. Do not invent a source tree.

## Never break

| ID | Rule |
|---|---|
| CR-21 | Only one class emits escape sequences (`internal.Ansi`) |
| CR-22 | Only one class touches JLine (`internal.TerminalPort`) |
| CR-23 | Only one class contains non-ASCII literals (`internal.Glyphs`) |
| CR-41 | `Cell` is a **core** type. Bitmap and canvas share it; neither defines its own |
| CR-42 | `StyledText` / `Renderable` are the only cross-part interchange |
| CR-43 | Dependency graph is acyclic and fixed: core ← text, core ← bitmap, core ← canvas, bitmap ← canvas |
| CR-44 | Core owns `Renderable → String`. Text is a facade over it, not the owner |
| CV-22 | No public path to the terminal writer; no caller-written escapes |
| NFR-12 | All static mutable state lives in `ConsoleRuntime` |
| CR-24 | No render path throws. `Ctrl-C` → `Cancelled` and `.art` loading are **not** render paths |
| CR-40 | One colour type: `dev.consolekit.core.Color`. No `AsciiColor` |
| TX-2 | Text mode never opens a terminal, loads native code, or starts a thread |
| BM-12 | Bitmap has no thread, no clock, no terminal, no canvas import |
| CV-47 | A key is offered until handled (focus → app → built-ins). Never broadcast |
| CV-4 | One canvas per process |

Enforced by NFR-10 source-scan / behavioural tests, not by convention.
JPMS: `module-info.java`, module `dev.consolekit`; do **not** export
`dev.consolekit.internal`.

## Build and test

- JDK **21**, Maven, **no preview** (NFR-9). Do not use `IO.println`.
- Runtime dependency: JLine only. No jansi, no ncurses (NFR-8).
- `mvn verify` is the gate. A milestone also needs a human visual check
  of the relevant demo on a real terminal (NFR-18).
- Tests: JUnit 5. Goldens: `src/test/resources/golden/`, UTF-8, `\n`
  (NFR-13). Canvas layouts: `AsciiCanvas.dump()`, no terminal.
  What-the-user-sees: `internal.VirtualTerminal`.
- NFR-5 (no alloc per frame) applies to **layout / paint / diff / flush
  only**. The event queue may allocate.

## Current work

Next shippable slice: **M1b** — `Styler`, `Snippets`, `Color` named
constants (TX-10…TX-15, CR-40). No engine change.

Then the migration order in `doc/architecture.md` §5, in order. Do not
skip.

| Step | What | Notes |
|---|---|---|
| M1b | `Color` constants, `Styler`, `Snippets` | now |
| M3 | canvas engine, `place` / `update` / `focus` / `remove` | no `send` yet. CV-9 and CV-13 are **new**. Create `core.Cell` here (CR-41) |
| M3b | package-scan test for the CR-43 layering | cheap now, archaeology later |
| M4 | `WidgetId`, `WidgetEvent`, `handle.send`, catalogue | `Tick` carries elapsed time |
| M4b | bitmap part: `AsciiBitmap`, `Palette`, `AsciiAnimation`, `.art` codec (BM-1…BM-12, AF-1…AF-8) | needs core only; still-bitmap half testable with no canvas |
| M4c | `AsciiSprite` (CV-90…CV-93) | needs M4 `Tick` + M4b. Should be a dozen lines |
| M5 | content-side encoding CR-29…CR-36, CR-39 | before keys (CR-37) |
| NFR-19 | conhost canvas rows of NFR-14 | **start-gate**, not a milestone. Before M6 **starts**. Not M6-done, not M7 |
| M6 | `KeyListener`, focus slot, CV-47 | built-ins `Ctrl-C` and `Ctrl-L` only |
| M7 | remaining NFR-14 rows, including macOS | does not relax NFR-19 |

Obsolete pin types (`AsciiWidget`, `PinHandle`, `PinContext`,
`PinStack`, `FancyConsole.pin`) stay compiling until `place` lands,
then delete. Do not delete them up front.

## Do not add

- Form / smart-input widgets: menus, confirm, text input, validation,
  masks, completion, Tab-cycle, caret, selection chrome (CV-67, CV-69).
  `onKey` and CV-47 stay; those widgets do not.
- Alternate screen buffer.
- Nested widgets, scrollbars, dialogs, a second canvas.
- Image/sixel rendering or an image→ASCII converter; sprite collision,
  scene graphs, tweening, a game loop. `AsciiBitmap` is text the author
  writes (BM-1, BM-5); `AsciiSprite` advances frames on `Tick` (CV-90).
- A second colour type, a second cell type, a second module, a second
  on-disk format, jansi, preview APIs.

Canvas widgets (CV-65) may return handled from `onKey` to cycle their
own visualisation. That is not a form.

## Resolved rules (do not reopen)

- **Canvas width** is the terminal width (CV-7). No sub-width option.
  The target picture is 80×12 on an 80×24 terminal.
- **Canvas height** (CV-6): explicit config, else sum of *definite*
  docked vertical sizes (fixed / preferred), else full viewport.
  Percentage and fill-remaining do **not** feed back into `h`.
  Last-docked-takes-remaining (CV-24) runs after `h` is known.
- **Layout size** is per-frame on `RenderContext` (CV-27).
  `Capabilities.size()` is probe-time / diagnostic only (CR-12).
  Reading it for layout after a resize is a defect.
- **Text-mode width** (TX-15): `COLUMNS` if it parses as a positive
  integer, else 80. Never JLine. Bash not exporting `COLUMNS` is
  expected; do not "fix" it.
- **`logTo`** (CV-13) **additionally** delivers `println` as
  `LogMessage`. It MUST NOT suppress the underlying stream.
- **`WidgetId`**: minted at `place`, process-unique, never reused after
  `remove`. Field on every `WidgetEvent`, stamped by `handle.send`.
  `send` is M4.
- **Percentage leftover** (CV-26): `floor`; remainder to the last
  percentage-or-fill widget on that axis (CV-28).
- **KeyListener decodes and enqueues.** It does not route and does not
  call widgets. Routing is `AsciiCanvas` on drain (CV-47).
- **Focus** is a slot, not a navigator (CV-48).
- **`AsciiBitmap`** (BM-1) is a `Renderable` value in `dev.consolekit.bitmap`:
  X × Y core cells, per-channel transparency (BM-3), single-column cells
  validated at construction (BM-2). Its **only** output contract is
  `Renderable` (BM-4) — that is what makes it printable to an ordinary
  console (CR-44), into scrollback (CV-10) and onto the canvas (CV-36)
  with no consumer-specific code. Never add a `print()` or a
  `paint(Surface)` to it.
- **Animation is a pure function of elapsed time**: `frameAt(elapsed)` /
  `cycleOffsetAt(elapsed)` in bitmap (BM-9, BM-10); the clock lives in
  `AsciiSprite` (CV-90), which is the only piece that may have one.
  Frames are skipped, never queued. Paused or unchanged ⇒ not dirty
  (CV-92).
- **`.art`** (AF-1…AF-8) is the *one* persistence format for both — a
  bitmap is a one-frame animation. **Binary**, uncompressed, big-endian,
  single forward pass: magic + header + glyph table + style table +
  frames. A cell is **2 bytes** — glyph slot, style slot — slot `0` =
  transparent, so ≤ 255 glyphs and ≤ 255 styles per file. A style slot
  indexes a full `Style` (fg/bg/attrs), **not** a packed VGA attribute.
  Check every declared count against its cap **before** allocating; throw
  with the byte offset. Load is **not** a render path, CR-24 does not
  apply. Text (rows + palette) is the authoring and review surface only
  (BM-5, AF-7) — `toSource()` is a view, never a second file format.
- **Big/filled art is not hand-written**: `AsciiBitmap.generate(w, h,
  (x,y) -> Cell)` evaluated once at construction (BM-7, never a
  per-paint callback), and **palette cycling** (BM-10) animates an
  N-colour pattern from one frame. Cells store the slot, not the
  resolved `Style`.
- **One JPMS module** `dev.consolekit`, four documentary parts. The
  parts are packages, not artefacts (requirements map §8).

## Still open (ask, don't guess)

1. Event-queue bound and whether `LogMessage` is droppable (CV-43).
2. Nested widgets (CV-32) — deferred until a widget actually needs them.
3. Whether `Snippets.print…` (TX-14) stays.
4. Whether the four parts ever become separate artefacts — not now.
5. The `.art` size/dimension/frame caps (AF-6) — pick them at M4b.

## Packages

See `doc/architecture.md` §1. Public: `dev.consolekit`, `.core`,
`.render`, `.widget`, `.bitmap`, `.canvas`, `.canvas.widget`, `.event`,
`.input`. Internal, not exported: `dev.consolekit.internal`.

Allowed edges (CR-43): core ← text, core ← bitmap, core ← canvas,
bitmap ← canvas. Nothing else.

Canvas widgets: `Label`, `StatusBar`, `Clock`, `ProgressBar`, `Spinner`,
`MultiProgress`, `StatusList`, `Graph`, `LogPane`, `Panel`,
`AsciiSprite`. That list is closed for this library.