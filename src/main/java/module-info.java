module dev.consolekit {
    requires org.jline.terminal;

    // Export a package only in the commit that adds its first class.
    // javac rejects empty packages ("package is empty or does not exist").
    exports dev.consolekit.core;
}
