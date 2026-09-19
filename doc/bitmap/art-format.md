# ConsoleKit `.art` — File format definition (`AF`)

Normative specification of the stored form for
[bitmap](requirements.md) values. Part of part 3; split out because a
file format is a compatibility surface with a longer life than the code
that reads it.

**The stored unit is two bytes: one glyph slot, one style slot (AF-3).**
The palette rides in the file, so a frame is a flat `2 × w × h` block
with no parsing, no escapes and no ambiguity about where row `y` starts.
Slot `0` in both tables is absent. **The file is exactly "palette +
frames"** (BM-14). After load, `cellsAt` / print resolve slots to core
`Cell`s (CR-41); the file itself is not a grid of those objects. This is
the VGA text-mode / `XBin` shape: the data *is* a grid of `(char,
colour)` pairs. Persistence I/O is `Art.load` / `Art.loadResource` /
`Art.write` only.

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
  that), and MUST use fixed-width big-endian integers. Every count a
  later section is validated against MUST appear *before* that section.
  The writer MUST be deterministic: equal values produce byte-identical
  files, which is what makes goldens possible. Table order is the
  palette's canonical order (BM-14), never a hash order.
- **AF-3** A cell MUST be exactly two bytes — glyph slot, style slot —
  with slot `0` in **both** tables meaning absent. That leaves up to
  **255 glyphs and 255 styles per file** and makes BM-3 transparency part
  of the encoding rather than a flag bolted onto it. Every frame MUST be
  the header's `width × height` (BM-8 normalises before writing),
  row-major with no padding, so frame `i` starts at a computable offset
  and a 10 × 10 frame is exactly 200 cell bytes.
- **AF-4** The glyph table MUST store Unicode code points, not bytes,
  with the `CP437` and `ASCII` alternates of BM-6 in the same entry; an
  alternate of `0` means *none supplied*. Every stored code point —
  primary and alternates — MUST satisfy BM-2. The indirection exists to
  make BM-2 cheap and total: single-column validation runs once per
  table entry at load — at most 255 × 3 checks — instead of `w × h` per
  frame, and a file whose table is valid cannot contain a mis-measured
  cell.
- **AF-5** The style table MUST store full `Style` values in the CR-1 /
  CR-40 model: foreground and background each **absent, default, named,
  `index:n` or 24-bit rgb**, plus the attribute flags. A style byte is an
  **index into that table**, not a packed VGA attribute — the format MUST
  NOT be limited to 16 colours, and per-channel absence (BM-3) MUST be
  expressible in an entry. `named` and `index` are distinct kinds so a
  value round-trips equal.
- **AF-6** Reading MUST accept a classpath resource, a `Path` and an
  `InputStream`, so a jar can ship an art set and a child process can
  stream one. Every limit MUST be enforced **before** allocating:

  | Limit | Cap |
  |---|---|
  | width | 1 … 512 |
  | height | 1 … 256 |
  | frames | 1 … 1024 |
  | total cells (`width × height × frames`) | ≤ 4 194 304 |
  | total bytes read | ≤ 16 MiB (counted on streams) |

  A declared size outside its cap, a truncated section, a slot outside
  its table, a cycle that violates BM-14, a glyph that violates BM-2, a
  non-zero reserved bit, an unknown kind or anchor value, a duration
  that resolves to 0, a bad magic or a newer major version MUST throw
  with the byte offset. There are no external references, no includes
  and nothing executable in the format. `.art` files MAY be
  user-supplied, so a hostile one MUST fail fast rather than allocate
  what its header claims. The 80 × 24 full-canvas frame is the sizing
  reference: the caps allow ~2 000 of them. Loading is **not** a render
  path: CR-24's "never throw" does not apply to it, and failing loudly at
  load is the point of BM-2.
