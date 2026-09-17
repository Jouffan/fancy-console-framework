# ConsoleKit Text — Architecture

Part 2 of 4. Requirements: [text/requirements.md](requirements.md).
See [the map](../architecture.md).

Three public types, all in `dev.consolekit.text` (artefact
`consolekit-text`, module `dev.consolekit.text`), all facades over the
core output path (CR-44). No state, no threads, no JLine, no third-party
dependency.

## `Text`

Static `String`-returning helpers designed for `import static`: sixteen
named colours, `fg`/`bg`, six attributes, theme roles, `styled(Style, s)`,
each with a `format` overload. Stateless; probes capabilities through the
JDK only (never JLine); returns the input unchanged when escapes can't be
rendered; closes each span with its specific SGR-off code (never `SGR 0`);
registers the SGR-reset shutdown hook on first escape.

## `Styler`

An immutable value over a `Theme`. Same role-method names as `Text`, as
instance methods:

```java
Styler s = Styler.of(Theme.DEFAULT)
                 .with(Theme.Role.HIGHLIGHT, Style.fg(Color.BRIGHT_CYAN).bold());
System.out.println("found " + s.highlight("42") + " matches");
```

Implementation is a one-line delegation to `Text.styledWith(caps, style,
text)` — the same package-private seam `Text` already exposes for tests.
`Styler` exists so that "the colour set in the style instance" is a value
the application can pass around, inject, or swap per environment, without
`Text` growing mutable state.

## `Snippets`

Glyph-tier-aware one-liners, all colourable, all returning `String`:
`line(Color)`, `line(Color, width)`, `rule(Color, title)`, `ok`/`fail`/
`warn`, `bullet`, `banner`, `bar(Color, fraction, width)`. Plus a
`print…` twin for each that writes to `System.out` — the only printing
methods in this part, and deliberately trivial. Width, when not given,
comes from `COLUMNS` if it parses as a positive integer, else 80 (TX-15).
Never JLine, never a live terminal size (TX-2). Glyphs come from
`internal.Glyphs`, so a rule is `─` on FULL and `-` on ASCII without the
caller knowing.

## What this part does not own

The escape emitter, capability resolution, width measurement and the
`Renderable → String` path are all core (CR-21, CR-44). This part is a
naming convenience over them. That is why a bitmap can print exactly like
text-mode output without depending on anything here (BM-4, CR-43).
