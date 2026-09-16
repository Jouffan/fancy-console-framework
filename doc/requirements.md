# ConsoleKit — Requirements map (v4, four parts)

v4 does one thing to v3: it **splits the single requirements document
into four parts** so each can be designed, argued about and built
without holding the other three in your head. No requirement was
weakened in the split. The v3 IDs `CR`, `TX`, `CV` keep their numbers;
the bitmap requirements move out of the `CV` range into their own
prefixes (§5).

This document is the map. It holds only what is *between* the parts:
the layering rule, the shared contract, milestones, status, and the ID
history. Everything normative lives in a part.

| Part | Document | Prefix | Depends on |
|---|---|---|---|
| **Core** — the commons | [core/requirements.md](core/requirements.md) | `CR`, `NFR` | — |
| **Text** — colour a string | [text/requirements.md](text/requirements.md) | `TX` | core |
| **Bitmap** — X × Y coloured characters | [bitmap/requirements.md](bitmap/requirements.md) | `BM` | core |
| ↳ file format | [bitmap/art-format.md](bitmap/art-format.md) | `AF` | core |
| **Canvas** — own the terminal | [canvas/requirements.md](canvas/requirements.md) | `CV` | core, bitmap |

Architecture is split the same way: [architecture.md](architecture.md)
is its map, with a document per part.

---

## 1. Why four parts

Merging everything into one document was making every decision cost more
than it was worth: a question about a file format dragged in the render
loop, a question about docking dragged in charset resolution. Divide and
conquer — but keep one place where the seams are written down, because
the seams are the only thing that can rot silently.

The split is drawn so that **each part is designed against core and
nothing else**. Only one part knows about another part at all (canvas
knows the bitmap model, because a sprite is a bitmap with a clock), and
that is one edge, in one direction.

```
                 ┌──────────┐
                 │   core   │   values, width, encoding, capabilities,
                 │   CR/NFR │   Cell, StyledText, Renderable, escape output
                 └────┬─────┘
          ┌───────────┼───────────┐
          ▼           ▼           ▼
     ┌────────┐  ┌────────┐  ┌──────────┐
     │  text  │  │ bitmap │  │  canvas  │
     │   TX   │  │ BM/AF  │◄─┤    CV    │   (sprites only)
     └────────┘  └────────┘  └──────────┘
```

## 2. The shared contract

