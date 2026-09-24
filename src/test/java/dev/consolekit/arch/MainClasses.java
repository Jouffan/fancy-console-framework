package dev.consolekit.arch;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** Every class compiled from {@code src/main/java}, loaded without initialisation. */
final class MainClasses {

    private static final Path CLASSES = Path.of("target", "classes");

    private MainClasses() {
    }

    static List<Class<?>> all() {
        try (Stream<Path> files = Files.walk(CLASSES)) {
            return files.map(CLASSES::relativize)
                .map(Path::toString)
                .filter(name -> name.endsWith(".class") && !name.endsWith("module-info.class"))
                .map(name -> name.substring(0, name.length() - 6).replace('/', '.').replace('\\', '.'))
                .sorted()
                .<Class<?>>map(MainClasses::load)
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Class<?> load(String name) {
        try {
            return Class.forName(name, false, MainClasses.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("cannot load " + name, e);
        }
    }
}
