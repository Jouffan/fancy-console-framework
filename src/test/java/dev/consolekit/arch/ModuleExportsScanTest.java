package dev.consolekit.arch;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModuleExportsScanTest {

    private static final Pattern EXPORTS = Pattern.compile("\\bexports\\s+([\\w.]+)");
    private static final Pattern MODULE = Pattern.compile("\\bmodule\\s+([\\w.]+)\\s*\\{");

    private static JavaSource moduleInfo() {
        return JavaSource.read(JavaSource.MAIN.resolve("module-info.java"));
    }

    private static List<String> exports() {
        List<String> exported = new ArrayList<>();
        Matcher m = EXPORTS.matcher(moduleInfo().code());
        while (m.find()) exported.add(m.group(1));
        return exported;
    }

    @Test
    void nfr9b_moduleIsDevConsolekit() {
        Matcher m = MODULE.matcher(moduleInfo().code());
        assertTrue(m.find());
        assertEquals("dev.consolekit", m.group(1));
    }

    @Test
    void nfr9b_noInternalPackageIsExported() {
        List<String> internal = exports().stream()
            .filter(p -> p.endsWith(".internal") || p.contains(".internal."))
            .toList();
        assertEquals(List.of(), internal);
    }

    @Test
    void nfr9b_everyExportedPackageHasAClass() {
        Set<String> packages = new TreeSet<>();
        for (JavaSource source : JavaSource.mainSources()) packages.add(source.packageName());
        for (String exported : exports()) {
            assertTrue(packages.contains(exported), exported);
        }
    }

    @Test
    void nfr9b_coreApiIsExported() {
        assertTrue(exports().contains("dev.consolekit.core"));
    }

    @Test
    void nfr8_onlyJlineIsRequired() throws IOException {
        String code = moduleInfo().code();
        Matcher m = Pattern.compile("\\brequires\\s+(?:transitive\\s+|static\\s+)*([\\w.]+)").matcher(code);
        while (m.find()) {
            String required = m.group(1);
            assertTrue(required.startsWith("org.jline") || required.startsWith("java."), required);
        }
        assertTrue(Files.readString(Path.of("pom.xml"), StandardCharsets.UTF_8).contains("<artifactId>jline-terminal"));
    }
}
