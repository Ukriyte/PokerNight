package com.pokernight.web.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pokernight.application.command.CommandReceiptStore;
import com.pokernight.application.command.PersistedCommand;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public final class JsonCommandReceiptStore implements CommandReceiptStore {
    private final Path file;
    private final ObjectMapper json;

    public JsonCommandReceiptStore(Path file, ObjectMapper json) {
        this.file = file;
        this.json = json;
    }

    @Override
    public synchronized List<PersistedCommand> loadAll() {
        if (!Files.exists(file)) return List.of();
        try {
            return List.copyOf(json.readValue(file.toFile(), new TypeReference<List<PersistedCommand>>() { }));
        } catch (IOException exception) {
            throw new IllegalStateException("could not load command receipts from " + file, exception);
        }
    }

    @Override
    public synchronized void saveAll(List<PersistedCommand> commands) {
        try {
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            json.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), commands);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("could not save command receipts to " + file, exception);
        }
    }
}
