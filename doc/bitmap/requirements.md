# ConsoleKit Bitmap — Requirements (`BM`)

Part 3 of 4. Package prefix `dev.consolekit.bitmap`. Depends on
[core](../core/requirements.md) only. The file format is specified
separately in [art-format.md](art-format.md) (`AF`). See
[the map](../requirements.md).

**X × Y coloured characters, as a value.** A bitmap is a grid of slots
that resolve to core `Cell`s; an animation is an ordered list of bitmaps
with durations. Nothing in this part owns a terminal, a thread or a
clock. Print is `Renderable` (CR-42); overlay paint is `cellsAt` into a
core `CellBuffer` (BM-13). This part does not know which consumer will
call which.

`MUST` / `SHOULD` / `MAY` are RFC 2119. IDs `BM-1…BM-13` replace
`CV-70…CV-89` from v3; BM-14 and BM-15 are new in v5. The map has the
table.

---

## 1. Terminology

**Palette** — the glyph table, the style table, the cycle groups, and —
at authoring time only — the map from a source character to a (glyph
slot, style slot) pair (BM-14).
**Slot** — an index into a palette table, as stored: cells reference
slots, not styles (AF-3), which is what makes colour cycling a rotation.
Slot `0` is *absent* in both tables.
**Bitmap** — an immutable `w × h` grid of glyph-slot + style-slot pairs
plus the palette they index. Resolves to core `Cell`s (CR-41). A value,
not a canvas and not an image (§5).
**Frame** — one bitmap in an animation, with a duration.
**Animation** — an ordered, non-empty list of frames sharing one
palette, plus an anchor. A bitmap is a one-frame animation.
**Cycle group** — an ordered list of style slots plus a period; the
styles rotate through the slots over time (BM-10).

---

## 2. The bitmap value

- **BM-1** `AsciiBitmap` MUST be an immutable `w × h` grid of glyph-slot
  + style-slot pairs plus the `Palette` they index (BM-14, AF-3) —
  X × Y coloured characters. Dimensions MUST be fixed at construction
  and MUST be at least 1 × 1. A bitmap is a value, not a mutable
  framebuffer; an edit produces a new bitmap. Equality is dimensions +
  slots + palette.
- **BM-2** Every glyph MUST be exactly **one code point after NFC** that
  occupies exactly **one display column** as measured by CR-6. Anything
  else — wide, combining, zero-width, a multi-code-point cluster, a
  control character (CR-9) — MUST be rejected **at construction**, not at
  paint: the grid is positional, and a two-column glyph at `(x, y)`
  shifts every cell to its right. Validation runs once per glyph-table
  entry, not per cell.
- **BM-3** Cells MUST support per-channel transparency — glyph,
  foreground and background independently absent (CR-41) — so a bitmap
  drawn over other content leaves what is underneath instead of punching
  a rectangular hole. Glyph slot `0` is the absent glyph. Style slot `0`
  is `Style.NONE`; any other style entry may leave either colour channel
  unset. `Color.DEFAULT` in a style entry is opaque (CR-40). Compositing
  itself is core's (CR-46); this part MUST NOT implement it.
- **BM-4** `AsciiBitmap` MUST be a `Renderable` (CR-16, CR-42). That is
  its **print** contract, not its only output: it prints to an ordinary
  console as a `String` with escapes applied exactly like text-mode
  output (CR-44), and prints into scrollback, with no code in this part
  that is aware of either. Where there is nothing underneath, the print
  door MUST resolve absent channels to a space and the default style,
  and MUST render at **cycle offset 0** — a `Renderable` has no elapsed
  time. `toString()` MUST delegate to `Rendering` (CR-44). Overlay paint
  is BM-13, not this contract. A `print()` or `paint(Surface)` method on
  the bitmap itself is a defect.
- **BM-5** Bitmaps MUST be **authorable** as text: a block of rows plus a
  palette, constructible from a `String`. A palette entry maps one source
  character to a `Style` and, optionally, to a different emitted glyph —
  which is what lets a pattern be written as digits that all paint `#`,
  and what keeps the source one character per cell (BM-2). Rows shorter
  than the declared width MUST be padded with fully transparent cells;
  rows longer MUST be an error; a source character not in the palette
  MUST be an error. Text is the authoring and review surface; the stored
  form is the binary `.art` file (AF-1…AF-8).
- **BM-6** Bitmap glyphs MUST pass through the same tier and encoding
  path as every other glyph (CR-7, CR-29…CR-32). A palette MAY carry
  per-tier alternates (`CP437` / `ASCII`) per glyph-table entry; when
  none is supplied the substitution policy applies, and because CR-30
  substitution is width-preserving the grid still lines up. Tier
  alternates MUST satisfy BM-2 and MUST change glyphs only, never
  geometry. Resolution happens per table entry (at most 255), never per
  cell, and MAY be memoised per capabilities. Colour downgrade is the
  escape emitter's job, not this part's.
