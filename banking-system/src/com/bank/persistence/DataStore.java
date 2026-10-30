package com.bank.persistence;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * GENERIC CLASS + FILE I/O.
 *
 * DataStore<T> knows how to persist a list of ANY type T as a text file, one
 * item per line. It does not know what T is: the caller passes an "encoder"
 * (T -> String) and a "decoder" (String -> T) as lambdas / method references.
 * The same class therefore stores Accounts AND Transactions.
 *
 * I/O topics shown here
 * ---------------------
 * - BufferedReader / BufferedWriter for efficient line-based I/O
 * - try-with-resources: the reader/writer is closed automatically, even when
 *   an exception is thrown (no manual finally/close needed)
 * - Crash-safe saving: write to a temp file, then atomically move it over the
 *   real file, so a crash mid-write can never leave a half-written ledger.
 */
public class DataStore<T> {

    private final Path file;
    private final String header;
    private final Function<T, String> encoder;
    private final Function<String, T> decoder;

    public DataStore(Path file, String header, Function<T, String> encoder, Function<String, T> decoder) {
        this.file = file;
        this.header = header;
        this.encoder = encoder;
        this.decoder = decoder;
    }

    /** Reads every valid line. Corrupt lines are reported and skipped, not fatal. */
    public List<T> loadAll() throws IOException {
        List<T> items = new ArrayList<>();
        if (!Files.exists(file)) {
            return items;                       // first run: nothing saved yet
        }
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (lineNumber == 1 && line.equals(header)) {
                    continue;                   // skip the CSV header row
                }
                if (line.isBlank()) {
                    continue;
                }
                try {
                    items.add(decoder.apply(line));
                } catch (RuntimeException e) {
                    System.err.printf("  [warn] %s line %d skipped (%s)%n",
                            file.getFileName(), lineNumber, e.getMessage());
                }
            }
        }                                       // reader.close() happens here automatically
        return items;
    }

    /** Overwrites the file with the given items (header + one line each). */
    public void saveAll(Collection<T> items) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");

        try (BufferedWriter writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
            writer.write(header);
            writer.newLine();
            for (T item : items) {
                writer.write(encoder.apply(item));
                writer.newLine();
            }
        }
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);   // fallback
        }
    }
}
