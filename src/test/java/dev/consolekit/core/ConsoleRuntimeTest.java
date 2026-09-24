package dev.consolekit.core;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConsoleRuntimeTest {

    @BeforeEach
    @AfterEach
    void reset() {
        ConsoleRuntime.reset();
    }

    @Test
    void cr14_configuredOptionsApply() {
        ConsoleRuntime.configure(ConsoleOptions.builder().colorDepth(ColorDepth.ANSI256).build());
        assertEquals(ColorDepth.ANSI256, ConsoleRuntime.capabilities().colorDepth());
    }

    @Test
    void cr14_configuringTwiceFailsLoudly() {
        ConsoleRuntime.configure(ConsoleOptions.builder().build());
        assertThrows(IllegalStateException.class, () -> ConsoleRuntime.configure(ConsoleOptions.builder().build()));
    }

    @Test
    void cr14_configuringAfterOutputFailsLoudly() {
        ConsoleRuntime.capabilities();
        assertThrows(IllegalStateException.class, () -> ConsoleRuntime.configure(ConsoleOptions.builder().build()));
    }

    @Test
    void cr14_nullOptionsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> ConsoleRuntime.configure(null));
    }

    @Test
    void cr11_capabilitiesAreResolvedOnce() {
        assertSame(ConsoleRuntime.capabilities(), ConsoleRuntime.capabilities());
    }

    @Test
    void nfr12_sessionOpensOnce() {
        AtomicInteger opened = new AtomicInteger();
        StringBuilder first = ConsoleRuntime.session(StringBuilder.class, () -> {
            opened.incrementAndGet();
            return new StringBuilder("session");
        });
        StringBuilder second = ConsoleRuntime.session(StringBuilder.class, StringBuilder::new);
        assertSame(first, second);
        assertEquals(1, opened.get());
    }

    @Test
    void nfr12_sessionIfOpenIsEmptyBeforeOpen() {
        assertEquals(Optional.empty(), ConsoleRuntime.sessionIfOpen(StringBuilder.class));
        StringBuilder s = ConsoleRuntime.session(StringBuilder.class, StringBuilder::new);
        assertSame(s, ConsoleRuntime.sessionIfOpen(StringBuilder.class).orElseThrow());
    }

    @Test
    void nfr12_sessionOpenerMustReturnAValue() {
        assertThrows(IllegalArgumentException.class, () -> ConsoleRuntime.session(StringBuilder.class, () -> null));
    }

    @Test
    void cr24_warnOnceWritesOnlyTheFirstWarning() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ConsoleRuntime.warnSink(new PrintStream(bytes, true, StandardCharsets.UTF_8));

        ConsoleRuntime.warnOnce("first");
        ConsoleRuntime.warnOnce("second");

        String written = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(written.contains("first"), written);
        assertFalse(written.contains("second"), written);
    }
}