- **BM-7** `AsciiBitmap` MUST be constructible from a function of
  position: `generate(w, h, (x, y) -> Cell)`. The function MUST be
  evaluated exactly once per cell **at construction**, in row-major
  order, and MUST NOT be retained, so the result stays an immutable
  value (BM-1) and painting stays a pure blit. The implicit palette is
  built in **first-appearance order**. More than 255 distinct glyphs or
  255 distinct styles MUST throw at construction — a smooth RGB gradient
  larger than 255 cells does not fit this model, and that is a
  documented limit, not a defect. A per-paint cell callback is the thing
  this requirement exists to prevent.
- **BM-14** `Palette` MUST be an immutable value that **owns** the glyph
  table, the style table and the cycle groups.
  - Table order is **declaration order** (first declaration of each
    distinct glyph / style), which is the canonical order the
    deterministic writer needs (AF-2). Unused entries are kept.
  - A cycle group is declared on the palette; it lists at least two
    distinct, non-zero style slots; a slot MUST belong to at most one
    group.
  - The source-character map exists for authoring only and is **not**
    part of palette equality: equality is tables + cycle groups.
  - Every frame of an animation MUST share an **equal** palette (BM-8).
    There is no table merging, so there is no late "too many styles"
    failure, and the `.art` file is exactly *palette + frames*.

## 3. Animation, as a value

The library that owns animation *timing* does not own a clock. Everything
here is a pure function of an elapsed `Duration`; the thing that has a
clock is the canvas render loop (CV-95), and it lives in the other part.

