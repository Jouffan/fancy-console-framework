package dev.consolekit.arch;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * A Java compilation unit split into code and literals, for the structural source scans.
 *
 * <p>Unicode escapes are translated first (JLS 3.3), then comments are dropped, and every char, string and
 * text-block literal is decoded (JLS 3.10.7) and removed from the code. What is left in {@link #code()} is
 * only what the compiler reads as tokens, with whitespace around dots collapsed so a qualified name reads
 * as one word.
 */
final class JavaSource {

    static final Path MAIN = Path.of("src", "main", "java");

    private static final Pattern PACKAGE = Pattern.compile("\\bpackage\\s+([\\w.]+)\\s*;");

    private final String path;
    private final String code;
    private final List<String> literals;

    private JavaSource(String path, String code, List<String> literals) {
        this.path = path;
        this.code = code;
        this.literals = literals;
    }

    static JavaSource parse(String path, String text) {
        String translated = translateUnicodeEscapes(text);
        StringBuilder code = new StringBuilder(translated.length());
        List<String> literals = new ArrayList<>();
        int i = 0;
        int n = translated.length();
        while (i < n) {
            char c = translated.charAt(i);
            if (c == '/' && i + 1 < n && translated.charAt(i + 1) == '/') {
                while (i < n && translated.charAt(i) != '\n') i++;
            } else if (c == '/' && i + 1 < n && translated.charAt(i + 1) == '*') {
                int end = translated.indexOf("*/", i + 2);
                i = end < 0 ? n : end + 2;
                code.append(' ');
            } else if (translated.startsWith("\"\"\"", i)) {
                int start = translated.indexOf('\n', i + 3) + 1;
                int end = start;
                while (end < n && !(translated.startsWith("\"\"\"", end) && !escaped(translated, end))) end++;
                literals.add(decode(translated.substring(start, Math.min(end, n))));
                i = Math.min(end + 3, n);
                code.append(" \"\" ");
            } else if (c == '"' || c == '\'') {
                int end = i + 1;
                while (end < n && !(translated.charAt(end) == c && !escaped(translated, end))) end++;
                literals.add(decode(translated.substring(i + 1, Math.min(end, n))));
                i = Math.min(end + 1, n);
                code.append(" \"\" ");
            } else {
                code.append(c);
                i++;
            }
        }
        String collapsed = code.toString().replaceAll("\\s*\\.\\s*", ".");
        return new JavaSource(path, collapsed, List.copyOf(literals));
    }

    static JavaSource read(Path file) {
        try {
            return parse(file.toString().replace('\\', '/'), Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<JavaSource> mainSources() {
        try (Stream<Path> files = Files.walk(MAIN)) {
            return files.filter(p -> p.toString().endsWith(".java"))
                .filter(p -> !p.getFileName().toString().equals("module-info.java"))
                .sorted()
                .map(JavaSource::read)
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    String path() {
        return path;
    }

    String code() {
        return code;
    }

    List<String> literals() {
        return literals;
    }

    String packageName() {
        Matcher m = PACKAGE.matcher(code);
        return m.find() ? m.group(1) : "";
    }

    String typeName() {
        String file = path.substring(path.lastIndexOf('/') + 1);
        String simple = file.endsWith(".java") ? file.substring(0, file.length() - 5) : file;
        String pkg = packageName();
        return pkg.isEmpty() ? simple : pkg + "." + simple;
    }

    private static boolean escaped(String s, int at) {
        int backslashes = 0;
        for (int i = at - 1; i >= 0 && s.charAt(i) == '\\'; i--) backslashes++;
        return backslashes % 2 == 1;
    }

    static String translateUnicodeEscapes(String s) {
        StringBuilder out = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\\' && !escapedInRaw(s, i) && i + 1 < s.length() && s.charAt(i + 1) == 'u') {
                int j = i + 1;
                while (j < s.length() && s.charAt(j) == 'u') j++;
                if (j + 4 <= s.length()) {
                    out.append((char) Integer.parseInt(s.substring(j, j + 4), 16));
                    i = j + 4;
                    continue;
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    private static boolean escapedInRaw(String s, int at) {
        return escaped(s, at);
    }

    static String decode(String body) {
        StringBuilder out = new StringBuilder(body.length());
        int i = 0;
        while (i < body.length()) {
            char c = body.charAt(i);
            if (c != '\\' || i + 1 >= body.length()) {
                out.append(c);
                i++;
                continue;
            }
            char e = body.charAt(i + 1);
            if (e >= '0' && e <= '7') {
                int j = i + 1;
                int max = e <= '3' ? 3 : 2;
                while (j < body.length() && j < i + 1 + max && body.charAt(j) >= '0' && body.charAt(j) <= '7') j++;
                out.append((char) Integer.parseInt(body.substring(i + 1, j), 8));
                i = j;
                continue;
            }
            switch (e) {
                case 'b' -> out.append('\b');
                case 's' -> out.append(' ');
                case 't' -> out.append('\t');
                case 'n' -> out.append('\n');
                case 'f' -> out.append('\f');
                case 'r' -> out.append('\r');
                case '\n' -> { }
                default -> out.append(e);
            }
            i += 2;
        }
        return out.toString();
    }
}
