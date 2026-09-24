package dev.consolekit.arch;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import dev.consolekit.core.Renderable;
import dev.consolekit.core.Rendering;

import static org.junit.jupiter.api.Assertions.*;

class ToStringDelegationTest {

    private static final Pattern DELEGATION = Pattern.compile(
        "\\bString\\s+toString\\s*\\(\\s*\\)\\s*\\{\\s*return\\s+(?:dev\\.consolekit\\.core\\.)?"
            + "Rendering\\.toString\\s*\\(\\s*this\\s*\\)\\s*;\\s*}");

    private static List<Class<?>> shippedRenderables() {
        return MainClasses.all().stream()
            .filter(Renderable.class::isAssignableFrom)
            .filter(c -> !c.isInterface() && !Modifier.isAbstract(c.getModifiers()))
            .toList();
    }

    @Test
    void cr44_thereAreShippedRenderablesToCheck() {
        assertFalse(shippedRenderables().isEmpty());
    }

    @Test
    void cr44_everyShippedRenderableDeclaresToString() {
        List<String> missing = new ArrayList<>();
        for (Class<?> c : shippedRenderables()) {
            try {
                c.getDeclaredMethod("toString");
            } catch (NoSuchMethodException e) {
                missing.add(c.getName());
            }
        }
        assertEquals(List.of(), missing);
    }

    @Test
    void cr44_everyShippedToStringDelegatesToRendering() {
        List<String> offenders = new ArrayList<>();
        for (Class<?> c : shippedRenderables()) {
            String top = c.getName().replaceAll("\\$.*", "");
            long renderablesInFile = shippedRenderables().stream()
                .filter(other -> other.getName().replaceAll("\\$.*", "").equals(top))
                .count();
            JavaSource source = JavaSource.read(JavaSource.MAIN.resolve(top.replace('.', '/') + ".java"));
            Matcher m = DELEGATION.matcher(source.code());
            long delegations = 0;
            while (m.find()) delegations++;
            if (delegations < renderablesInFile) offenders.add(c.getName());
        }
        assertEquals(List.of(), offenders);
    }

    @Test
    void cr44_toStringEqualsRenderingForConstructibleRenderables() throws ReflectiveOperationException {
        for (Class<?> c : shippedRenderables()) {
            Constructor<?> noArgs;
            try {
                noArgs = c.getDeclaredConstructor();
            } catch (NoSuchMethodException e) {
                continue;
            }
            noArgs.setAccessible(true);
            Renderable r = (Renderable) noArgs.newInstance();
            assertEquals(Rendering.toString(r), r.toString(), c.getName());
        }
    }
}
