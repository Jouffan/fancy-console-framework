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
  channel independently absent. A bitmap grid and a canvas back buffer
  are the same type, so blitting is a copy, not a conversion.
- **CR-42 — `StyledText` and `Renderable` are the only interchange.**
  Implement `Renderable` and every consumer can already use you. New
  cross-part plumbing is a design defect.
- **CR-43 — the dependency graph is fixed and acyclic**, and a package
  scan enforces it (NFR-10).
- **CR-44 — core owns `Renderable → String`.** The text part is a
  facade over it, not the owner of it.

**The worked consequence** — the one the split had to get right — is
that an `AsciiBitmap` is printable to an ordinary console *and* to the
canvas, through one contract, with no knowledge of either:

```java
AsciiBitmap logo = AsciiBitmap.load("/art/logo.art");

System.out.println(logo);   // CR-44: escapes applied, like text-mode output
console.print(logo);        // CV-10: into scrollback above the canvas
surface.blit(logo);         // CV-36: into a Rect, clipped
```

The bitmap part imports neither `text` nor `canvas` (BM-12). The canvas
part has no bitmap-specific blit path (CV-36). The text part is not
involved at all.

The second consequence is **where animation lives**: the timing *maths*
is a pure function in bitmap (`frameAt(elapsed)`, BM-9), and the only
thing with a clock is a canvas widget (CV-90). Core has neither.

## 3. Where does a new requirement go?

| If it… | It belongs in |
|---|---|
| is about colour, width, graphemes, charsets, capabilities, or the `Renderable` contract | core |
| is about a `String`-returning helper with no lifecycle | text |
| is about a grid of cells, a frame, a palette, or the `.art` file | bitmap |
| needs a clock, a thread, a terminal, a cursor, or a key | canvas |
| needs two parts to agree | core, as a CR-41…CR-44-style rule |

If a requirement seems to need a new edge in the graph of §1, it is
almost always a missing `Renderable` (CR-42), not a missing dependency.

## 4. Milestones

| # | Deliverable | State |
|---|---|---|
| M1 | Core model, capabilities, `Text` | done (v2 M0–M1) |
| M1b | `Styler`, `Snippets`, `Color` named constants (TX-10…TX-15, CR-40) | not started |
| M2 | Static `Renderable` catalogue (CR-18) | done (v2 M2) |
| M3 | Canvas engine: buffers, layout, blit, flush diff, canvas-above-scrollback routing, stream capture, restore paths, virtual terminal, `place`/`update`/`focus`/`remove` (CV-1…CV-38 except `handle.send`, CV-53…CV-63) | partially reusable from v2 M3 |
| M3b | Layering: move `Cell` to core, package-scan test (CR-41, CR-43) | not started |
| M4 | Event core (`WidgetId`, `WidgetEvent`, `handle.send`) + event-driven widget catalogue (CV-39…CV-44, CV-65, CV-66) | not started |
| M4b | Bitmap part: `AsciiBitmap`, `Palette`, `AsciiAnimation` (BM-1…BM-12) and the `.art` codec (AF-1…AF-8) | not started |
| M4c | `AsciiSprite` (CV-90…CV-93). Needs M4's `Tick` and M4b | not started |
| M5 | Content-side encoding robustness (CR-29…CR-36, CR-39) | not started |
| M6 | `KeyListener`, single-consumer forwarding, focus slot (CV-45…CV-52, CV-68, CV-69). No form widgets. Do not start until NFR-19 has passed | not started |
| M7 | Remaining NFR-14 platform rows (`SUPPORTED-TERMINALS.md`), including macOS | 1 of 10 |

M4b needs only core and M3 for the still-bitmap half; the animation half
is pure value code and needs nothing. M5 is deliberately before M6:
keyboard decoding (CR-37) depends on the charset resolution M5
introduces.

**NFR-19 start-gate (not a milestone):** the conhost rows of NFR-14 MUST
be verified for canvas mode **before M6 starts**.

## 5. Status

| Area | Requirements | State |
|---|---|---|
| Core model | CR-1 … CR-15 | implemented |
| One colour type | CR-40 | `Color` exists; named constants missing |
| Layering and interchange | CR-41 … CR-44 | **not started** (`Cell` still to be created in core) |
| Encoding robustness | CR-28 … CR-39 | glyph side done, content side not started |
| Shared render contract | CR-16 … CR-20 | implemented |
| Terminal safety | CR-21 … CR-27 | implemented for current code |
| Text mode, static | TX-1 … TX-9 | implemented |
| Text mode, `Styler` / `Snippets` | TX-10 … TX-15 | **not started** |
| Bitmap model | BM-1 … BM-12 | **not started** |
| `.art` format | AF-1 … AF-8 | **not started** |
| Canvas ownership, println, capture | CV-1 … CV-22 | implemented for the strip engine; needs canvas retarget |
| Canvas layout, widget model | CV-23 … CV-38 | **not started** |
| Widget events | CV-39 … CV-44 | **not started** |
| KeyListener | CV-45 … CV-52, CV-68, CV-69 | **not started** |
| Rendering | CV-53 … CV-59 | strip-based version implemented; cell diff not started |
| Degradation | CV-60 … CV-63 | implemented |
| Widget catalogue | CV-64 … CV-67 | static done; canvas widgets not started |
| Sprites | CV-90 … CV-93 | **not started** |
| Platform verification | NFR-14 … NFR-19 | 1 of 10 rows |

## 6. ID history

v3 → v4. Nothing was renumbered except the bitmap block, which left the
`CV` range because it is no longer part of the canvas.

| v3 | v4 | Note |
|---|---|---|
| CR-1 … CR-40 | same, in [core](core/requirements.md) | CR-17 and CR-24 reworded; CR-18 generalised from "both modes" to "every consumer" |
| — | CR-41 … CR-44 | new: `Cell` in core, `Renderable` as the only interchange, the dependency rule, core owns `Renderable → String` |
| NFR-1 … NFR-19 | same, in core | NFR-7, NFR-10, NFR-11 extended to four parts |
| TX-1 … TX-15 | same, in [text](text/requirements.md) | unchanged |
| CV-1 … CV-69 | same, in [canvas](canvas/requirements.md) | unchanged except CV-10, CV-36, CV-40, CV-64, CV-65 which now name the bitmap contract instead of describing it |
| CV-70 … CV-75 | BM-1 … BM-6 | bitmap value, transparency, `Renderable` output, text authoring, tiers |
| CV-87 | BM-7 | `generate` |
| CV-76 | BM-8 | animation as a value |
| CV-77 | BM-9 + CV-90 | **split**: pure `frameAt(elapsed)` in bitmap; the thing with a clock in canvas |
| CV-88 | BM-10 + CV-91 | **split**: cycle maths in bitmap; the `SetCycleOffset` event in canvas |
| CV-80 | BM-11 + CV-93 | **split**: value determinism; canvas dump assertion |
| CV-78, CV-79 | CV-91, CV-92 | playback events, dirty rule, non-TTY policy |
| — | BM-12 | new: bitmap depends on core only |
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
- **`Cell` is core.** *(new)* Both the bitmap grid and the canvas back
  buffer use it (CR-41).
- **Animation is a pure function of elapsed time** *(new)*, in bitmap;
  the clock is in canvas (BM-9, CV-90).
- **`.art` is binary, two bytes per cell**, one format for a still and an
  animation; text is the authoring and review view, not a second file
  format (AF-1…AF-7).
- **Two output surfaces, one contract.** A bitmap prints and blits
  through `Renderable` (CR-42, BM-4).
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