Four core requirements carry the whole split. They are stated in
[core](core/requirements.md#27-layering-and-interchange--new-in-v4) and
summarised here because they are the thing to re-read before adding a
cross-part feature.

- **CR-41 — `Cell` is a core type.** One glyph, one `Style`, each
  channel independently absent. Absence means the same thing everywhere:
  leave what is underneath. A bitmap *resolves to* a grid of these
  cells; a canvas back buffer *is* a grid of them. That is a shared
  type, not a claim that paint is a memcpy.
- **CR-42 — `StyledText` and `Renderable` are the print interchange.**
  Implement `Renderable` and every *print* consumer can already use you
  (CR-44, CV-10, `Surface.blit(Renderable)` for opaque catalogue
  widgets). Overlay paint is not this contract: it composites core
  `Cell`s (BM-13, CV-94). That is allowed because both sides already
  depend on core — it is not a new part-to-part bridge.
- **CR-43 — the dependency graph is fixed and acyclic**, and a package
  scan enforces it (NFR-10).
- **CR-44 — core owns `Renderable → String`.** The text part is a
  facade over it, not the owner of it.

**Two output doors**, because `StyledText` cannot say “glyph absent, fg
present”, and a round-trip through `Renderable` would punch rectangular
holes in overlays (BM-3):

| Door | Contract | Transparency | Used for |
|---|---|---|---|
| **Print** | `Renderable` → `StyledText` → `String` (CR-44) | No — absent channels become space + default style | `System.out`, scrollback |
| **Paint** | `cellsAt(elapsed)` → core `Cell[]` → composite (CV-94) | Yes (BM-3) | `AsciiSprite`, any overlay |

```java
AsciiAnimation art = Art.load("/art/logo.art");
AsciiBitmap logo = art.frameAt(Duration.ZERO);

System.out.println(logo);   // print door: CR-44, like text-mode output
console.print(logo);        // print door: CV-10, into scrollback
console.place(new AsciiSprite(art), Rect.of(2, 1, 5, 3));  // paint door
```

`Surface.blit(Renderable)` stays for `Table` / `Panel`. It is **not**
the sprite path. The bitmap part imports neither `text` nor `canvas`
(BM-12). The text part is not involved at all.

The second consequence is **where animation lives**: the timing *maths*
is a pure function in bitmap (`frameAt` / `cellsAt(elapsed)`, BM-9,
BM-13), and the only thing with a clock is a canvas widget (CV-90).
Core has neither.

## 3. Where does a new requirement go?

| If it… | It belongs in |
|---|---|
| is about colour, width, graphemes, charsets, capabilities, or the `Renderable` print contract | core |
| is about a `String`-returning helper with no lifecycle | text |
| is about a grid of slots, a frame, a palette, `cellsAt`, or the `.art` file | bitmap |
| needs a clock, a thread, a terminal, a cursor, a key, or cell composite onto a Surface | canvas |
| needs two parts to agree | core, as a CR-41…CR-44-style rule |

If a requirement seems to need a new *print* edge in the graph of §1, it
is almost always a missing `Renderable` (CR-42). Overlay paint is the
Cell composite door (CV-94), not a third interchange.

## 4. Milestones

| # | Deliverable | State |
|---|---|---|
| M1 | Core model, capabilities, `Text` | done (v2 M0–M1) |
| M1b | `Styler`, `Snippets`, `Color` named constants (TX-10…TX-15, CR-40) | not started |
| M2 | Static `Renderable` catalogue (CR-18) | done (v2 M2) |
| M3 | Canvas engine: buffers, layout, blit, flush diff, canvas-above-scrollback routing, stream capture, restore paths, virtual terminal, `place`/`update`/`focus`/`remove` (CV-1…CV-38 except `handle.send`, CV-53…CV-63) | partially reusable from v2 M3 |
| M3b | Layering: move `Cell` to core, package-scan test (CR-41, CR-43) | not started |
| M4 | Event core (`WidgetId`, `WidgetEvent`, `handle.send`) + event-driven widget catalogue (CV-39…CV-44, CV-65, CV-66) | not started |
| M4b | Bitmap part: `AsciiBitmap`, `Palette`, `AsciiAnimation` (BM-1…BM-13) and the `.art` codec (AF-1…AF-8) | not started |
| M4c | `AsciiSprite` (CV-90…CV-94). Needs M4's `Tick` and M4b | not started |
| M5 | Content-side encoding robustness (CR-29…CR-36). Cross-part fixture CR-39 lives in canvas tests | not started |
| M6 | `KeyListener`, single-consumer forwarding, focus slot (CV-45…CV-52, CV-68, CV-69). No form widgets. Do not start until NFR-19 has passed | not started |
| M7 | Remaining NFR-14 platform rows (`SUPPORTED-TERMINALS.md`), including macOS | 1 of 10 |

M4b needs only core and M3 for the still-bitmap half; the animation half
is pure value code and needs nothing. M5 is deliberately before M6:
keyboard decoding (CR-37) depends on the charset resolution M5
introduces.

**NFR-19 start-gate (not a milestone):** the conhost rows of NFR-14 MUST
be verified for canvas mode **before M6 starts**.

## 5. Status

This workspace may be **docs only**. “v2 code” means the earlier branch,
not files here.

| Area | Requirements | State |
|---|---|---|
| Core model | CR-1 … CR-15 | v2 code |
| One colour type | CR-40 | v2: `Color` exists; named constants missing |
| Layering and interchange | CR-41 … CR-44 | **not started** (`Cell` still to be created in core) |
| Encoding robustness | CR-28 … CR-38 | v2: glyph side done; content side not started |
| Encoding fixture on canvas | CR-39 (canvas tests) | **not started** |
| Shared render contract | CR-16 … CR-20 | v2 code |
| Terminal safety | CR-21 … CR-27 | v2 code, for the strip engine |
| Text mode, static | TX-1 … TX-9 | v2 code |
| Text mode, `Styler` / `Snippets` | TX-10 … TX-15 | **not started** |
| Bitmap model | BM-1 … BM-13 | **not started** |
| `.art` format | AF-1 … AF-8 | **not started** |
| Canvas ownership, println, capture | CV-1 … CV-22 | v2 strip engine; needs canvas retarget |
| Canvas layout, widget model | CV-23 … CV-38 | **not started** |
| Widget events | CV-39 … CV-44 | **not started** |
| KeyListener | CV-45 … CV-52, CV-68, CV-69 | **not started** |
| Rendering | CV-53 … CV-59, CV-94 | v2 line-diff; cell diff and cell-composite not started |
| Degradation | CV-60 … CV-63 | v2 code |
| Widget catalogue | CV-64 … CV-67 | v2 static done; canvas widgets not started |
| Sprites | CV-90 … CV-94 | **not started** |
| Canvas NFRs | NFR-3 … NFR-6, NFR-19 | live in the canvas part |
| Platform verification | NFR-14 … NFR-18 | 1 of 10 rows (v2) |

## 6. ID history

v3 → v4. Nothing was renumbered except the bitmap block, which left the
`CV` range because it is no longer part of the canvas.

| v3 | v4 | Note |
|---|---|---|
| CR-1 … CR-40 | same, in [core](core/requirements.md) | CR-17/23/24/37 stripped of canvas citations; CR-18 is the print catalogue; **CR-39 moved to canvas tests** |
| — | CR-41 … CR-44 | `Cell` in core; `Renderable` is the **print** interchange; overlay paint is Cell composite |
| NFR-1, 2, 7–18 | [core](core/requirements.md) | whole-library / build |
| NFR-3, 4, 5, 6, 19 | [canvas](canvas/requirements.md) | canvas dump, frame budget, no-alloc, no-flicker, conhost gate |
| TX-1 … TX-15 | same, in [text](text/requirements.md) | unchanged |
| CV-1 … CV-69 | same, in [canvas](canvas/requirements.md) | CV-36 is catalogue blit; sprite paint is CV-94 |
| CV-70 … CV-75 | BM-1 … BM-6 | bitmap value, transparency, **print** via `Renderable`, text authoring, tiers |
| CV-87 | BM-7 | `generate` |
| CV-76 | BM-8 | animation as a value |
| CV-77 | BM-9 + BM-13 + CV-90 | **split**: `frameAt` / `cellsAt` in bitmap; the clock in canvas |
| CV-88 | BM-10 + CV-91 | **split**: cycle maths in bitmap; the `SetCycleOffset` event in canvas |
| CV-80 | BM-11 + CV-93 | **split**: value determinism; canvas dump assertion |
| CV-78, CV-79 | CV-91, CV-92 | playback events, dirty rule, non-TTY policy |
| — | BM-12 | bitmap depends on core only |
| — | BM-13 | `cellsAt(elapsed)` → core `Cell[]` |
| — | CV-94 | `Surface` composites `Cell[]` with per-channel absence |
| CV-81 … CV-86 | AF-1 … AF-6 | `.art`: one format, binary layout, 2-byte cell, glyph table, style table, bounded reading |
| CV-89 | AF-7 | text view / round trip |
| — | AF-8 | new: versioning rule, fail closed |
| CV-70 … CV-89 | *retired as CV IDs* | MUST NOT be reused in the canvas part |

## 7. Out of scope — whole library

Each part restates its own; these apply everywhere.

- Mouse support.
- Image or sixel rendering, and decoding an image file into characters.
- Command-line argument or option parsing.
- Forms and smart input (CV-67, CV-69).
- Multiple canvases per process; the alternate screen buffer.
- A second colour type, a second cell type, a second escape emitter, a
  second on-disk format.

## 8. Decisions (do not reopen)

Carried from v3 unless marked new.

- **Four parts, one JPMS module.** *(new)* The split is documentary and
  by package, not by artefact: there is still exactly one module
  `dev.consolekit`. CR-43 makes the graph acyclic, so extracting
  artefacts later would be mechanical — but it is not done now, and
  "which artefact does this go in" is not a question anyone has to
  answer yet. See §9 open question 4.
- **`Cell` is core.** *(new)* Same type, same meaning of absence
  (CR-41). Stored bitmaps are slots (AF-3); `cellsAt` resolves them.
- **Animation is a pure function of elapsed time** *(new)*, in bitmap;
  the clock is in canvas (BM-9, BM-13, CV-90).
- **`.art` is binary, two bytes per cell**, one format for a still and an
  animation; I/O is `Art.load` / `Art.write`; text is the authoring and
  review view, not a second file format (AF-1…AF-7).
- **Two output doors.** Print is `Renderable` (CR-42, BM-4, CR-44).
  Overlay paint is Cell composite (BM-13, CV-94). `blit(Renderable)` is
  the catalogue path, not the sprite path.
- **No alternate screen buffer.** The canvas lives at the bottom of the
  normal screen buffer; one restore path, `println` stays meaningful,
  the last frame survives in scrollback.
- **Application-level key delivery: yes**, single-consumer (CV-47), one
  `onKey` contract (CV-68), forms are a later library (CV-69).
- **Focus is a slot**, not a form navigator (CV-48).
- **Content-derived sizing: yes**, optional per widget (CV-26).
- **One colour type**, `Color` (CR-40).
- **JDK 21, Maven, no preview.** `IO.println` is not used.
- **Terminal size for layout is per-frame** (CV-27); `Capabilities.size()`
  is probe-time only (CR-12).
- **Text-mode width** is `COLUMNS` then 80, never JLine (TX-2, TX-15).
- **`logTo` is additional**, not instead-of (CV-13).
- **`WidgetId`** is minted at `place`, process-unique, never reused.
- **Percentage leftover** goes to the last percentage-or-fill widget on
  that axis (CV-26).
- **Tests:** JUnit 5; goldens at `src/test/resources/golden/`.

## 9. Open questions

1. **Event queue overflow detail (CV-43).** Coalesce-then-drop is
   specified; the bound, and whether `LogMessage` is ever droppable
   (probably not — drop `Tick`/`GraphAppend` first), need deciding when
   M4 starts.
2. **Nested widgets (CV-32).** Deferred until a widget actually needs
   them.
3. **Should `Snippets.print…` exist at all** (TX-14)?
4. **Do the parts ever become separate artefacts?** *(new)* Not now
   (§8). Revisit only if someone genuinely wants the bitmap part without
   JLine on the classpath — which is the one case the split would
   actually pay for.
5. **`.art` caps (AF-6).** The documented maxima for width, height,
   frame count and file size are not chosen yet; pick them when M4b
   starts, with the 80 × 24 full-canvas frame as the sizing reference.
6. **`cellsAt` allocation vs NFR-5.** Whether `cellsAt` may allocate, or
   must fill a caller-supplied buffer, is decided at M4b — not by
   pretending paint is a `Renderable` blit.
