package dev.consolekit.core;

import java.util.Map;

/**
 * Semantic roles mapped to styles, so nothing downstream hard-codes a colour. Immutable.
 *
 * {@snippet :
 * Theme mine = Theme.DEFAULT.with(Theme.Role.HIGHLIGHT, Style.fg(Color.BRIGHT_CYAN).bold());
 * Style error = mine.style(Theme.Role.ERROR);
 * }
 */
public final class Theme {

    /**
     * What a piece of text means, rather than how it looks.
     *
     * {@snippet :
     * Style s = Theme.MONOCHROME.style(Theme.Role.MUTED);
     * }
     */
    public enum Role {
        SUCCESS, WARNING, ERROR, INFO, MUTED, HIGHLIGHT, BORDER, SELECTION
    }

    public static final Theme DEFAULT = new Theme(new Style[] {
        Style.fg(Color.GREEN),
        Style.fg(Color.YELLOW),
        Style.fg(Color.RED).bold(),
        Style.fg(Color.CYAN),
        Style.fg(Color.BRIGHT_BLACK),
        Style.fg(Color.BRIGHT_CYAN).bold(),
        Style.fg(Color.BRIGHT_BLACK),
        Style.NONE.reverse(),
    });

    public static final Theme MONOCHROME = new Theme(new Style[] {
        Style.NONE.bold(),
        Style.NONE.bold(),
        Style.NONE.bold().underline(),
        Style.NONE.italic(),
        Style.NONE.dim(),
        Style.NONE.bold(),
        Style.NONE.dim(),
        Style.NONE.reverse(),
    });

    private final Style[] styles;

    private Theme(Style[] styles) {
        this.styles = styles;
    }

    /** A theme from explicit role styles; a role not in the map gets {@link Style#NONE}. */
    public static Theme of(Map<Role, Style> styles) {
        if (styles == null) throw new IllegalArgumentException("styles must not be null");
        Style[] all = new Style[Role.values().length];
        for (Role role : Role.values()) {
            Style s = styles.get(role);
            all[role.ordinal()] = s == null ? Style.NONE : s;
        }
        return new Theme(all);
    }

    public Style style(Role role) {
        return styles[role.ordinal()];
    }

    public Theme with(Role role, Style style) {
        if (role == null) throw new IllegalArgumentException("role must not be null");
        if (style == null) throw new IllegalArgumentException("style must not be null");
        Style[] copy = styles.clone();
        copy[role.ordinal()] = style;
        return new Theme(copy);
    }
}
