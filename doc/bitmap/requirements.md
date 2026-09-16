# ConsoleKit Bitmap — Requirements (`BM`)

Part 3 of 4. Depends on [core](../core/requirements.md) only. The file
format is specified separately in [art-format.md](art-format.md) (`AF`).
See [the map](../requirements.md).

**X × Y coloured characters, as a value.** A bitmap is a grid of cells;
an animation is an ordered list of bitmaps with durations. Nothing in
this part owns a terminal, a thread or a clock, and nothing in it knows
whether the result will be printed to `System.out` or painted on a
canvas — that is the point of CR-42.

`MUST` / `SHOULD` / `MAY` are RFC 2119. IDs `BM-1…BM-12` replace
`CV-70…CV-89` from v3; the map has the table.

---

## 1. Terminology

**Bitmap** — an immutable `w × h` grid of core `Cell`s (CR-41). A value,
not a canvas and not an image (§5).
**Frame** — one bitmap in an animation, with a duration.
**Animation** — an ordered, non-empty list of frames plus an anchor and
any cycle groups. A bitmap is a one-frame animation.
**Palette** — the authoring-time map from a source character to a style
and an emitted glyph.
**Slot** — a palette index as stored: cells reference slots, not styles
(AF-3), which is what makes colour cycling a rotation.

---

## 2. The bitmap value

- **BM-1** `AsciiBitmap` MUST be an immutable `w × h` grid of core cells
  — X × Y coloured characters — each carrying a glyph and a `Style`
  (CR-1, CR-41). Dimensions MUST be fixed at construction. A bitmap is a
  value, not a mutable framebuffer; an edit produces a new bitmap.
- **BM-2** Every cell MUST occupy exactly one display column as measured
  by CR-6. A wide, combining or zero-width grapheme MUST be rejected **at
  construction**, not at paint: the grid is positional, and a two-column
  glyph at `(x, y)` shifts every cell to its right.
- **BM-3** Cells MUST support per-channel transparency — glyph,
  foreground and background independently absent (CR-41) — so a bitmap
  drawn over other content leaves what is underneath instead of punching
  a rectangular hole. Compositing a cell with an absent glyph MUST leave
  the destination glyph untouched; likewise per colour channel.
- **BM-4** `AsciiBitmap` MUST be a `Renderable` (CR-16, CR-42), and that
  MUST be its *only* output contract. It therefore prints to an ordinary
  console as a `String` with escapes applied exactly like text-mode
  output (CR-44), prints into scrollback (CV-10), and blits into a canvas
  Rect (CV-36) — with no code in this part that is aware of any of the
  three. Where there is nothing underneath, absent channels MUST resolve
  to a space and the default style. A second, consumer-specific output
  method is a defect.
- **BM-5** Bitmaps MUST be **authorable** as text: a block of rows plus a
  palette, constructible from a `String`. A palette entry maps one source
  character to a `Style` and, optionally, to a different emitted glyph —
  which is what lets a pattern be written as digits that all paint `#`,
  and what keeps the source one character per cell (BM-2). Rows shorter
  than the declared width MUST be padded with fully transparent cells;
  rows longer MUST be an error. Text is the authoring and review surface;
  the stored form is the binary `.art` file (AF-1…AF-8).
- **BM-6** Bitmap glyphs MUST pass through the same tier and encoding
  path as every other glyph (CR-7, CR-29…CR-32). A bitmap MAY carry
  per-tier alternates (`FULL` / `CP437` / `ASCII`) per distinct glyph;
  when none is supplied the substitution policy applies, and because
  CR-30 substitution is width-preserving the grid still lines up. Tier
  alternates MUST change glyphs only, never geometry.
- **BM-7** `AsciiBitmap` MUST be constructible from a function of
  position: `generate(w, h, (x, y) -> Cell)`. The function MUST be
  evaluated exactly once per cell **at construction** and MUST NOT be
  retained, so the result stays an immutable value (BM-1) and painting
  stays a pure blit. A per-paint cell callback is the thing this
  requirement exists to prevent.

