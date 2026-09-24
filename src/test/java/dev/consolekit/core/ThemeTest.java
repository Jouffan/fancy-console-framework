package dev.consolekit.core;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ThemeTest {

    @Test
    void cr5_defaultMapsEveryRoleToAStyle() {
        for (Theme.Role role : Theme.Role.values()) {
            assertNotEquals(Style.NONE, Theme.DEFAULT.style(role), role.name());
        }
        assertEquals(Color.GREEN, Theme.DEFAULT.style(Theme.Role.SUCCESS).fg());
        assertEquals(Color.RED, Theme.DEFAULT.style(Theme.Role.ERROR).fg());
    }

    @Test
    void cr5_monochromeUsesNoColour() {
        for (Theme.Role role : Theme.Role.values()) {
            Style s = Theme.MONOCHROME.style(role);
            assertFalse(s.hasFg(), role.name());
            assertFalse(s.hasBg(), role.name());
        }
    }

    @Test
    void cr5_withReplacesOneRoleAndKeepsTheOriginal() {
        Theme custom = Theme.DEFAULT.with(Theme.Role.HIGHLIGHT, Style.fg(Color.MAGENTA).underline());
        assertEquals(Style.fg(Color.MAGENTA).underline(), custom.style(Theme.Role.HIGHLIGHT));
        assertEquals(Theme.DEFAULT.style(Theme.Role.ERROR), custom.style(Theme.Role.ERROR));
        assertNotEquals(custom.style(Theme.Role.HIGHLIGHT), Theme.DEFAULT.style(Theme.Role.HIGHLIGHT));
    }

    @Test
    void cr5_ofFillsMissingRolesWithNone() {
        Theme t = Theme.of(Map.of(Theme.Role.ERROR, Style.fg(Color.RED)));
        assertEquals(Style.fg(Color.RED), t.style(Theme.Role.ERROR));
        assertEquals(Style.NONE, t.style(Theme.Role.INFO));
    }

    @Test
    void cr5_nullArgumentsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> Theme.of(null));
        assertThrows(IllegalArgumentException.class, () -> Theme.DEFAULT.with(null, Style.NONE));
        assertThrows(IllegalArgumentException.class, () -> Theme.DEFAULT.with(Theme.Role.INFO, null));
    }
}
