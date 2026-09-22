package com.anightdazingzoroark.squirrellauncher.launcher;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/** Mirrors backend console output to a line listener without suppressing the console. */
final class LauncherOutputBridge implements AutoCloseable {
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;
    private final PrintStream bridgedOut;
    private final PrintStream bridgedErr;

    LauncherOutputBridge(@NotNull Consumer<String> listener) {
        this.bridgedOut = createStream(this.originalOut, listener);
        this.bridgedErr = createStream(this.originalErr, listener);
        System.setOut(this.bridgedOut);
        System.setErr(this.bridgedErr);
    }

    @Override
    public void close() {
        if (System.out == this.bridgedOut) System.setOut(this.originalOut);
        if (System.err == this.bridgedErr) System.setErr(this.originalErr);
        this.bridgedOut.flush();
        this.bridgedErr.flush();
    }

    private static PrintStream createStream(PrintStream original, Consumer<String> listener) {
        return new PrintStream(new LineOutputStream(original, listener), true, StandardCharsets.UTF_8);
    }

    private static final class LineOutputStream extends OutputStream {
        private final PrintStream original;
        private final Consumer<String> listener;
        private final ByteArrayOutputStream line = new ByteArrayOutputStream();

        private LineOutputStream(PrintStream original, Consumer<String> listener) {
            this.original = original;
            this.listener = listener;
        }

        @Override
        public synchronized void write(int value) {
            this.original.write(value);
            if (value == '\n') publishLine();
            else if (value != '\r') this.line.write(value);
        }

        @Override
        public synchronized void write(byte[] bytes, int offset, int length) {
            for (int index = offset; index < offset + length; index++) write(bytes[index]);
        }

        @Override
        public synchronized void flush() {
            this.original.flush();
        }

        @Override
        public synchronized void close() throws IOException {
            if (this.line.size() > 0) publishLine();
            flush();
        }

        private void publishLine() {
            String value = this.line.toString(StandardCharsets.UTF_8);
            this.line.reset();
            try {
                this.listener.accept(value);
            }
            catch (RuntimeException ignored) {
                // A display failure must never interrupt a backend operation.
            }
        }
    }
}