## 3. Animation, as a value

The library that owns animation *timing* does not own a clock. Everything
here is a pure function of an elapsed `Duration`; the thing that has a
clock is the canvas widget (CV-90), and it lives in the other part.

- **BM-8** `AsciiAnimation` MUST be an ordered, non-empty list of frames,
  each with a duration, plus an anchor and a default duration. A single
  bitmap MUST be representable as a one-frame animation, so there is one
  model and one file format (AF-1), not two. Frames MAY differ in size;
  the animation's bounds are the maximum over its frames, with each frame
  anchored per the declared anchor and padded with fully transparent
  cells (BM-3).
- **BM-9** Frame selection MUST be a **pure function of elapsed time**:
  `frameAt(Duration elapsed)`, total, allocation-free and deterministic.
  It MUST NOT read a clock, sleep, or start a thread. Because it is a
  function of absolute elapsed time rather than a step counter, a
  consumer that falls behind **skips** frames instead of queueing them,
  and playback speed is independent of any refresh rate.
- **BM-10** An animation MUST support **palette cycling**: ordered groups
  of style slots, each with a period, rotated by one position per period.
  The rotation offset MUST be the same pure-function-of-elapsed-time
  shape as BM-9 (`cycleOffsetAt(Duration)`), and resolving a cell's style
  through the current offset MUST happen at composite time, not by
  rewriting cells. This is what makes an N-colour moving pattern **one**
  frame instead of N: a 10 × 10 field of `#` with diagonal stripes is one
  frame, a 1-entry glyph table, a 4-entry style table and one cycle group.
- **BM-11** The model MUST be assertable without a terminal, without a
  canvas and without sleeping: the same elapsed value MUST always select
  the same frame and the same cycle offset, and the text view (AF-7) MUST
  be usable as the assertion surface.
- **BM-12** This part MUST NOT depend on the canvas or text parts
  (CR-43). It has no `Widget`, no `Tick`, no `Surface`, no `println`. A
  requirement that cannot be satisfied without one of those belongs in
  the canvas part.

---

## 4. Usage sketches

Authoring, printing and blitting — the same value through the one
contract (BM-4):

```java
AsciiBitmap logo = AsciiBitmap.parse("""
        .###.
        #.o.#
        .###.
        """,
        Palette.of('#', Style.fg(Color.BRIGHT_CYAN),
                   'o', Style.fg(Color.RED).bold(),
                   '.', Palette.TRANSPARENT));

System.out.println(logo);        // ordinary console, like text mode (CR-44)
console.print(logo);             // canvas: into scrollback (CV-10)
surface.blit(logo);              // canvas: into a Rect (CV-36)
```

Generated rather than typed (BM-7):

```java
Style[] ramp = { Style.fg(Color.RED),   Style.fg(Color.YELLOW),
                 Style.fg(Color.GREEN), Style.fg(Color.CYAN) };

AsciiBitmap field = AsciiBitmap.generate(10, 10,
        (x, y) -> Cell.of('#', ramp[(x + y) % ramp.length]));
```

A 10 × 10 field of `#` with stripes travelling diagonally: **one** frame
plus one cycle group (BM-5, BM-10). The digits are palette characters
that all emit `#`; rotating the group of style slots moves the stripes:

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

band.frameAt(Duration.ofMillis(240));   // pure; no clock, no thread (BM-9)
Art.write(Path.of("art/stripes.art"), band);
```

Making it move on screen is the canvas part's job (CV-90):

```java
console.place(new AsciiSprite(band), Rect.of(4, 2, 10, 10)).play();
```

---

## 5. Out of scope for this part

- Image or sixel rendering, and decoding an image file into characters.
  An `AsciiBitmap` is a grid of characters an author writes, not a
  picture the library converts.
- Collision detection, scene graphs, tweening, game loops.
- Anything with a clock, a thread or a terminal (BM-12).
- An interactive art editor.
