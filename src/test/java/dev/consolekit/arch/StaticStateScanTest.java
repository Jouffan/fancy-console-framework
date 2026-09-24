package dev.consolekit.arch;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StaticStateScanTest {

    private static final String RUNTIME = "dev.consolekit.core.ConsoleRuntime";

    @Test
    void nfr12_onlyConsoleRuntimeHasStaticMutableFields() {
        List<String> offenders = new ArrayList<>();
        for (Class<?> c : MainClasses.all()) {
            if (c.getName().equals(RUNTIME)) continue;
            for (Field f : c.getDeclaredFields()) {
                int mods = f.getModifiers();
                if (!Modifier.isStatic(mods) || f.isSynthetic()) continue;
                if (!Modifier.isFinal(mods)) offenders.add(c.getName() + "." + f.getName() + " is not final");
            }
        }
        assertEquals(List.of(), offenders);
    }

    @Test
    void nfr12_noStaticFinalMutableHolderOutsideConsoleRuntime() throws IllegalAccessException {
        List<String> offenders = new ArrayList<>();
        for (Class<?> c : MainClasses.all()) {
            if (c.getName().equals(RUNTIME)) continue;
            for (Field f : c.getDeclaredFields()) {
                int mods = f.getModifiers();
                if (!Modifier.isStatic(mods) || !Modifier.isFinal(mods) || f.isSynthetic()) continue;
                Class<?> type = f.getType();
                String typeName = type.getName();
                if (typeName.startsWith("java.util.concurrent") || typeName.equals("java.lang.StringBuilder")) {
                    offenders.add(c.getName() + "." + f.getName());
                    continue;
                }
                if (Collection.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type)) {
                    f.setAccessible(true);
                    Object value = f.get(null);
                    if (value != null && !isUnmodifiable(value)) offenders.add(c.getName() + "." + f.getName());
                }
            }
        }
        assertEquals(List.of(), offenders);
    }

    private static boolean isUnmodifiable(Object value) {
        String name = value.getClass().getName();
        return name.startsWith("java.util.ImmutableCollections$")
            || name.startsWith("java.util.Collections$Unmodifiable");
    }
}
