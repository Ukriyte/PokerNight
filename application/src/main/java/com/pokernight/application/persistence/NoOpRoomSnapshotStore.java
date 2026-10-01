package com.pokernight.application.persistence;

import com.pokernight.application.room.RoomCode;
import java.util.List;

public final class NoOpRoomSnapshotStore implements RoomSnapshotStore {
    @Override public List<RoomSnapshot> loadAll() { return List.of(); }
    @Override public void save(RoomSnapshot snapshot) { }
    @Override public void delete(RoomCode roomCode) { }
}
