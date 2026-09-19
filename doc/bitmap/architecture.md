# ConsoleKit Bitmap — Architecture

Part 3 of 4. Requirements: [bitmap/requirements.md](requirements.md),
format: [art-format.md](art-format.md). See [the map](../architecture.md).

Values and a codec. **No thread, no clock, no terminal** (BM-12) — which
is what lets the same artefact print to `System.out` (BM-4) and resolve
into a `CellBuffer` for overlay paint (BM-13) without either consumer
appearing in this package.

## 1. Packages

```
dev.consolekit.bitmap            AsciiBitmap, Palette, AsciiAnimation,
                                 Anchor, Art
dev.consolekit.bitmap.internal   NOT exported. ArtFormat  (binary codec)
                                 ArtText    (authoring parse + toSource view)
```

`Cell` and `CellBuffer` are core (CR-41, CR-46). This package defines no
cell type, no grid-of-cells type, no compositor, no colour type and no
style type.

## 2. `Palette` (BM-14)

The owner of everything the slots point at:

```
glyphs[1..n]   code point + cp437Alt + asciiAlt        n ≤ 255
styles[1..m]   Style (channels may be unset)           m ≤ 255
cycles[0..g)   int[] styleSlots + period
source map     char → (glyphSlot, styleSlot)           authoring only
```

Built by `Palette.of(...)` / `Palette.builder()`; table order is
declaration order, which is what makes the writer deterministic (AF-2).
BM-2 validation and the 255 limits are enforced in `build()`. Two derived
arrays are computed once and kept: `groupOf[styleSlot]` and
`positionInGroup[styleSlot]`, so resolving a style through a cycle offset
is two array reads and an addition.

Equality is tables + cycles. The source map is excluded, which is why a
loaded animation equals the parsed one it came from (AF-7).

## 3. `AsciiBitmap`

An immutable `w × h` grid of **slots** in row-major order (a `byte[]` of
`2 × w × h`, the stored layout) plus its `Palette` — not resolved
`Cell`s. Three reasons: load is a copy rather than a conversion; palette
cycling is a rotation of a small array instead of a rewrite of `w × h`
cells (BM-10); and CR-6 single-column validation plus tier resolution
run once per table entry instead of once per cell (BM-2, AF-4).

- `parse(text, palette)` is `ArtText`.
- `generate(w, h, fn)` evaluates the function once per cell, row-major,
  builds the implicit palette in first-appearance order, throws past 255,
  and drops the reference (BM-7).
- `render(ctx)` is the **print** door (BM-4): resolve slots at cycle
  offset 0, resolve glyphs for `ctx` (§5), emit `List<StyledText>` with
  absent channels as space + default style. `toString()` is
  `Rendering.toString(this)` (CR-44).

There is no `print()`, no `paint(Surface)` and no `cellsAt` here: the
paint door is on `AsciiAnimation`, and a still is
`AsciiAnimation.of(bitmap)`. Persistence is `Art` only;
`AsciiBitmap.parse` is authoring, not I/O.

## 4. `AsciiAnimation`

Normalised frames (all `width × height`, BM-8) + effective durations +
anchor + the shared palette. Precomputed at construction: the cumulative
end time of each frame and the total.

```java
// values
int frameCount();  int cycleGroupCount();  Duration totalDuration();
int width();  int height();  Anchor anchor();  Palette palette();
AsciiBitmap frame(int index);

// pure selection (BM-9, BM-10) — total, allocation-free
int frameIndexAt(Duration elapsed);              // wraps; negative → 0
int cycleOffsetAt(int group, Duration elapsed);  // floor(t/period) mod length
AsciiBitmap frameAt(Duration elapsed);           // frame(frameIndexAt(t))

// paint door
void cells(int frameIndex, int[] cycleOffsets,   // BM-15 — no allocation
           RenderContext ctx, CellBuffer out);
void cellsAt(Duration elapsed,                   // BM-13 — convenience
             RenderContext ctx, CellBuffer out);
CellBuffer cellsAt(Duration elapsed, RenderContext ctx);   // tests
```

`cells` is one loop over the frame's slot bytes: glyph slot → resolved
code point (§5) or absent; style slot → through `groupOf` /
`positionInGroup` and the supplied offset → `Style`; then
`out.put(x, y, codePoint, style)`. Absence is written as absence; nothing
is composited here (CR-46).

`cellsAt(elapsed, ctx, out)` computes the index and the offsets and calls
`cells`. It may allocate the small offsets array; a per-frame consumer
that must not allocate keeps its own `int[]` and calls the primitive —
which is exactly what `AsciiSprite` does, and also how it pins a frame
or an offset (CV-91).

The consumer that supplies elapsed time is `canvas.widget.AsciiSprite`
(CV-90). It is in the other part on purpose.

## 5. Glyph resolution (BM-6)

`ctx.capabilities()` gives the glyph tier and the output encoding. For
each glyph-table entry: take the tier's alternate if one is declared,
else the primary; then pass it through `core.internal.Encoding`
substitution (CR-29…CR-31), which is width-preserving. The result is a
`int[256]` of resolved code points. Capabilities are resolved once per
process, so the array is memoised on the palette keyed by capabilities
identity; the race is benign because the computation is idempotent.

Until M5 lands the content-side substitution, this step is the tier
lookup alone; the seam is already in the right place.

## 6. `ArtFormat` — the binary codec

One reader and one writer for `.art` (AF-1…AF-8), reached only through
`Art`:

```java
Art.load(Path)   Art.load(InputStream)   Art.loadResource(String)
Art.write(Path, AsciiAnimation)   Art.write(OutputStream, AsciiAnimation)
```

The reader is a single forward pass over a counting, bounded
`DataInputStream` that checks every declared count against its AF-6 cap
**before** allocating and throws with the byte offset — loading is not a
render path, so CR-24's "never throw" does not apply here and must not be
applied by reflex. The section order (tables before cycles before frames)
means every slot is validated the moment it is read. The writer is
deterministic so goldens can be byte-compared; the first golden is the
262-byte stripe field worked in the format document.

Layout, slot semantics, caps and the versioning rules are normative in
[art-format.md](art-format.md) and are not restated in code comments.

## 7. `ArtText` — the text view

Parses the rows-plus-palette authoring syntax (BM-5) and renders
`toSource()` (AF-7), synthesising source characters deterministically. It
produces and consumes the same value object; it never produces a file.
This is how a binary `.art` gets reviewed, diffed in a test failure
message, and dumped by `Probe`.

Because palette entries carry the emitted glyph separately from the
source character, an authoring block stays one character per cell while
painting anything — the four-colour `#` stripe field is digits `1`–`4`
over a one-entry glyph table.

## 8. Build order inside M2b

`Palette` → `AsciiBitmap` + `ArtText` (testable through
`Rendering.toString` and `toSource`) → `AsciiAnimation` selection maths
(tested with bare `Duration`s) → `cells` / `cellsAt` (tested through
`CellBuffer.dump()`) → `ArtFormat` against byte goldens. Nothing in the
list needs canvas.
