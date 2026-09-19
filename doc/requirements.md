# ConsoleKit — Requirements map (v5, four parts)

v4 split the single requirements document into four parts. **v5 closes
the seams v4 left open**: the shape of `Cell`, who owns palette tables
and cycles, how events are addressed, what `Tick` is, how `Ctrl-C`
behaves, and how the layering is mechanically enforced. No requirement
was weakened. v4 IDs keep their numbers; v5 amends some and adds a few
(§6).

This document is the map. It holds only what is *between* the parts:
the layering rule, the shared contract, milestones, status, and the ID
history. Everything normative lives in a part.

| Part | Document | Prefix | Package prefix | Depends on |
|---|---|---|---|---|
| **Core** — the commons | [core/requirements.md](core/requirements.md) | `CR`, `NFR` | `dev.consolekit.core` | — |
| **Text** — colour a string | [text/requirements.md](text/requirements.md) | `TX` | `dev.consolekit.text` | core |
| **Bitmap** — X × Y coloured characters | [bitmap/requirements.md](bitmap/requirements.md) | `BM` | `dev.consolekit.bitmap` | core |
| ↳ file format | [bitmap/art-format.md](bitmap/art-format.md) | `AF` | | core |
| **Canvas** — own the terminal | [canvas/requirements.md](canvas/requirements.md) | `CV` | `dev.consolekit.canvas` | core, bitmap |

Architecture is split the same way: [architecture.md](architecture.md)
is its map, with a document per part.

**This is a specification set.** An earlier "v2" code branch exists and
is a *reference*, not a constraint: where v2 code and these documents
disagree, the documents win. There are no external users, so package
names and signatures are free to change to match the spec.

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
                 │   CR/NFR │   Cell, CellBuffer, StyledText, Renderable,
                 └────┬─────┘   Rendering (escape output)
          ┌───────────┼───────────┐
          ▼           ▼           ▼
     ┌────────┐  ┌────────┐  ┌──────────┐
     │  text  │  │ bitmap │◄─┤  canvas  │   (sprites only)
     │   TX   │  │ BM/AF  │  │    CV    │
     └────────┘  └────────┘  └──────────┘
```

**A part is a package prefix** (CR-43). `dev.consolekit.tools` (`Probe`,
demos) is classified as an *application*: it may reference every part,
and nothing may reference it.

## 2. The shared contract

Six core requirements carry the whole split. They are stated in
[core §2.7](core/requirements.md#27-layering-and-interchange) and
summarised here because they are the thing to re-read before adding a
cross-part feature.

- **CR-41 — `Cell` is a core value.** One grapheme, one `Style`. The
  glyph, the foreground and the background are each independently
  **absent**; absence means the same thing everywhere: leave what is
  underneath. Attributes travel with the glyph. A colour channel has
  three states — absent, `Color.DEFAULT` (explicit terminal default,
  CR-40), or an explicit colour.
- **CR-46 — `CellBuffer` is the core grid of cells, and the one
  compositor.** A canvas back buffer *is* a `CellBuffer`; a bitmap
  *resolves into* one. `CellBuffer.composite` is the only implementation
  of the absence rule, so "the same thing everywhere" is code, not
  convention.
- **CR-42 — `StyledText` and `Renderable` are the print interchange.**
  Implement `Renderable` and every *print* consumer can already use you.
  Overlay paint is not this contract: it composites `CellBuffer`s
  (BM-13, CV-94). That is allowed because both sides already depend on
  core — it is not a new part-to-part bridge.
- **CR-43 — the dependency graph is fixed, acyclic, and expressed as
  package prefixes**, so one package-scan rule enforces it (NFR-10).
- **CR-44 — core owns `Renderable → String`**, through the named API
  `core.Rendering`. The text part is a facade over it, not the owner.
- **CR-45 — ambient width** (for `toString()` and width-less snippets)
  is `COLUMNS` if it parses as a positive integer, else 80. Never JLine.

**Two output doors**, because `StyledText` cannot say "glyph absent, fg
present", and a round-trip through `Renderable` would punch rectangular
holes in overlays (BM-3):

| Door | Contract | Transparency | Used for |
|---|---|---|---|
| **Print** | `Renderable` → `StyledText` → `String` (CR-44) | No — absent channels become space + default style | `System.out`, scrollback, `Surface.blit` |
| **Paint** | `cellsAt(elapsed, ctx, CellBuffer)` → `CellBuffer.composite` (BM-13, CR-46, CV-94) | Yes (BM-3) | `AsciiSprite`, any overlay |

```java
AsciiAnimation art = Art.loadResource("/art/logo.art");
AsciiBitmap logo = art.frame(0);

System.out.println(logo);   // print door: CR-44, like text-mode output
console.print(logo);        // print door: CV-10, into scrollback

