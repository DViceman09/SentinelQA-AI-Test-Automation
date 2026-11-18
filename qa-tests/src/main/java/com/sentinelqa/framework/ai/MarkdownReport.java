package com.sentinelqa.framework.ai;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Appends rows to a Markdown table under target/, which CI pastes into the GitHub job summary. */
final class MarkdownReport {

    private MarkdownReport() {}

    static synchronized void appendRow(Path file, String header, String... cells) {
        try {
            Files.createDirectories(file.getParent());
            if (!Files.exists(file)) {
                int columns = header.split("\\|", -1).length - 2;
                Files.writeString(file, header + "\n|" + "---|".repeat(columns) + "\n");
            }
            StringBuilder row = new StringBuilder("|");
            for (String cell : cells) row.append(' ').append(cell.replace("|", "\\|").replace("\n", " ")).append(" |");
            Files.writeString(file, row + "\n", StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("[report] could not write " + file + ": " + e.getMessage());
        }
    }
}
