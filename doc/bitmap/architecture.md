# ConsoleKit Bitmap — Architecture

Part 3 of 4. Requirements: [bitmap/requirements.md](requirements.md),
format: [art-format.md](art-format.md). See [the map](../architecture.md).

Values and a codec. **No thread, no clock, no terminal** (BM-12) — which
is what lets the same artefact print to `System.out` (BM-4) and resolve
to `Cell[]` for overlay paint (BM-13) without either consumer appearing
in this package.

## 1. Packages

Artefact `consolekit-bitmap`, module `dev.consolekit.bitmap`. Depends on
core only; no JLine, no third-party dependency.

```
dev.consolekit.bitmap          AsciiBitmap, Palette, AsciiAnimation, Anchor,
                               Art, ArtProbe
dev.consolekit.bitmap.internal NOT exported. ArtFormat  (binary codec)
                               ArtText    (authoring parse + toSource view)
```

`ArtProbe` is the `.art` dump of AF-7. It lives here, not in
`core.Probe`, because core cannot depend on this part (CR-43).

`Cell` is core (CR-41). This package defines no cell type, no colour
type and no style type.

## 2. `AsciiBitmap`

An immutable `w × h` grid of **slots** in row-major order plus the two
tables it was built from — a glyph slot and a style slot (AF-3), not
resolved `Cell`s. Three reasons: it is the stored layout, so load is a
copy rather than a conversion; palette cycling becomes a rotation of a
small array instead of a rewrite of `w × h` cells (BM-10); and CR-6
single-column validation plus `Glyphs` tier resolution run once per
table entry, at most 255 times, instead of once per cell (BM-2, AF-4).

`AsciiBitmap implements Renderable` is the **print** story (BM-4):
`render(ctx)` resolves slots through the current cycle offset, applies
CR-29…CR-31 substitution, and emits `List<StyledText>`. Absent channels
become space + default style. Core turns that into a `String` (CR-44).
`cellsAt(elapsed)` is the **paint** story (BM-13): same resolution, but
the result is core `Cell[]` with absence intact. There is no `print()`
and no `paint(Surface)` in this package. Persistence is `Art.load` /
`Art.write` only; `AsciiBitmap.parse` is authoring, not I/O.

`generate(w, h, fn)` evaluates the function once per cell into the array
and drops the reference (BM-7). `parse(text, palette)` is `ArtText`.

## 3. `AsciiAnimation`

Frames + durations + anchor + cycle groups. Three pure functions:

```java
AsciiBitmap frameAt(Duration elapsed);      // BM-9
int         cycleOffsetAt(Duration elapsed);// BM-10
Cell[]      cellsAt(Duration elapsed);      // BM-13 — allocation TBD at M4b
```

`frameAt` and `cycleOffsetAt` are total, allocation-free and defined on
absolute elapsed time, so a consumer that falls behind skips rather than
queues, and a test can ask for `t = 240ms` without sleeping (BM-11).
`cellsAt` is the same pure-function shape; whether it may allocate is
open question 6. A still bitmap is a one-frame animation, so the codec
has one shape (AF-1).

The consumer that supplies the elapsed time is `canvas.widget.AsciiSprite`
(CV-90). It is in the other part on purpose: it is the only piece that
needs a clock.

## 4. `ArtFormat` — the binary codec

One reader and one writer for `.art` (AF-1…AF-8), reached only through
`Art.load/write`. The reader is a single forward pass over a bounded
`DataInputStream` that checks every declared count against its cap
**before** allocating and throws with the byte offset — loading is not a
render path, so CR-24's "never throw" does not apply here and must not be
applied by reflex (AF-6). The writer is deterministic so goldens can be
byte-compared.

Layout, slot semantics and the versioning rules are normative in
[art-format.md](art-format.md) and are not restated in code comments.

## 5. `ArtText` — the text view

Parses the rows-plus-palette authoring syntax (BM-5) and renders
`toSource()` (AF-7). It produces and consumes the same value object; it
never produces a file. This is how a binary `.art` gets reviewed, diffed
in a test failure message, and dumped by `ArtProbe`.

Because palette entries carry the emitted glyph separately from the
source character, an authoring block stays one character per cell while
painting anything — the four-colour `#` stripe field is digits `1`–`4`
over a one-entry glyph table.
