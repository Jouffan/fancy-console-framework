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

**Work in one part at a time.** A change that seems to need a new *print*
edge in that table is almost always a missing `Renderable` (CR-42), not a
missing dependency. Overlay paint is Cell composite (BM-13, CV-94), not
a third interchange. Cross-part rules live in core §2.7 (CR-41…CR-44)
and the map.

This repository is **docs only — there is no source tree yet**, and
nothing is implemented. Do not claim a milestone is done because a
requirement reads as though it describes existing behaviour. A v2
prototype existed on an earlier branch and is **not** merged here;
`doc/architecture.md` §6 records what it taught us, and nothing more.
Build from the specification, starting at M0.

## Never break

| ID | Rule |
|---|---|
| CR-21 | Only one class emits escape sequences (`core.internal.Ansi`) |
| CR-22 | Only one class touches JLine (`canvas.internal.TerminalPort`) |
| CR-23 | Only one class contains non-ASCII literals (`core.internal.Glyphs`) |
| CR-41 | `Cell` is a **core** type. Bitmap and canvas share it; neither defines its own |
| CR-42 | `StyledText` / `Renderable` are the **print** interchange. Overlay paint is Cell composite (CV-94) |
| CR-43 | Dependency graph is acyclic and fixed: core ← text, core ← bitmap, core ← canvas, bitmap ← canvas. Enforced by the reactor — an illegal edge does not compile |
| CR-44 | Core owns `Renderable → String`. Text is a facade over it, not the owner |
| CV-22 | No public path to the terminal writer; no caller-written escapes |
| NFR-12 | All static mutable state lives in `ConsoleRuntime` (core), behind an **opaque slot** — it never names another part's type |
| CR-24 | No render path throws. `Ctrl-C` → `Cancelled` and `.art` loading are **not** render paths |
| CR-40 | One colour type: `dev.consolekit.core.Color`. No `AsciiColor` |
| TX-2 | Text mode never opens a terminal, loads native code, or starts a thread |
| BM-12 | Bitmap has no thread, no clock, no terminal, no canvas import |
| CV-47 | A key is offered until handled (focus → app → built-ins). Never broadcast |
| CV-4 | One canvas per process |

CR-43 is enforced by the reactor. CR-21, CR-22, CR-23 and CV-22 are
enforced by NFR-10 scan / behavioural tests — not by convention. Scope
the CR-23 scan to `src/main`; the CR-10 fixtures are non-ASCII on
purpose.

JPMS: one `module-info.java` per artefact; never export a `*.internal`
package; no package shared by two artefacts.

## Build and test

- JDK **21**, Maven, **no preview** (NFR-9). Do not use `IO.println`.
- Four artefacts: `consolekit-core`, `consolekit-text`,
  `consolekit-bitmap`, `consolekit-canvas`.
- Runtime dependency: **JLine in `consolekit-canvas` only** —
  `jline-terminal` + `jline-terminal-jni`. Not FFM (needs JDK 22+), not
  JNA (removed in JLine 4). No jansi, no ncurses (NFR-8). Adding a
  dependency to core, text or bitmap is a defect.
- `mvn verify` is the gate. A milestone also needs a human visual check
  of the relevant demo on a real terminal (NFR-18).
- Tests: JUnit 5. Goldens: `src/test/resources/golden/`, UTF-8, `\n`
  (NFR-13). Canvas layouts: `AsciiCanvas.dump()`, no terminal.
  What-the-user-sees: `canvas.internal.VirtualTerminal`.
- NFR-5 (no alloc per frame) lives in the canvas part and applies to
  **layout / paint / diff / flush only**. The event queue may allocate.
  `cellsAt` allocation is open question 6 (M4b).

## Current work

Next shippable slice: **M0** — the build skeleton, `module-info.java`,
and the NFR-10 structural scan tests. Nothing exists yet; M0 is what
makes every later milestone checkable.

Then the order in `doc/architecture.md` §5. Do not skip.

| Step | What | Notes |
|---|---|---|
| M0 | pom, `module-info.java`, structural scan tests | CR-21, CR-22, CR-23, CR-43, CV-22. Scope CR-23 to `src/main` |
| M1 | core model, capabilities, `Text` | CR-1…CR-20, TX-1…TX-9 |
| M1b | `Color` constants, `Styler`, `Snippets` | TX-10…TX-15, CR-40 |
| M2 | static `Renderable` catalogue | CR-18 |
| M3 | canvas engine, `place` / `update` / `focus` / `remove` | no `send` yet. Create `core.Cell` here (CR-41) |
| M4 | `WidgetId`, `WidgetEvent`, `handle.send`, catalogue | `Tick` carries elapsed time |
| M4b | bitmap part: `AsciiBitmap`, `Palette`, `AsciiAnimation`, `cellsAt` (BM-1…BM-13, AF-1…AF-8) | needs core only; testable with no canvas |
| M4c | `AsciiSprite` (CV-90…CV-94) | needs M4 `Tick` + M4b. Clock + CV-94 composite, not `blit(Renderable)` |
| M5 | content-side encoding CR-29…CR-36; CR-39 in canvas tests | before keys (CR-37) |
| NFR-19 | conhost canvas rows of NFR-14 | **start-gate**, not a milestone. Before M6 **starts**. Not M6-done, not M7 |
| M6 | `KeyListener`, focus slot, CV-47 | built-ins `Ctrl-C` and `Ctrl-L` only |
| M7 | remaining NFR-14 rows, including macOS | does not relax NFR-19 |

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
  percentage-or-fill widget on that axis.