- **BM-8** `AsciiAnimation` MUST be an ordered, non-empty list of frames,
  each with a duration, plus an anchor and a default duration. A single
  bitmap MUST be representable as a one-frame animation
  (`AsciiAnimation.of(bitmap)`), so there is one model and one file
  format (AF-1), not two.
  - All frames MUST share an equal palette (BM-14); otherwise
    construction MUST throw.
  - Frames MAY be *supplied* in different sizes. Construction MUST
    **normalise** them: the animation's bounds are the maximum over the
    supplied frames, and each frame is placed inside the bounds per the
    anchor and padded with fully transparent cells (BM-3). After
    construction every frame has the animation's `width × height`.
  - The **anchor** (nine positions, `TOP_LEFT` … `BOTTOM_RIGHT`) has two
    uses: that normalisation, and — for a consumer — how the bounds sit
    inside a larger or smaller target rectangle.
  - Every effective duration (a frame's own, else the default) MUST be
    at least 1 ms, so the total is never zero.
- **BM-9** Frame selection MUST be a **pure function of elapsed time**:
  `frameIndexAt(Duration elapsed)`, total, allocation-free and
  deterministic; `frameAt(elapsed)` is `frame(frameIndexAt(elapsed))`.
  - Elapsed time at or past the total duration **wraps** (`elapsed mod
    total`). Negative elapsed is treated as zero. A one-frame animation
    always selects index 0.
  - Holding the last frame of a play-once sprite is *consumer state*
    (CV-90), not a second selection function.
  - It MUST NOT read a clock, sleep, or start a thread. Because it is a
    function of absolute elapsed time rather than a step counter, a
    consumer that falls behind **skips** frames instead of queueing
    them, and playback speed is independent of any refresh rate.
- **BM-10** An animation MUST support **palette cycling** through its
  palette's cycle groups (BM-14).
  - `cycleOffsetAt(int group, Duration elapsed)` MUST be
    `floor(elapsed / period) mod length`, the same
    pure-function-of-elapsed-time shape as BM-9, with negative elapsed
    treated as zero.
  - At offset `k`, the slot at group position `i` MUST resolve to the
    style declared at position `(i + k) mod length`.
  - Resolution through the offset MUST happen at resolve time, not by
    rewriting cells. This is what makes an N-colour moving pattern
    **one** frame instead of N: a 10 × 10 field of `#` with diagonal
    stripes is one frame, a 1-entry glyph table, a 4-entry style table
    and one cycle group.
- **BM-11** The model MUST be assertable without a terminal, without a
  canvas and without sleeping: the same elapsed value MUST always select
  the same frame and the same cycle offsets, and the text view (AF-7)
  and `CellBuffer.dump()` MUST be usable as the assertion surfaces.
- **BM-12** This part MUST NOT depend on the canvas or text parts
  (CR-43). It has no `Widget`, no `Tick`, no `Surface`, no `println`. A
  requirement that cannot be satisfied without one of those belongs in
  the canvas part.
- **BM-13** `AsciiAnimation` MUST expose the **paint door**:
  `cellsAt(Duration elapsed, RenderContext ctx, CellBuffer out)`. It
  fully overwrites `out` — which MUST be exactly the animation's bounds
  — with the cells of the selected frame resolved through the current
  cycle offsets, **with per-channel absence intact** (BM-3). `ctx`
  supplies glyph tier and encoding (BM-6) and nothing else. It MUST be a
  pure function of its arguments. It MUST NOT import canvas. A
  convenience `cellsAt(elapsed, ctx)` returning a new `CellBuffer` MAY
  exist for tests. A still bitmap is the one-frame case of the same
  call.
- **BM-15** The elapsed-time calls MUST be conveniences over an
  **index-addressed primitive**:
  `cells(int frameIndex, int[] cycleOffsets, RenderContext ctx,
  CellBuffer out)`, with `frameCount()`, `cycleGroupCount()`,
  `totalDuration()`, `frame(int)`, `width()`, `height()`, `anchor()` and
  `palette()` alongside. `cycleOffsets` has one entry per group, each
  taken modulo that group's length; `null` means all zero. The primitive
  MUST NOT allocate, which makes it the path a per-frame consumer uses
  (NFR-5); it is also what lets a consumer pin a frame or an offset
  (CV-91) without this part knowing what "pinned" means.

---

## 4. Usage sketches

Authoring and the print door (BM-4, BM-5). Persistence is `Art` only:

```java
AsciiBitmap logo = AsciiBitmap.parse("""
        .###.
        #.o.#
        .###.
        """,
        Palette.of('#', Style.fg(Color.BRIGHT_CYAN),
                   'o', Style.fg(Color.RED).bold(),
                   '.', Palette.TRANSPARENT));

System.out.println(logo);        // print door: ordinary console (CR-44)
console.print(logo);             // print door: into scrollback
Art.write(Path.of("art/logo.art"), AsciiAnimation.of(logo));
```

Per-channel transparency and an opaque default background (BM-3):

```java
Palette glass = Palette.builder()
        .entry('~', Palette.NO_GLYPH, Style.bg(Color.BLUE))   // tint only
        .entry('#', Style.fg(Color.WHITE))                    // glyph + fg, bg kept
        .entry('_', ' ', Style.bg(Color.DEFAULT))             // erase to default
        .transparent('.')
        .build();
```

Generated rather than typed (BM-7):

```java
Style[] ramp = { Style.fg(Color.RED),   Style.fg(Color.YELLOW),
                 Style.fg(Color.GREEN), Style.fg(Color.CYAN) };

AsciiBitmap field = AsciiBitmap.generate(10, 10,
        (x, y) -> Cell.of('#', ramp[(x + y) % ramp.length]));
```

A 10 × 10 field of `#` with stripes travelling diagonally: **one** frame
plus one cycle group (BM-5, BM-10, BM-14). The digits are palette
characters that all emit `#`; rotating the group of style slots moves
the stripes:

```java
Palette stripes = Palette.builder()
        .entry('1', '#', Style.fg(Color.RED))
        .entry('2', '#', Style.fg(Color.YELLOW))
        .entry('3', '#', Style.fg(Color.GREEN))
        .entry('4', '#', Style.fg(Color.CYAN))
        .cycle(Duration.ofMillis(80), '1', '2', '3', '4')
        .build();

AsciiAnimation band = AsciiAnimation.of(AsciiBitmap.parse("""
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

band.frameIndexAt(Duration.ofMillis(240));      // 0   — pure (BM-9)
band.cycleOffsetAt(0, Duration.ofMillis(240));  // 3   — pure (BM-10)
Art.write(Path.of("art/stripes.art"), band);
```

Several frames, one palette (BM-8, BM-14):

```java
AsciiAnimation spinner = AsciiAnimation.builder()
        .anchor(Anchor.CENTER)
        .defaultDuration(Duration.ofMillis(100))
        .frame(AsciiBitmap.parse("|", p))
        .frame(AsciiBitmap.parse("/", p))
        .frame(AsciiBitmap.parse("-", p), Duration.ofMillis(150))
        .frame(AsciiBitmap.parse("\\", p))
        .build();
```

The paint door, asserted with no terminal and no sleeping (BM-11,
BM-13):

```java
CellBuffer out = CellBuffer.of(band.width(), band.height());
band.cellsAt(Duration.ofMillis(240), ctx, out);
assertEquals(expectedDump, out.dump());
```

Making it move on screen is the canvas part's job (CV-90):

```java
AsciiSprite sprite = new AsciiSprite(band);
console.place(sprite, Rect.of(4, 2, 10, 10));
sprite.play();
```

---

## 5. Out of scope for this part

- Image or sixel rendering, and decoding an image file into characters.
  An `AsciiBitmap` is a grid of characters an author writes, not a
  picture the library converts.
- Collision detection, scene graphs, tweening, game loops.
- Anything with a clock, a thread or a terminal (BM-12).
- Compositing (CR-46) — this part resolves cells; core composites them.
- An interactive art editor.
