package com.pokernight.application.persistence;

import com.pokernight.application.room.RoomCode;

import java.util.List;

/** Port implemented by no-op, JSON-file, or future database adapters. */
public interface RoomSnapshotStore {
    List<RoomSnapshot> loadAll();
    void save(RoomSnapshot snapshot);
    void delete(RoomCode roomCode);
}