- **KeyListener decodes and enqueues.** It does not route and does not
  call widgets. Routing is `AsciiCanvas` on drain (CV-47).
- **Focus** is a slot, not a navigator (CV-48).
- **Two output doors.** Print: `Renderable` → `StyledText` → `String`
  (CR-44, BM-4). Paint: `cellsAt(elapsed)` → core `Cell[]` → composite
  (BM-13, CV-94). `Surface.blit(Renderable)` is catalogue / `Panel`,
  **not** the sprite path. Never add `print()` or `paint(Surface)` to
  the bitmap.
- **`AsciiBitmap`** (BM-1) is a value in `dev.consolekit.bitmap`: X × Y
  glyph-slot + style-slot pairs plus tables (AF-3), per-channel
  transparency (BM-3), single-column cells validated at construction
  (BM-2). It implements `Renderable` for the print door.
- **Animation is a pure function of elapsed time**: `frameAt` /
  `cycleOffsetAt` / `cellsAt(elapsed)` in bitmap (BM-9, BM-10, BM-13);
  the clock lives in `AsciiSprite` (CV-90), which is the only piece that
  may have one. Frames are skipped, never queued. Paused or unchanged ⇒
  not dirty (CV-92). Construct sprites with `new AsciiSprite(Art.load(...))`.
  No `AsciiBitmap.load` / `AsciiSprite.load`.
- **`.art`** (AF-1…AF-8) is the *one* persistence format for both — a
  bitmap is a one-frame animation. I/O is **`Art.load` / `Art.write`
  only**. **Binary**, uncompressed, big-endian, single forward pass:
  magic + header + glyph table + style table + frames. A stored cell is
  **2 bytes** — glyph slot, style slot — slot `0` = transparent, so
  ≤ 255 glyphs and ≤ 255 styles per file. A style slot indexes a full
  `Style` (fg/bg/attrs), **not** a packed VGA attribute. Check every
  declared count against its cap **before** allocating; throw with the
  byte offset. Load is **not** a render path, CR-24 does not apply.
  Text (rows + palette) is the authoring and review surface only
  (BM-5, AF-7) — `toSource()` is a view, never a second file format.
  `AsciiBitmap.parse` stays for authoring.
- **Big/filled art is not hand-written**: `AsciiBitmap.generate(w, h,
  (x,y) -> Cell)` evaluated once at construction (BM-7, never a
  per-paint callback), and **palette cycling** (BM-10) animates an
  N-colour pattern from one frame. Cells store the slot, not the
  resolved `Style`.
- **Four artefacts, four modules** (NFR-9b): `consolekit-core`,
  `consolekit-text`, `consolekit-bitmap`, `consolekit-canvas`. The
  reactor enforces CR-43. **JLine is a canvas dependency only** — core,
  text and bitmap have no third-party dependency, which is why
  `TerminalPort` lives in canvas. No split packages.

## Still open (ask, don't guess)

1. Event-queue bound and whether `LogMessage` is droppable (CV-43).
2. Nested widgets (CV-32) — deferred until a widget actually needs them.
3. Whether `Snippets.print…` (TX-14) stays.
4. *(closed)* Four separate artefacts — yes, decided (NFR-9b).
5. The `.art` size/dimension/frame caps (AF-6) — pick them at M4b.
6. Whether `cellsAt` may allocate, or must fill a caller-supplied
   buffer — pick at M4b. Do not pretend paint is a `Renderable` blit.

## Packages

See `doc/architecture.md` §1. One module per artefact:

| Artefact | Module | Exported | Not exported |
|---|---|---|---|
| `consolekit-core` | `dev.consolekit.core` | `.core`, `.core.render`, `.core.widget` | `.core.internal` |
| `consolekit-text` | `dev.consolekit.text` | `.text` | — |
| `consolekit-bitmap` | `dev.consolekit.bitmap` | `.bitmap` | `.bitmap.internal` |
| `consolekit-canvas` | `dev.consolekit.canvas` | `.canvas`, `.canvas.widget`, `.canvas.event`, `.canvas.input` | `.canvas.internal` |

Allowed edges (CR-43): core ← text, core ← bitmap, core ← canvas,
bitmap ← canvas. Nothing else.

Canvas widgets: `Label`, `StatusBar`, `Clock`, `ProgressBar`, `Spinner`,
`MultiProgress`, `StatusList`, `Graph`, `LogPane`, `Panel`,
`AsciiSprite`. That shipped list is closed. `Widget` is public;
applications that need something else implement `Custom` or their own
`Widget`.