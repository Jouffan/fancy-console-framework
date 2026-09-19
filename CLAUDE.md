# ConsoleKit — agent notes

Java 21 terminal UI library. One JPMS module `dev.consolekit`, four
parts, **a part is a package prefix**. The specification is the source of
truth; this file is only the rules that must never be out of mind, plus
where to read the rest. **It deliberately does not restate requirements
— read them.**

## Read before you work

| Working in | Read first |
|---|---|
| anything | `doc/requirements.md` (the map: seams, decisions §8, open questions §9) and `doc/architecture.md` (packages, edges, build order §5) |
| `dev.consolekit.core` | `doc/core/requirements.md`, `doc/core/architecture.md` |
| `dev.consolekit.text` | `doc/text/requirements.md`, `doc/text/architecture.md` |
| `dev.consolekit.bitmap` | `doc/bitmap/requirements.md`, `doc/bitmap/art-format.md`, `doc/bitmap/architecture.md` |
| `dev.consolekit.canvas` | `doc/canvas/requirements.md`, `doc/canvas/architecture.md` |

Precedence: requirements > architecture > this file > any existing code.
If two documents disagree, **stop and ask** — do not invent a third
reading, and do not "fix" the spec to match code.

**Work in one part at a time.** Cite requirement IDs (`CR-41`, `BM-13`)
in test names and commit messages.

## State of the tree

Specification phase; implementation starts at **M1**. An older "v2"
branch may be present. It is a *reference*: a class that compiles and
has tests is not therefore correct. If v2 sources are in the tree,
`doc/architecture.md` §6 says what to keep, move, reinterpret or delete.
If they are not, do not look for them and do not recreate v2 shapes.

## Never break

| ID | Rule |
|---|---|
| CR-43 | Edges are exactly core ← text, core ← bitmap, core ← canvas, bitmap ← canvas. A class under `dev.consolekit.X` imports only `X..`, `core..`, and (canvas only) `bitmap`. `tools` may import anything; nothing imports `tools` |
| CR-21 | Only `core.internal.Ansi` emits escape sequences |
| CR-22 | Only `canvas.internal.TerminalPort` touches JLine. Core, text and bitmap never reference it |
| CR-23 | Only `core.internal.Glyphs` contains non-ASCII literals |
| CR-41 / CR-46 | `Cell` and `CellBuffer` are core. `CellBuffer.composite` is the **only** implementation of the absence rule. No second cell type, grid, or compositor anywhere |
| CR-42 | `Renderable` / `StyledText` are the **print** door. Overlay paint is the cell-composite door. Never route a sprite through `blit(Renderable)`; never add `print()` or `paint(Surface)` to a bitmap |
| CR-44 | Core owns `Renderable → String` via `core.Rendering`. Every shipped `Renderable.toString()` delegates to it |
| CR-40 | One colour type, `core.Color`. `Color.DEFAULT` ≠ unset |
| CR-24 | No render path throws. Parsing, `.art` loading and construction are **not** render paths and *should* throw |
| TX-2 | Text never opens a terminal, loads native code, or starts a thread. Width is `COLUMNS` else 80 (CR-45) — do not "fix" the 80 |
| BM-12 | Bitmap has no thread, no clock, no terminal, no canvas import, no compositing |
| CV-4 | One canvas per process |
| CV-22 | No public path to the terminal writer; no caller-written escapes |
| CV-39 | Events are payloads; the target is on the `Envelope`. Events are the only mutation path — there is no `handle.update()` |
| CV-95 | `Tick` is synthesised by the loop, never queued, never sendable |
| CV-47 / CV-49 | A key is offered focus → app → built-ins, never broadcast. `Ctrl-C` is a **signal**, not a key; there is no `Cancelled` |
| NFR-12 | All static mutable state lives in `core.ConsoleRuntime`; other parts use its opaque session slot |
| NFR-9b | No `*.internal` package is exported from `module-info.java` |

Structural rules are enforced by scan tests that ship in M1 (NFR-10). If
one fails, the code is wrong, not the test.

## Do not add

- Form / smart-input widgets: menus, confirm, text input, validation,
  masks, completion, Tab-cycle, caret, selection chrome (CV-67, CV-69).
- The alternate screen buffer; nested widgets, scrollbars, dialogs; a
  second canvas.
- Image / sixel rendering or an image→ASCII converter; sprite collision,
  scene graphs, tweening, a game loop.
- A second colour type, cell type, cell grid, compositor, escape
  emitter, module, or on-disk format. jansi. Preview APIs. `IO.println`.
  Any logging framework (CR-25).
- Widgets beyond the closed CV-65 list. Compatibility shims for v2 types.

## Build and test

```
mvn -q verify                                  # the gate
mvn -q -Dtest=CellBufferTest test              # one class
mvn -q -Dtest=CellBufferTest#compositeKeepsGlyph test
mvn -q verify -Dconsolekit.golden.update=true  # regenerate goldens — see below
```

- JDK **21**, Maven, **no preview** (NFR-9). Runtime dependency: JLine
  only (NFR-8).
- Tests are JUnit 5. **Core is tests-first** (NFR-2).
- Goldens: `src/test/resources/golden/`, UTF-8, `\n`. Never regenerate a
  golden to make a test pass. Regenerate only when the requirement
  changed, show the diff, and say so (NFR-12). An `.art` byte-golden diff
  is a *format change* (art-format §3).
- Assertion surfaces: `Rendering.toString` for print, `CellBuffer.dump()`
  for cells and layout, `toSource()` for bitmaps, `EventReplayer` for
  widgets and sprites, `VirtualTerminal` for what the user sees. **No
  test sleeps.**
- NFR-5 (no per-iteration allocation) covers tick delivery, layout,
  paint, diff and flush. The queue, envelopes and key decoding may
  allocate.

## Stop and ask

- A requirement and an architecture document disagree, or a requirement
  is ambiguous.
- A change seems to need a new edge in CR-43. It is almost always a
  missing `Renderable` (print) or a missing core rule (map §3).
- Anything listed in `doc/requirements.md` §9 **Open questions** that does
  not yet have a default below.
- You are about to reopen something in `doc/requirements.md` §8
  **Decisions**.
- **End of a milestone.** `mvn verify` green is necessary, not
  sufficient: NFR-18 needs a human to run the demo on a real terminal,
  and NFR-17 rows need a human on that platform. Report what to run and
  what to look for; do not mark the milestone done yourself.
- **NFR-19:** do not start M6 until a human confirms the conhost rows.

## Defaults picked for open questions

These were chosen before implementation started. Do not change without
asking.

| Decided before | What | Default |
|---|---|---|
| M3 | Minimum canvas size (CV-8) | **20 × 3** |
| M3 | GraphemeTable intern bound | **4096 clusters**; replacement past bound |
| M3 | Stale-partial-line flush timeout (CV-15) | **50 ms** |
| M4 | Event queue bound (CV-43) | **1024 envelopes** |
| M1b | Whether `Snippets.print…` ships (TX-14) | **still open** — stop and ask |

## Current work

**M1 — core**, per `doc/architecture.md` §5 step 1. Then the steps in
order; M2b (bitmap) may run in parallel with M3 (canvas engine) because
it needs core only. Do not skip steps and do not start M4c before both
M2b and M4.

Definition of done for every step: the requirement IDs listed for the
milestone in `doc/requirements.md` §4 each have at least one test naming
them; `mvn -q verify` is green; public types have Javadoc with a usage
snippet (NFR-1); the human check above has been requested.
