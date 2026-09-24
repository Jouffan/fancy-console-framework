package dev.consolekit.arch;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The structural scan rules as functions over one {@link JavaSource}, testable on a snippet. */
final class Scans {

    static final String ANSI = "dev.consolekit.core.internal.Ansi";
    static final String GLYPHS = "dev.consolekit.core.internal.Glyphs";
    static final String TERMINAL_PORT = "dev.consolekit.canvas.internal.TerminalPort";

    private static final Pattern REFERENCE = Pattern.compile("\\bdev\\.consolekit(?:\\.\\w+)*");
    private static final Pattern JLINE = Pattern.compile("\\borg\\.jline\\b");
    private static final Pattern LOGGING = Pattern.compile(
        "\\b(?:org\\.slf4j|org\\.apache\\.logging|org\\.apache\\.log4j|java\\.util\\.logging|ch\\.qos\\.logback"
            + "|org\\.apache\\.commons\\.logging|System\\.Logger|System\\.getLogger)\\b");
    private static final Pattern ESC_CAST = Pattern.compile(
        "\\(\\s*char\\s*\\)\\s*\\(?\\s*(?:27|0[xX]0*1[bB]|0+33|0[bB]0*11011)[lL]?\\b");

    private Scans() {
    }

    /** Every reference to a {@code dev.consolekit} package that the file's part may not make. */
    static List<String> layeringViolations(JavaSource source) {
        List<String> violations = new ArrayList<>();
        String from = source.packageName();
        String fromPart = part(from);
        Matcher m = REFERENCE.matcher(source.code());
        while (m.find()) {
            String target = m.group();
            if (target.equals(from) || target.equals("dev.consolekit")) continue;
            if (!allowed(fromPart, target)) violations.add(source.path() + ": " + from + " -> " + target);
        }
        if (!isPart(fromPart)) violations.add(source.path() + ": " + from + " is not under a part prefix");
        return violations;
    }

    private static boolean allowed(String fromPart, String target) {
        String toPart = part(target);
        if (!isPart(toPart)) return false;
        if (fromPart.equals("tools")) return true;
        if (toPart.equals("tools")) return false;
        if (toPart.equals(fromPart)) return true;
        if (toPart.equals("core")) return true;
        return fromPart.equals("canvas") && toPart.equals("bitmap")
            && !(target + ".").startsWith("dev.consolekit.bitmap.internal.");
    }

    private static boolean isPart(String part) {
        return switch (part) {
            case "core", "text", "bitmap", "canvas", "tools" -> true;
            default -> false;
        };
    }

    static String part(String qualified) {
        String prefix = "dev.consolekit.";
        if (!qualified.startsWith(prefix)) return "";
        String rest = qualified.substring(prefix.length());
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }

    /** JLine may be referenced only by {@code canvas.internal.TerminalPort}. */
    static boolean referencesJline(JavaSource source) {
        return JLINE.matcher(source.code()).find();
    }

    /** Whether the file references a logging framework. */
    static boolean referencesLogging(JavaSource source) {
        return LOGGING.matcher(source.code()).find();
    }

    /** An escape byte in a literal, or a {@code char} cast of 27 in any radix. */
    static boolean containsEscape(JavaSource source) {
        for (String literal : source.literals()) {
            if (literal.indexOf('\u001b') >= 0 || literal.indexOf('\u009b') >= 0) return true;
        }
        return ESC_CAST.matcher(source.code()).find();
    }

    /** A non-ASCII character in a literal. */
    static boolean containsNonAsciiLiteral(JavaSource source) {
        for (String literal : source.literals()) {
            for (int i = 0; i < literal.length(); i++) {
                if (literal.charAt(i) > 0x7F) return true;
            }
        }
        return false;
    }
}
