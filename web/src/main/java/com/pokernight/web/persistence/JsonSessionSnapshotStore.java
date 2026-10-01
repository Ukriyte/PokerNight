package com.pokernight.web.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pokernight.web.session.SessionSnapshot;
import com.pokernight.web.session.SessionSnapshotStore;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public final class JsonSessionSnapshotStore implements SessionSnapshotStore {
    private final Path file;
    private final ObjectMapper json;

    public JsonSessionSnapshotStore(Path file, ObjectMapper json) {
        this.file = file;
        this.json = json;
    }

    @Override
    public synchronized List<SessionSnapshot> loadAll() {
        if (!Files.exists(file)) return List.of();
        try { return List.copyOf(json.readValue(file.toFile(), new TypeReference<List<SessionSnapshot>>() { })); }
        catch (IOException exception) { throw new IllegalStateException("could not load sessions from " + file, exception); }
    }

    @Override
    public synchronized void saveAll(List<SessionSnapshot> sessions) {
        try {
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            json.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), sessions);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("could not save sessions to " + file, exception);
        }
    }
}
