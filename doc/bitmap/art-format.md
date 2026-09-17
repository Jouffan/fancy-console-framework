# ConsoleKit `.art` — File format definition (`AF`)

Normative specification of the stored form for
[bitmap](requirements.md) values. Part of part 3; split out because a
file format is a compatibility surface with a longer life than the code
that reads it.

**The stored unit is two bytes: one glyph slot, one style slot (AF-3).**
Both tables ride in the file, so a frame is a flat `2 × w × h` block with
no parsing, no escapes and no ambiguity about where row `y` starts. Slot
`0` in both tables is transparent. After load, `cellsAt` / print resolve
slots to core `Cell`s (CR-41); the file itself is not a grid of those
objects. This is the VGA text-mode / `XBin` shape: the data *is* a grid
of `(char, colour)` pairs, and a format that is not a grid of
`(char, colour)` pairs has to re-derive one at load. Persistence I/O is
`Art.load` / `Art.write` only.

`MUST` / `SHOULD` / `MAY` are RFC 2119. IDs `AF-1…AF-8` replace
`CV-81…CV-86` and `CV-89` from v3.

---

## 1. Requirements

- **AF-1** There MUST be exactly one persistence format, and it MUST
  cover both a still bitmap and an animation: a bitmap is stored as a
  one-frame animation (BM-8). One reader, one writer, one extension
  (`.art`). A separate "sprite format" is a defect.
- **AF-2** An `.art` file MUST be binary, MUST be readable in a single
  forward pass with no seeking (so it streams from a jar entry or a
  pipe), MUST NOT be compressed (jar and transport layers already do
  that), and MUST use fixed-width big-endian integers. The writer MUST
  be deterministic: equal values produce byte-identical files, which is
  what makes goldens possible.
- **AF-3** A cell MUST be exactly two bytes — glyph slot, style slot —
  with slot `0` in **both** tables meaning transparent. That leaves up to
  **255 glyphs and 255 styles per file** and makes BM-3 transparency part
  of the encoding rather than a flag bolted onto it. Frames MUST be
  row-major with no padding, so frame `i` starts at a computable offset
  and a 10 × 10 frame is exactly 200 bytes.
- **AF-4** The glyph table MUST store Unicode code points, not bytes,
  with the `CP437` and `ASCII` alternates of BM-6 in the same entry. The
  indirection exists to make BM-2 cheap and total: single-column
  validation runs once per table entry at load — at most 255 checks —
  instead of `w × h` per frame, and a file whose table is valid cannot
  contain a mis-measured cell. Tier alternates MUST change glyphs only,
  never geometry.
- **AF-5** The style table MUST store full `Style` values in the CR-1 /
  CR-40 model: foreground and background each absent, named, `index:n` or
  24-bit rgb, plus the attribute flags. A style byte is an **index into
  that table**, not a packed VGA attribute — the format MUST NOT be
  limited to 16 colours, and per-channel absence (BM-3) MUST be
  expressible in an entry.
- **AF-6** Reading MUST accept a classpath resource, a `Path` and an
  `InputStream`, so a jar can ship an art set and a child process can
  stream one. Every limit MUST be enforced **before** allocating:
  documented caps on width, height, frame count and total file size; a
  declared size that exceeds them, a truncated frame, a slot outside its
  table, a bad magic or a newer major version MUST throw with the byte
  offset. There are no external references, no includes and nothing
  executable in the format. `.art` files MAY be user-supplied, so a
  hostile one MUST fail fast rather than allocate what its header claims.
  Loading is **not** a render path: CR-24's "never throw" does not apply
  to it, and failing loudly at load is the point of BM-2.
- **AF-7** Because the stored form is not reviewable, the text form MUST
  stay first-class as its **view**: `toSource()` MUST render any bitmap or
  animation as the BM-5 rows-plus-palette text, and
  `parse(text) → write → read` MUST produce an equal value. The text form
  is a projection of the value, never a second on-disk format, and
  `ArtProbe` MUST be able to dump an `.art` file to it so a broken file can
  be read without a hex editor.
- **AF-8** Versioning MUST be explicit and MUST fail closed: a reader
  encountering a **major** version it does not know MUST refuse the file;
  a **minor** bump MUST only ever add trailing, length-prefixed blocks
  that an older reader can skip. Fields MUST NOT change meaning within a
  major version. The format MUST NOT be extended by reinterpreting
  reserved bits.

---

## 2. Layout

```
magic    "CKART"  u8[5]
version  u8 major, u8 minor          major bump = refuse to load (AF-8)

header   u16 width, u16 height, u16 frames,
         u8  anchor,
         u32 defaultDurationMillis,
         u8  cycleCount, then per cycle:
             u8 length, u8[length] styleSlots, u32 periodMillis

glyphs   u8 count, then per entry:
             u32 codePoint (FULL), u32 cp437Alt, u32 asciiAlt

styles   u8 count, then per entry:
             u8  flags   (fg kind, bg kind, attrs present)
             fg spec, bg spec, u8 attrBits

frames   per frame:
             u32 durationMillis  (0 = use default)
             width*height cells, row-major, each:
                 u8 glyphSlot, u8 styleSlot
```

Slot `0` of the glyph table and slot `0` of the style table are reserved
as *transparent* and MUST NOT be written as real entries, which is why
the usable count is 255 and not 256 (AF-3).

Worked size: a 10 × 10 single-frame field of `#` in four colours is
`5 + 2` magic and version, a header, a 1-entry glyph table, a 4-entry
style table, one cycle group, and **200 cell bytes**. Ten hand-written
frames of the same thing would be two thousand.

---

## 3. Compatibility rules

1. The magic and the major version are the only things a reader may
   trust before validating (AF-6).
2. Adding a field = minor bump + a trailing length-prefixed block.
3. Changing a field's meaning, width or order = major bump.
4. A writer MUST emit the lowest minor version whose features it uses,
   so files stay readable by the widest set of readers.
5. Goldens under `src/test/resources/golden/` pin the byte layout; a
   diff there is a format change and MUST be argued for, not absorbed.