AsciiSprite sprite = new AsciiSprite(art);
console.place(sprite, Rect.of(2, 1, 5, 3));   // paint door
sprite.play();
```

`Surface.blit(Renderable)` stays for `Table` / `Panel`. It is **not**
the sprite path. The bitmap part imports neither `text` nor `canvas`
(BM-12). The text part is not involved at all.

The second consequence is **where animation lives**: the timing *maths*
is a pure function in bitmap (`frameIndexAt` / `cycleOffsetAt` /
`cellsAt`, BM-9, BM-10, BM-13, BM-15), and the only thing with a clock
is the canvas render loop, which hands elapsed time to widgets on `Tick`
(CV-95). Core has neither.

## 3. Where does a new requirement go?

| If it… | It belongs in |
|---|---|
| is about colour, width, graphemes, charsets, capabilities, `Cell` / `CellBuffer`, or the `Renderable` print contract | core |
| is about a `String`-returning helper with no lifecycle | text |
| is about a grid of slots, a frame, a palette, `cellsAt`, or the `.art` file | bitmap |
| needs a clock, a thread, a terminal, a cursor, a key, or a `Surface` | canvas |
| needs two parts to agree | core, as a CR-41…CR-46-style rule |

If a requirement seems to need a new *print* edge in the graph of §1, it
is almost always a missing `Renderable` (CR-42). Overlay paint is the
cell-composite door (CR-46, CV-94), not a third interchange.

## 4. Milestones

| # | Deliverable | Needs |
|---|---|---|
| M1 | Core: model, capabilities, `Color` constants + `DEFAULT`, `Cell`, `CellBuffer`, `Rendering`, ambient width, **CR-43 package-scan test** (CR-1…CR-27, CR-40…CR-46 glyph side, NFR-10) | — |
| M1b | Text: `Text`, `Styler`, `Snippets` (TX-1…TX-15) | M1 |
| M2 | Static `Renderable` catalogue (CR-18) | M1 |
| M2b | Bitmap part: `Palette`, `AsciiBitmap`, `AsciiAnimation` (BM-1…BM-15) and the `.art` codec (AF-1…AF-8) | M1 only — may be built in parallel with M3 |
| M3 | Canvas engine: buffers, layout, blit, composite, flush diff, canvas-above-scrollback routing, stream capture, restore paths incl. SIGINT, virtual terminal, `place` / `focus` / `remove`, engine `Tick`, and the **minimal** event path (`handle.send`, `SetText`) (CV-1…CV-38, CV-39, CV-44, CV-53…CV-63, CV-94…CV-96) | M1, M2 |
| M4 | Full event vocabulary, coalescing and overflow policy, `EventRecorder` / `EventReplayer`, the event-driven widget catalogue (CV-40…CV-43, CV-65, CV-66) | M3 |
| M4c | `AsciiSprite` (CV-90…CV-93) | M2b, M4 |
| M5 | Content-side encoding robustness (CR-29…CR-36); BM-6's substitution half lands here. Cross-part fixture CR-39 lives in canvas tests | M3 |
| — | **NFR-19 start-gate (not a milestone):** conhost rows of NFR-14 verified for canvas mode **before M6 starts** | M3 |
| M6 | `KeyListener`, single-consumer forwarding, focus slot (CV-45…CV-52, CV-68, CV-69). No form widgets | M5, NFR-19 |
| M7 | Remaining NFR-14 platform rows (`SUPPORTED-TERMINALS.md`), including macOS | M6 |

M5 is deliberately before M6: keyboard decoding (CR-37) depends on the
charset resolution M5 introduces. v4's M3b is folded into M1 (the scan is
trivial once parts are package prefixes) and v4's M4b is renamed M2b
because it no longer waits for canvas.

## 5. Status

Specification phase. "v2 reference" means the earlier branch has code
that covers the area under the *old* package layout; it must be moved
and re-verified against v5, not assumed correct.

| Area | Requirements | State |
|---|---|---|
| Core model | CR-1 … CR-15 | v2 reference |
| One colour type, `DEFAULT` | CR-40 | v2: `Color` exists; constants and `DEFAULT` missing |
| Layering and interchange | CR-41 … CR-46 | **not started** |
| Encoding robustness | CR-28 … CR-38 | v2: glyph side; content side not started |
| Encoding fixture on canvas | CR-39 (canvas tests) | **not started** |
| Shared render contract | CR-16 … CR-20 | v2 reference |
| Terminal safety | CR-21 … CR-27 | v2 reference, for the strip engine |
| Text mode, static | TX-1 … TX-9 | v2 reference |
| Text mode, `Styler` / `Snippets` | TX-10 … TX-15 | **not started** |
| Bitmap model | BM-1 … BM-15 | **not started** |
| `.art` format | AF-1 … AF-8 | **not started** |
| Canvas ownership, println, capture | CV-1 … CV-22 | v2 strip engine; needs canvas retarget |
| Canvas layout, widget model | CV-23 … CV-38, CV-96 | **not started** |
| Widget events, `Tick` | CV-39 … CV-44, CV-95 | **not started** |
| KeyListener | CV-45 … CV-52, CV-68, CV-69 | **not started** |
| Rendering | CV-53 … CV-59, CV-94 | v2 line-diff; cell diff and composite not started |
| Degradation | CV-60 … CV-63 | v2 reference |
| Widget catalogue | CV-64 … CV-67 | v2 static done; canvas widgets not started |
| Sprites | CV-90 … CV-93 | **not started** |
| Canvas NFRs | NFR-3 … NFR-6, NFR-19 | live in the canvas part |
| Platform verification | NFR-14 … NFR-18 | 1 of 10 rows (v2) |

## 6. ID history

### v4 → v5

Nothing was renumbered. Amended IDs keep their number; the note says
what changed.

| ID | Change |
|---|---|
| CR-12, CR-22 | probe-time size is environment-derived; the JLine toucher lives in canvas |
| CR-40 | adds `Color.DEFAULT` (explicit terminal default) |
| CR-41 | `Cell` shape fixed: grapheme + `Style`; three-state colour channels; attributes travel with the glyph |
| CR-43 | parts are package prefixes; `tools` is an application; `core.internal` rule |
| CR-44 | names the API (`core.Rendering`); every shipped `toString()` delegates to it |
| — → CR-45 | ambient width rule, moved up from TX-15 |
| — → CR-46 | `CellBuffer` and the single compositor |
| TX-15 | now cites CR-45 |
| BM-5, BM-7 | palette declaration order / first-appearance order is canonical; 255 limit is a construction error |
| BM-8 | frames are normalised to bounds at construction; anchor meaning; durations ≥ 1 ms |
| BM-9 | `frameIndexAt` is the primitive; wrap and negative semantics stated |
| BM-10 | `cycleOffsetAt(group, elapsed)`; rotation direction stated |
| BM-13 | `cellsAt(elapsed, ctx, CellBuffer out)`; v4 open question 6 closed |
| — → BM-14 | `Palette` owns the tables and the cycle groups; frames share one palette |
| — → BM-15 | index-addressed primitive `cells(frameIndex, offsets, ctx, out)` |
| AF-3 … AF-6, AF-8 | byte-exact colour spec, anchor enum, alternate sentinel, cycles moved after the style table, caps chosen, trailer grammar |
| CV-35, CV-38 | `Surface` and `WidgetHandle` signatures; `update()` removed |
| CV-37, CV-53 | dirty gates *frames*, not widgets |
| CV-39, CV-40, CV-43 | events are payloads, the queue holds the envelope; per-type coalescing; back-pressure |
| CV-49 | `Ctrl-C` stays a signal; `Cancelled` removed; `onCancel` hook |
| CV-66 | `TaskHandle` and task-scoped events |
| CV-90 … CV-92 | sprite state model; `SetCycleOffset(group, n)` |
| — → CV-95 | `Tick` is engine-synthesised, never queued |
| — → CV-96 | widget attach lifecycle; object API lives on the widget |
| NFR-5 | restated against cached paint |
| M3b, M4b | folded into M1; renamed M2b |

### v3 → v4

| v3 | v4 | Note |
|---|---|---|
| CR-1 … CR-40 | same, in [core](core/requirements.md) | CR-17/23/24/37 stripped of canvas citations; CR-18 is the print catalogue; **CR-39 moved to canvas tests** |
| — | CR-41 … CR-44 | `Cell` in core; `Renderable` is the **print** interchange; overlay paint is cell composite |
| NFR-1, 2, 7–18 | [core](core/requirements.md) | whole-library / build |
| NFR-3, 4, 5, 6, 19 | [canvas](canvas/requirements.md) | canvas dump, frame budget, no-alloc, no-flicker, conhost gate |
| TX-1 … TX-15 | same, in [text](text/requirements.md) | unchanged |
| CV-1 … CV-69 | same, in [canvas](canvas/requirements.md) | CV-36 is catalogue blit; sprite paint is CV-94 |
| CV-70 … CV-75 | BM-1 … BM-6 | bitmap value, transparency, **print** via `Renderable`, text authoring, tiers |
| CV-87 | BM-7 | `generate` |
| CV-76 | BM-8 | animation as a value |
| CV-77 | BM-9 + BM-13 + CV-90 | **split**: frame selection / `cellsAt` in bitmap; the clock in canvas |
| CV-88 | BM-10 + CV-91 | **split**: cycle maths in bitmap; the `SetCycleOffset` event in canvas |
| CV-80 | BM-11 + CV-93 | **split**: value determinism; canvas dump assertion |
| CV-78, CV-79 | CV-91, CV-92 | playback events, dirty rule, non-TTY policy |
| — | BM-12 | bitmap depends on core only |
| CV-81 … CV-86 | AF-1 … AF-6 | `.art`: one format, binary layout, 2-byte cell, glyph table, style table, bounded reading |
| CV-89 | AF-7 | text view / round trip |
| — | AF-8 | versioning rule, fail closed |
| CV-70 … CV-89 | *retired as CV IDs* | MUST NOT be reused in the canvas part |

## 7. Out of scope — whole library

Each part restates its own; these apply everywhere.

- Mouse support.
- Image or sixel rendering, and decoding an image file into characters.
- Command-line argument or option parsing.
- Forms and smart input (CV-67, CV-69).
- Multiple canvases per process; the alternate screen buffer.
- A second colour type, a second cell type, a second cell grid, a second
  compositor, a second escape emitter, a second on-disk format.

## 8. Decisions (do not reopen)

- **Four parts, one JPMS module, a part is a package prefix.** *(v5)*
  The split is by package, not by artefact. CR-43 is one mechanical rule
  over prefixes. See §9 open question 4.
- **`Cell` and `CellBuffer` are core.** *(v5)* One value, one grid, one
  compositor (CR-41, CR-46). Stored bitmaps are slots (AF-3); `cellsAt`
  resolves them into a caller-supplied `CellBuffer`.
- **Three-state colour channels; attributes travel with the glyph.**
  *(v5)* Absent / `Color.DEFAULT` / explicit (CR-40, CR-41).
- **Animation is a pure function of elapsed time**, in bitmap; the clock
  is in the canvas loop (BM-9, BM-13, CV-95). Selection wraps; holding
  the last frame of a non-looping sprite is canvas state (CV-90).
- **The palette owns the tables and the cycles; an animation has one
  palette.** *(v5)* No table merging, canonical order is declaration
  order, the file is "palette + frames" (BM-14).
- **`.art` is binary, two bytes per cell**, one format for a still and an
  animation; I/O is `Art.load` / `Art.loadResource` / `Art.write`; text
  is the authoring and review view, not a second file format
  (AF-1…AF-7). Caps: 512 × 256, 1024 frames, 4 Mi cells, 16 MiB (AF-6).
- **Two output doors.** Print is `Renderable` (CR-42, BM-4, CR-44).
  Overlay paint is cell composite (BM-13, CR-46, CV-94).
  `blit(Renderable)` is the catalogue path, not the sprite path.
- **Events are payloads; the queue holds the envelope.** *(v5)* The
  `WidgetId` is on the envelope, not in the record (CV-39).
- **`Tick` is engine-synthesised and never queued.** *(v5)* It cannot be
  coalesced or dropped, so sprite time cannot drift (CV-95).
- **Coalescing is declared per event type**, `LATEST` or `NEVER`;
  `NEVER` events apply back-pressure rather than being dropped (CV-43).
- **The object API lives on the widget**, bound by `attached(handle)`;
  `WidgetHandle.update()` does not exist (CV-96).
- **Dirty gates frames, not widgets.** *(v5)* If a frame runs, every
  widget paints; the cell diff keeps the bytes minimal (CV-37, CV-53).
- **`Ctrl-C` stays a signal.** *(v5)* Raw mode keeps signal generation
  on; restore runs on the SIGINT path; there is no `Cancelled`
  exception (CV-49).
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
  is probe-time, environment-derived and diagnostic only (CR-12).
- **Ambient width** is `COLUMNS` then 80, never JLine (CR-45, TX-15).
- **`logTo` is additional**, not instead-of (CV-13).
- **`WidgetId`** is minted at `place`, process-unique, never reused.
- **Percentage leftover** goes to the last percentage-or-fill widget on
  that axis (CV-26).
- **Tests:** JUnit 5; goldens at `src/test/resources/golden/`.

## 9. Open questions

1. **Event queue bound (CV-43).** The policy is decided (coalesce
   `LATEST`, drop oldest `LATEST`, back-pressure for `NEVER`); only the
   numeric bound is open. Pick it when M4 starts.
2. **Nested widgets (CV-32).** Deferred until a widget actually needs
   them.
3. **Should `Snippets.print…` exist at all** (TX-14)?
4. **Do the parts ever become separate artefacts?** Not now (§8).
   Revisit only if someone genuinely wants the bitmap part without JLine
   on the classpath. v5 makes that mechanical: JLine is referenced only
   under `dev.consolekit.canvas`.
5. *(closed in v5)* `.art` caps — AF-6.
6. *(closed in v5)* `cellsAt` allocation — BM-13: caller-supplied
   `CellBuffer`.
7. **`SetContent(Renderable)` over a pipe (CV-41).** In-process only for
   now; the serialised form (pre-rendered lines at a declared width?) is
   decided if and when the pipe driver is built.
