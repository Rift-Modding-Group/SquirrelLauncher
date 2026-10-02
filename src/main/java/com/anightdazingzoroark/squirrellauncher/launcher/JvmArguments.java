package com.anightdazingzoroark.squirrellauncher.launcher;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts between editable JVM argument text and the individual arguments required by ProcessBuilder.
 * */
public final class JvmArguments {
    private JvmArguments() {}

    @NotNull
    public static List<String> parse(@NotNull String text) {
        List<String> arguments = new ArrayList<>();
        StringBuilder argument = new StringBuilder();
        char quote = 0;
        boolean argumentStarted = false;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == 0 || (Character.isISOControl(character) && !Character.isWhitespace(character))) {
                throw new IllegalArgumentException("JVM arguments cannot contain control characters.");
            }
            if (quote != 0) {
                if (character == quote) {
                    quote = 0;
                    continue;
                }
                if (character == '\\' && quote == '"' && index + 1 < text.length()) {
                    char next = text.charAt(index + 1);
                    if (next == '"' || next == '\\') {
                        argument.append(next);
                        index++;
                        continue;
                    }
                }
                argument.append(character);
                continue;
            }
            if (Character.isWhitespace(character)) {
                if (argumentStarted) {
                    arguments.add(argument.toString());
                    argument.setLength(0);
                    argumentStarted = false;
                }
                continue;
            }
            if (character == '\'' || character == '"') {
                quote = character;
                argumentStarted = true;
                continue;
            }
            if (character == '\\' && index + 1 < text.length()) {
                char next = text.charAt(index + 1);
                if (Character.isWhitespace(next) || next == '\'' || next == '"' || next == '\\') {
                    argument.append(next);
                    argumentStarted = true;
                    index++;
                    continue;
                }
            }
            argument.append(character);
            argumentStarted = true;
        }
        if (quote != 0) throw new IllegalArgumentException("JVM arguments contain an unterminated quote.");
        if (argumentStarted) arguments.add(argument.toString());
        return List.copyOf(arguments);
    }

    @NotNull
    public static String format(@NotNull List<String> arguments) {
        StringBuilder text = new StringBuilder();
        for (String argument : arguments) {
            if (!text.isEmpty()) text.append(' ');
            boolean requiresQuotes = argument.isEmpty();
            for (int index = 0; index < argument.length() && !requiresQuotes; index++) {
                char character = argument.charAt(index);
                requiresQuotes = Character.isWhitespace(character) || character == '\'' || character == '"';
            }
            if (!requiresQuotes) {
                text.append(argument);
                continue;
            }
            text.append('"');
            for (int index = 0; index < argument.length(); index++) {
                char character = argument.charAt(index);
                if (character == '"' || character == '\\') text.append('\\');
                text.append(character);
            }
            text.append('"');
        }
        return text.toString();
    }

    @NotNull
    public static List<String> withMemory(
            @NotNull List<String> arguments,
            int minimumMemoryMegabytes,
            int maximumMemoryGigabytes
    ) {
        if (minimumMemoryMegabytes < 1 || maximumMemoryGigabytes < 1) {
            throw new IllegalArgumentException("JVM memory allocations must be positive.");
        }
        List<String> updatedArguments = new ArrayList<>(arguments.size() + 2);
        updatedArguments.add("-Xms" + minimumMemoryMegabytes + "M");
        updatedArguments.add("-Xmx" + maximumMemoryGigabytes + "G");
        for (String argument : arguments) {
            if (!JvmArguments.isMemoryArgument(argument)) updatedArguments.add(argument);
        }
        return List.copyOf(updatedArguments);
    }

    public static boolean isMemoryArgument(@NotNull String argument) {
        return argument.startsWith("-Xms") || argument.startsWith("-Xmx");
    }

    public static int minimumMemoryMegabytes(@NotNull List<String> arguments) {
        return JvmArguments.memoryMegabytes(arguments, "-Xms");
    }

    public static int maximumMemoryGigabytes(@NotNull List<String> arguments) {
        int memoryMegabytes = JvmArguments.memoryMegabytes(arguments, "-Xmx");
        if (memoryMegabytes % 1024 != 0) {
            throw new IllegalArgumentException("Maximum memory must be a whole number of gigabytes.");
        }
        return memoryMegabytes / 1024;
    }

    private static int memoryMegabytes(@NotNull List<String> arguments, @NotNull String prefix) {
        String memoryArgument = null;
        for (String argument : arguments) {
            if (!argument.startsWith(prefix)) continue;
            if (memoryArgument != null) {
                throw new IllegalArgumentException("JVM arguments contain more than one " + prefix + " option.");
            }
            memoryArgument = argument;
        }
        if (memoryArgument == null) throw new IllegalArgumentException("JVM arguments must contain " + prefix + ".");
        String memoryText = memoryArgument.substring(prefix.length());
        if (memoryText.isEmpty()) throw new IllegalArgumentException("Invalid JVM memory argument: " + memoryArgument);
        char suffix = Character.toUpperCase(memoryText.charAt(memoryText.length() - 1));
        boolean hasSuffix = suffix == 'K' || suffix == 'M' || suffix == 'G' || suffix == 'T';
        String numberText = hasSuffix ? memoryText.substring(0, memoryText.length() - 1) : memoryText;
        try {
            long value = Long.parseLong(numberText);
            if (value < 1) throw new NumberFormatException();
            long memoryMegabytes = switch (hasSuffix ? suffix : 'B') {
                case 'K' -> value % 1024L == 0 ? value / 1024L : -1L;
                case 'M' -> value;
                case 'G' -> Math.multiplyExact(value, 1024L);
                case 'T' -> Math.multiplyExact(value, 1024L * 1024L);
                default -> value % (1024L * 1024L) == 0 ? value / (1024L * 1024L) : -1L;
            };
            if (memoryMegabytes < 1 || memoryMegabytes > Integer.MAX_VALUE) throw new NumberFormatException();
            return (int) memoryMegabytes;
        }
        catch (ArithmeticException | NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid JVM memory argument: " + memoryArgument, exception);
        }
    }
}
