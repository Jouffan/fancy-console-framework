# ConsoleKit — coding conventions

How code is written. What it must do is in the requirements. Precedence:
requirements > architecture > this file. **A name given in the
specification wins over any rule here**. Do not rename `Ansi`, `Glyphs`,
`CellBuffer`, `Rendering` or anything else the documents name.

These rules are not machine-enforced. Reviewers check them. The
structural rules (CR-21…CR-23, CR-43, NFR-9b) *are* enforced, by the
NFR-10 scans.

## 1. Files

- UTF-8, `\n`, final newline, 4-space indent, no tabs, lines ≤ 120
  columns (`.editorconfig`, `.gitattributes`).
- One top-level type per file. No wildcard imports, static or not. Import
  order: `java.*`, then third-party, then `dev.consolekit.*`, then
  static imports. Tests may use `import static
  org.junit.jupiter.api.Assertions.*`.
- Main sources: non-ASCII only in comments and in `core.internal.Glyphs`
  literals (CR-23, NFR-10). Test sources may contain any UTF-8.

## 2. Naming

| Element | Rule | Example |
|---|---|---|
| Package | lowercase, one word, singular | `widget`, `event`, `internal` |
| Type | UpperCamel noun. No `I`, `Impl`, `Abstract`, `Base`, `Manager`, `Helper` or `Util` | `CellBuffer`, `TerminalPort` |
| Acronym | one word, cased as one word | `Ansi`, `Sgr`, `Utf8Probe`, `WidgetId` |
| Method | lowerCamel verb, or noun for accessors | `composite`, `dump`, `width()` |
| Accessor | record style, no `get` prefix | `width()`, `style()`. `get(x, y)` is an indexed read, not a getter |
| Boolean | `is` / `has` / `can` | `isTransparent()`, `hasGlyph()` |
| Constant | `UPPER_SNAKE` for `static final` immutable values | `Color.DEFAULT`, `Cell.TRANSPARENT` |
| Field / local | lowerCamel. No Hungarian notation, no `m`/`_` prefix | `clipX` |
| Parameter | same name as the field it lands in | `this.width = width` |
| Index | one letter for the axis; a word when two share a loop | `x`, `y`, `i`; `row`, `slot` |
| Type parameter | single capital letter | `T` |

Spelling: identifiers use US spelling, matching the JDK (`Color`,
`center`). Prose and Javadoc follow the documents (British: "colour").

**Acronyms.** An acronym is one word, in both cases. Two letters stay
upper: `IO`, `ioStream`. Three or more take a capital and then lower:
`Ascii`, `asciiCode`, never `ASCIICode`. Two adjacent acronyms stay two
words: `utf8Probe`, not `utf8probe`. A name the documents already give
(`Ansi`, `Sgr`, `WidgetId`) is not respelt to fit this.

**Indexes.** A loop over one axis uses one letter: `x` and `y` for a
grid, `i` otherwise. When one loop walks two sequences, each index is a
word (`slot`, `frame`) so the two cannot be swapped unread. No `idx`,
no `index1`.

**Parameters match fields.** A parameter that is stored is named as the
field, and the assignment is `this.width = width`. Do not invent `w`,
`newWidth` or `widthIn` for that parameter. A parameter that is not
stored may be short (`w`, `h` on `CellBuffer.of`).

**Factory verbs.** Use the ones the specification already uses, and no
others unless a document names them:

| Verb | Meaning | Throws? |
|---|---|---|
| `of(...)` | build a value from parts | on invalid arguments |
| `parse(text, ...)` | text → value | yes, on bad input (CR-24: not a render path) |
| `load(...)` / `write(...)` | I/O for a format (`Art`) | yes |
| `builder()` | multi-step construction | at `build()` |
| `with(...)` | copy with one thing changed | on invalid arguments |
| `toX()` | conversion | no |
| `forX(...)` | resolve for a context (`Encoding.forOutput`) | no |

## 3. Types and state

- **Default to the narrowest thing.** Package-private before `public`.
  A `final` class before an open one. A `record` for any immutable value.
  A `sealed` interface for any closed hierarchy (`WidgetEvent`).
- **Immutable by default.** Make defensive copies at the boundary
  (`List.copyOf`, `array.clone()`). Never expose a mutable internal
  array. Mutable types (`CellBuffer`, widgets) say in their Javadoc who
  owns them (NFR-7).
