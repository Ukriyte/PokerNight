package com.pokernight.web.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pokernight.application.persistence.RoomSnapshot;
import com.pokernight.application.persistence.RoomSnapshotStore;
import com.pokernight.application.room.RoomCode;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** One atomically replaced JSON file per room. */
public final class JsonRoomSnapshotStore implements RoomSnapshotStore {
    private final Path directory;
    private final ObjectMapper json;

    public JsonRoomSnapshotStore(Path directory, ObjectMapper json) {
        this.directory = directory;
        this.json = json;
        createDirectory();
    }

    @Override
    public synchronized List<RoomSnapshot> loadAll() {
        createDirectory();
        List<RoomSnapshot> snapshots = new ArrayList<>();
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".json")).toList()) {
                snapshots.add(json.readValue(file.toFile(), RoomSnapshot.class));
            }
            return List.copyOf(snapshots);
        } catch (IOException exception) {
            throw persistenceFailure("could not load room snapshots", exception);
        }
    }

    @Override
    public synchronized void save(RoomSnapshot snapshot) {
        createDirectory();
        Path target = file(snapshot.roomCode());
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            json.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), snapshot);
            atomicReplace(temporary, target);
        } catch (IOException exception) {
            throw persistenceFailure("could not save room " + snapshot.roomCode(), exception);
        }
    }

    @Override
    public synchronized void delete(RoomCode roomCode) {
        try {
            Files.deleteIfExists(file(roomCode.value()));
        } catch (IOException exception) {
            throw persistenceFailure("could not delete room " + roomCode, exception);
        }
    }

    private void atomicReplace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path file(String code) { return directory.resolve(code + ".json"); }

    private void createDirectory() {
        try { Files.createDirectories(directory); }
        catch (IOException exception) { throw persistenceFailure("could not create snapshot directory", exception); }
    }

    private IllegalStateException persistenceFailure(String message, Exception cause) {
        return new IllegalStateException(message + " at " + directory.toAbsolutePath(), cause);
    }
}