- **AF-7** Because the stored form is not reviewable, the text form MUST
  stay first-class as its **view**: `toSource()` MUST render any bitmap or
  animation as the BM-5 rows-plus-palette text, and both
  `parse(text) → write → read` and `parse(toSource(v))` MUST produce a
  value equal to the original. Source characters are not part of the
  value (BM-14), so `toSource()` MUST synthesise them deterministically:
  `.` for the fully absent cell, then for each distinct (glyph, style)
  pair in first-appearance order, the glyph itself if it is printable
  ASCII and still free, else the next free character of a fixed
  alphabet. The text form is a projection of the value, never a second
  on-disk format, and `Probe` MUST be able to dump an `.art` file to it
  so a broken file can be read without a hex editor.
- **AF-8** Versioning MUST be explicit and MUST fail closed: a reader
  encountering a **major** version it does not know MUST refuse the file;
  a **minor** bump MUST only ever add **trailer blocks** (§2) that an
  older reader can skip. Fields MUST NOT change meaning within a major
  version. The format MUST NOT be extended by reinterpreting reserved
  bits, which is why a reader MUST reject them when set.

---

## 2. Layout — version 1.0

All integers big-endian. Sections in this order, no padding.

```
magic    "CKART"  u8[5]
version  u8 major = 1, u8 minor = 0     unknown major = refuse (AF-8)

header   u16 width, u16 height, u16 frames        caps: AF-6
         u8  anchor                               0 TOP_LEFT   1 TOP     2 TOP_RIGHT
                                                  3 LEFT       4 CENTER  5 RIGHT
                                                  6 BOTTOM_LEFT 7 BOTTOM 8 BOTTOM_RIGHT
         u32 defaultDurationMillis                ≥ 1

glyphs   u8 count, then per entry (slots 1…count):
             u32 codePoint                        FULL tier; BM-2
             u32 cp437Alt                         0 = none
             u32 asciiAlt                         0 = none

styles   u8 count, then per entry (slots 1…count):
             colour fg
             colour bg
             u8 attrBits                          bit0 bold   bit1 dim     bit2 italic
                                                  bit3 underline bit4 strike bit5 reverse
                                                  bits 6–7 MUST be 0

  colour = u8 kind, then payload:
             0 absent    —
             1 default   —
             2 named     u8   0…15
             3 index     u8   0…255
             4 rgb       u8 r, u8 g, u8 b

cycles   u8 count, then per group:
             u8 length                            ≥ 2
             u8[length] styleSlots                1…styles.count, distinct,
                                                  each in at most one group
             u32 periodMillis                     ≥ 1

frames   per frame:
             u32 durationMillis                   0 = use default
             width × height cells, row-major, each:
                 u8 glyphSlot                     0…glyphs.count
                 u8 styleSlot                     0…styles.count

trailer  zero or more blocks until end of input:
             u16 tag, u32 length, u8[length]
         1.0 defines no tags. A reader MUST skip tags it does not know,
         still counting their bytes against the AF-6 size cap.
```

Slot `0` of each table is reserved as *absent* and is not written as an
entry, which is why the usable count is 255 and not 256 (AF-3). The
`cycles` section follows `styles` so that every slot in it can be
validated the moment it is read (AF-2).

Worked size — the 10 × 10 four-colour `#` stripe field of BM-10:

| Section | Bytes |
|---|---|
| magic + version | 5 + 2 |
| header | 11 |
| glyphs: count + 1 entry | 1 + 12 |
| styles: count + 4 × (named fg 2, absent bg 1, attrs 1) | 1 + 16 |
| cycles: count + 1 group (length 1, slots 4, period 4) | 1 + 9 |
| frame: duration + 100 cells | 4 + 200 |
| **total** | **262** |

Ten hand-written frames of the same thing would carry two thousand cell
bytes. The 262-byte file is the first byte golden.

---

## 3. Compatibility rules

1. The magic and the major version are the only things a reader may
   trust before validating (AF-6).
2. Adding information = minor bump + a new trailer block tag.
3. Changing a field's meaning, width or order = major bump.
4. A writer MUST emit the lowest minor version whose features it uses,
   so files stay readable by the widest set of readers.
5. Goldens under `src/test/resources/golden/` pin the byte layout; a
   diff there is a format change and MUST be argued for, not absorbed
   (NFR-12).
