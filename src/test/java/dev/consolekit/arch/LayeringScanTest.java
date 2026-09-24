package dev.consolekit.arch;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LayeringScanTest {

    @Test
    void nfr10_scanSeesTheMainSources() {
        assertTrue(JavaSource.mainSources().stream().anyMatch(s -> s.typeName().equals(Scans.ANSI)));
    }

    @Test
    void cr43_everyReferenceStaysOnAnAllowedEdge() {
        List<String> violations = new ArrayList<>();
        for (JavaSource source : JavaSource.mainSources()) {
            violations.addAll(Scans.layeringViolations(source));
        }
        assertEquals(List.of(), violations);
    }

    @Test
    void cr22_onlyTerminalPortReferencesJline() {
        List<String> offenders = JavaSource.mainSources().stream()
            .filter(Scans::referencesJline)
            .map(JavaSource::typeName)
            .filter(name -> !name.equals(Scans.TERMINAL_PORT))
            .toList();
        assertEquals(List.of(), offenders);
    }

    @Test
    void cr25_noLoggingFramework() {
        List<String> offenders = JavaSource.mainSources().stream()
            .filter(Scans::referencesLogging)
            .map(JavaSource::typeName)
            .toList();
        assertEquals(List.of(), offenders);
    }

    @Test
    void cr25_nfr8_buildDeclaresNoLoggingOrJansiDependency() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"), StandardCharsets.UTF_8).toLowerCase();
        for (String banned : new String[] {"slf4j", "log4j", "logback", "commons-logging", "jansi"}) {
            assertFalse(pom.contains(banned), banned);
        }
    }
}
