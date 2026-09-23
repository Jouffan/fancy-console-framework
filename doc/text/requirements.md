# ConsoleKit Text — Requirements (`TX`)

Part 2 of 4. Package prefix `dev.consolekit.text`. Depends on
[core](../core/requirements.md) only. See [the map](../requirements.md).

**Colour a string.** No lifecycle, no setup, no terminal ownership, no
threads. Three surfaces over one implementation: static inline helpers
(`red("x")`), a configurable style instance (`styler.highlight("x")`),
and pre-made snippets (`Snippets.line(Color)`).

Text is a **facade over the core render path** (CR-44), not the owner of
it. Anything else that wants to print — a bitmap, a table — goes to core
directly and MUST NOT depend on this part (CR-43).

`MUST` / `SHOULD` / `MAY` are RFC 2119.

---

## 1. Common rules

- **TX-1** Colouring MUST be available as stateless static helpers
  returning a plain `String` with escapes already applied.
- **TX-2** Text mode MUST NOT open a terminal, load native code, or start
  a thread — ever, under any code path. It MAY read the resolved
  capabilities (colour depth, glyph tier) and the ambient width (CR-45)
  because those come from the JDK and environment, not from a terminal
  library. Discovering width via JLine is a defect — and, since JLine is
  referenced only by `canvas.internal.TerminalPort` (CR-22), also a layering
  failure the CR-43 scan catches.
- **TX-3** When the environment can't render escapes, text mode MUST
  return the string unchanged rather than emitting anything.
- **TX-4** Text mode MUST NOT measure, wrap, pad or align caller text, so
  its output is safe to embed inside canvas-mode output. (Snippets that
  need a width take it as an argument or use the ambient width; they do
  not measure the caller's strings.)
- **TX-5** Each styled span MUST be closed with its specific SGR "off"
  code, never a blanket reset, so surrounding styles survive nesting.
- **TX-6** The known consequence of TX-5 — nested spans of the same
  category are not stack-aware — MUST be documented and pinned by a test
  rather than silently fixed.
- **TX-7** The first time text mode emits an escape it MUST register a
  shutdown hook resetting SGR on the original stdout, disableable by
  the environment variable `CONSOLEKIT_NO_SGR_HOOK` (set to any value).
  The hook and the "already registered" flag MUST live on
  `ConsoleRuntime` (NFR-12), not as static mutable state in this part.

## 2. Static inline helpers (`Text`)

- **TX-8** `Text` MUST offer, as static methods designed for
  `import static`: the sixteen named colours (`red`, `brightRed`, …),
  `fg(Color, s)`, `bg(Color, s)`, the six attributes (`bold`, `dim`,
  `italic`, `underline`, `strike`, `reverse`), the theme roles (`success`,
  `warning`, `error`, `info`, `muted`, `highlight`), and `styled(Style, s)`.
  Each MUST have a `(String)` and a `(String format, Object... args)`
  overload.
- **TX-9** Theme-role helpers on `Text` use `Theme.DEFAULT`. Changing the
  theme is what `Styler` is for; `Text` MUST NOT grow mutable state.

## 3. Dynamic style instances (`Styler`)

- **TX-10** `Styler` MUST be an immutable value holding a `Theme` (or a
  role→`Style` map) and offering the same method names as `Text`'s role
  helpers as **instance** methods: `styler.highlight("x")`,
  `styler.success("x")`, `styler.error("x")`, etc., plus
  `styler.styled(Role, s)`.
- **TX-11** `Styler` MUST be constructible from a `Theme`
  (`Styler.of(Theme.MONOCHROME)`) and MUST offer `with(Role, Style)`
  returning a new instance, so one line of setup can say "highlight is
  bold cyan on this instance".
- **TX-12** `Styler` MUST apply the same capability gating and SGR-close
  rules as `Text` (TX-3, TX-5). It is a facade over the same code, not a
  second implementation.

## 4. Pre-made snippets (`Snippets`)

- **TX-13** `Snippets` MUST offer glyph-tier-aware, colourable one-liners
  returning `String`: `line(Color)` and `line(Color, int width)` (a
  horizontal rule), `rule(Color, String title)`, `ok(String)` /
  `fail(String)` / `warn(String)` (mark plus text), `bullet(Color, String)`,
  `banner(Color, String)`, and `bar(Color, double fraction, int width)`
  (a static progress bar). The catalogue MAY grow; each entry MUST
  degrade to `ASCII`.
- **TX-14** For each `String`-returning snippet a `print…` convenience
  MUST exist (`Snippets.printLine(Color)`), writing the snippet plus a
  newline to `System.out`. These are the *only* text-mode methods that
  print, and they MUST go through `System.out` exactly as the caller
  would, never through a terminal port.
- **TX-15** Snippets that need a width and are not given one MUST use the
  core ambient width (CR-45): `COLUMNS` if it parses as a positive
  integer, else 80. The 80 fallback in shells that do not export
  `COLUMNS` is not a defect and MUST NOT be "fixed" by reaching for a
  terminal library (TX-2).

---

## 5. Usage sketches

Illustrative. Method names that are already required (`Text.red`,
`Styler.of`, `Snippets.line`, …) are frozen; surrounding glue is not.

```java
import static dev.consolekit.text.Text.red;
import static dev.consolekit.text.Text.success;

System.out.println(success("done") + " in " + red("12ms"));
```

```java
Styler s = Styler.of(Theme.DEFAULT)
        .with(Theme.Role.HIGHLIGHT, Style.fg(Color.BRIGHT_CYAN).bold());
System.out.println("found " + s.highlight("42") + " matches");
```

```java
System.out.println(Snippets.rule(Color.CYAN, "scan"));
System.out.println(Snippets.ok("indexed 1 204 files"));
System.out.println(Snippets.bar(Color.GREEN, 0.61, 40));
Snippets.printLine(Color.RED);   // TX-14: the only text-mode print
```

Anything that is a `Renderable` prints the same way in every part, and
does **not** come through this one (CR-42, CR-44):

```java
Table t = Table.of(
        List.of("file", "size"),
        List.of(List.of("příliš žluťoučký kůň.dat", "12K"),
                List.of("encode.mp4", "1.1G")));
System.out.println(t);                    // toString → Rendering, ambient context
console.print(t);                         // canvas: into scrollback
surface.blit(t);                          // canvas: catalogue blit (CV-36)

System.out.println(logo);                 // an AsciiBitmap, same path (BM-4)
```

---

## 6. Out of scope for this part

- Measuring, wrapping or aligning caller text (TX-4) — that is core.
- Any terminal ownership, cursor movement or animation — that is canvas.
- Owning the escape emitter. Core owns it (CR-21, CR-44).
