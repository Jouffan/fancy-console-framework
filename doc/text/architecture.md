# ConsoleKit Text — Architecture

Part 2 of 4. Requirements: [text/requirements.md](requirements.md).
See [the map](../architecture.md).

Three public types, all in `dev.consolekit.text`, all facades over the
core output path (CR-44). No state, no threads, no terminal library.

```
dev.consolekit.text       Text, Styler, Snippets
```

There is no `text.internal`: everything this part needs below its
surface is `dev.consolekit.core.internal` (`Ansi`, `Glyphs`), which every
part may use (CR-43).

## `Text`

Static `String`-returning helpers designed for `import static`: sixteen
named colours, `fg`/`bg`, six attributes, theme roles, `styled(Style, s)`,
each with a `format` overload. Stateless; reads capabilities from
`ConsoleRuntime`, which resolves them through the JDK only; returns the
input unchanged when escapes can't be rendered; closes each span with its
specific SGR-off code (never `SGR 0`); registers the SGR-reset shutdown
hook on first escape.

One package-private seam, `Text.styledWith(Capabilities, Style, String)`,
is where all three types meet and where tests inject capabilities.

## `Styler`

An immutable value over a `Theme`. Same role-method names as `Text`, as
instance methods:

```java
Styler s = Styler.of(Theme.DEFAULT)
                 .with(Theme.Role.HIGHLIGHT, Style.fg(Color.BRIGHT_CYAN).bold());
System.out.println("found " + s.highlight("42") + " matches");
```

Implementation is a one-line delegation to `Text.styledWith(caps, style,
text)`. `Styler` exists so that "the colour set in the style instance" is
a value the application can pass around, inject, or swap per environment,
without `Text` growing mutable state.

## `Snippets`

Glyph-tier-aware one-liners, all colourable, all returning `String`:
`line(Color)`, `line(Color, width)`, `rule(Color, title)`, `ok`/`fail`/
`warn`, `bullet`, `banner`, `bar(Color, fraction, width)`. Plus a
`print…` twin for each that writes to `System.out` — the only printing
methods in this part, and deliberately trivial. Width, when not given,
is the core ambient width (CR-45, TX-15). Glyphs come from
`core.internal.Glyphs`, so a rule is `─` on FULL and `-` on ASCII without
the caller knowing.

## What this part does not own

The escape emitter, capability resolution, width measurement, the
ambient width and the `Renderable → String` path are all core (CR-21,
CR-44, CR-45). This part is a naming convenience over them. That is why
a bitmap can print exactly like text-mode output without depending on
anything here (BM-4, CR-43).