- **Static utility classes** are `final` with a private constructor. They
  hold no mutable state: all process-wide state is in `ConsoleRuntime`
  (NFR-12). No singletons, no static caches anywhere else.
- **Cross-package internals** go in the part's `internal` package. A
  `public` type outside `internal` is API, and it needs Javadoc with a
  snippet (NFR-1).
- **Exports.** Add a package's `exports` line to `module-info.java` in
  the same commit as its first class. Never export `*.internal` (NFR-9b).
- **Write the type.** No `var`. The type sits on the left, where a
  reader does not have to reconstruct it from the right-hand side.

## 3.1 Control flow

- **A method, not a lambda.** A lambda is allowed only as a one-expression
  argument that captures nothing. A block, a capture, a nest, or a body
  you would name in a comment is a named method. A named method can
  carry the requirement ID and a test; a lambda cannot. This is also
  why a hot path has no capturing lambda (NFR-5).
- **`switch` for a sealed type.** Dispatch on a sealed hierarchy
  (`WidgetEvent`) is a `switch` over the permitted types, not an
  `if`/`else if` chain of `instanceof`. The `switch` is exhaustive, so
  a new permitted type fails the build instead of falling through.
- **One absence channel.** Domain absence is a value, and there is one
  of them: an unset style channel, a missing glyph, `Cell.TRANSPARENT`
  (CR-41). Do not also return `null` or `Optional` for that absence.
  `OptionalInt` is allowed only where a document names it
  (`KeyEvent.codePoint`). `null` is an illegal argument, rejected by a
  guard, not a third way to say "absent".

## 4. Errors

- Parsing, loading and construction throw. Render paths never throw
  (CR-24).
- Use JDK unchecked exceptions (`IllegalArgumentException`,
  `IllegalStateException`, `UncheckedIOException`,
  `IndexOutOfBoundsException`). Add a new exception type only if a
  document names it.
- A message states what was wrong and the offending value, e.g.
  `"width must be > 0: -3"`. Messages are ASCII (CR-23).
- Never swallow an exception silently. The only place that catches
  broadly is the CR-24 render boundary, which warns once. There is no
  logging framework (CR-25).

## 5. Hot paths (NFR-5)

Tick delivery, layout, paint, diff and flush must not allocate per
iteration. Inside those paths:

- no streams, lambdas that capture, iterators over collections, boxing,
  varargs, string concatenation, or `String.format`;
- index loops over arrays or packed buffers;
- scratch buffers are fields sized once, reused, and resized only when
  the geometry changes.

The event queue, envelopes and key decoding may allocate. Everywhere
else, write the clear code first.

## 6. Javadoc and comments

- Every public type has Javadoc with a usage snippet (NFR-1). Use
  `{@snippet :` for it, not `<pre>`. Public methods get Javadoc when
  their behaviour is not obvious from their name and types.
- Cite the requirement a type or method implements:
  `Implements CR-46.` in Javadoc.
- Comments say *why*. If a line exists only because of a requirement,
  the comment names the ID: `// CR-46: never leave a wide cell split`.
- No commented-out code, no `TODO` without a milestone
  (`// TODO(M3): ...`).

## 7. Tests

- One test class per production type: `<Type>Test`, in the same package.
  Structural scans live in `dev.consolekit.arch` (`LayeringScanTest`,
  `AnsiMonopolyScanTest`, ...).
- **Method name = requirement ID + behaviour**, lowerCamel, ID lowercase
  with the hyphen dropped:
  `cr41_compositeKeepsGlyphWhenSourceAbsent`,
  `cr24_renderFailureFallsBackToPlainOutput`,
  `cr46_cr35_wideGlyphSkippedWhenContinuationClipped`. Tests of internal
  detail with no requirement behind them have no prefix.
- One behaviour per test, in arrange / act / assert order. No sleeps, no
  wall clock, no real terminal, no network.
- Assert on the surfaces `CLAUDE.md` lists (`Rendering.toString`,
  `CellBuffer.dump()`, `toSource()`, `EventReplayer`, `VirtualTerminal`).
  Build expected text as a text block, or as a golden when it is more
  than a few lines.
- Only JUnit 5. Do not add a test dependency (AssertJ, Mockito, ...)
  without asking. Prefer hand-written fakes to mocks.

## 8. Commits

- Imperative subject ≤ 72 columns, prefixed by milestone and IDs:
  `M1 CR-41 CR-46: add CellBuffer.composite`.
- One logical change per commit. A golden regeneration is a commit of its
  own and says why (NFR-12).
