# ConsoleKit — agent notes

Ground truth is `doc/requirements.md` (v3 IDs) and `doc/architecture.md`
(target structure). If they disagree, requirements win; then stop and
ask — do not invent a third reading.

This branch was written against **v2** (three tiers, pin/strip). A class
that compiles and has tests is not therefore correct. Keep / reinterpret /
obsolete is `doc/requirements.md` §11.2. The pin/strip engine is being
replaced; stream capture, `TerminalPort`, capabilities and the static
widgets are the parts to keep.

This workspace may contain **docs only**. Do not invent a source tree.

## Never break

| ID | Rule |
|---|---|
| CR-21 | Only one class emits escape sequences (`internal.Ansi`) |
| CR-22 | Only one class touches JLine (`internal.TerminalPort`) |
| CR-23 | Only one class contains non-ASCII literals (`internal.Glyphs`) |
| CV-22 | No public path to the terminal writer; no caller-written escapes |
| NFR-12 | All static mutable state lives in `ConsoleRuntime` |
| CR-24 | No render path throws. `Ctrl-C` → `Cancelled` is **not** a render path |
| CR-40 | One colour type: `dev.consolekit.core.Color`. No `AsciiColor` |
| TX-2 | Text mode never opens a terminal, loads native code, or starts a thread |
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

Then architecture §9, in order. Do not skip.

| Step | What | Notes |
|---|---|---|
| M1b | `Color` constants, `Styler`, `Snippets` | now |
| M3 | canvas engine, `place` / `update` / `focus` / `remove` | no `send` yet. CV-9 and CV-13 are **new** |
| M4 | `WidgetId`, `WidgetEvent`, `handle.send`, catalogue | |
| M4b | `AsciiBitmap`, `AsciiSprite` (CV-70…CV-89) | bitmap + `.art` codec need only M3; sprite needs M4 `Tick` |
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
  writes (CV-70, CV-74); `AsciiSprite` advances frames on `Tick` (CV-77).
- A second colour type, a second module, jansi, preview APIs.

Canvas widgets (CV-65) may return handled from `onKey` to cycle their
own visualisation. That is not a form.

## Resolved rules (do not reopen)

- **Canvas width** is the terminal width (CV-7). No sub-width option.
  The §1.2 picture is 80×12 on an 80×24 terminal.
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
- **`AsciiBitmap`** (CV-70) is a static `Renderable` value in `.widget`:
  X × Y cells, per-channel transparency (CV-72), single-column cells
  validated at construction (CV-71). **`AsciiSprite`** (CV-76) is a
  canvas widget: no thread, no sleep, no clock in `paint` — frames
  advance from `Tick` elapsed time and are skipped, never queued
  (CV-77). Paused ⇒ not dirty (CV-78).
- **`.art`** (CV-81…CV-86) is the *one* persistence format for both — a
  bitmap is a one-frame animation. **Binary**, uncompressed, big-endian,
  single forward pass: magic + header + glyph table + style table +
  frames. A cell is **2 bytes** — glyph slot, style slot — slot `0` =
  transparent, so ≤ 255 glyphs and ≤ 255 styles per file. A style slot
  indexes a full `Style` (fg/bg/attrs), **not** a packed VGA attribute.
  Check every declared count against its cap **before** allocating; throw
  with the byte offset. Load is **not** a render path, CR-24 does not
  apply. Text (rows + palette) is the authoring and review surface only
  (CV-74, CV-89) — `toSource()` is a view, never a second file format.
- **Big/filled art is not hand-written**: `AsciiBitmap.generate(w, h,
  (x,y) -> Cell)` evaluated once at construction (CV-87, never a
  per-paint callback), and **palette cycling** (CV-88) animates an
  N-colour pattern from one frame on a second `Tick` accumulator. Cells
  store the palette slot, not the resolved `Style`.
- **One JPMS module** `dev.consolekit`.

## Still open (ask, don't guess)

1. Event-queue bound and whether `LogMessage` is droppable (CV-43).
2. Nested widgets (CV-32) — deferred until a widget actually needs them.
3. Whether `Snippets.print…` (TX-14) stays.

## Packages

See architecture §2. Public: `dev.consolekit`, `.core`, `.render`,
`.widget`, `.canvas`, `.canvas.widget`, `.event`, `.input`.
Internal, not exported: `dev.consolekit.internal`.

Canvas widgets: `Label`, `StatusBar`, `Clock`, `ProgressBar`, `Spinner`,
`MultiProgress`, `StatusList`, `Graph`, `LogPane`, `Panel`,
`AsciiSprite`. That list is closed for this library.