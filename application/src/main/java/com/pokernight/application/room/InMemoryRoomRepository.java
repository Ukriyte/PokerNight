package com.pokernight.application.room;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Replaceable in-memory adapter. RoomManager never depends on its storage details. */
public final class InMemoryRoomRepository implements RoomRepository {
    private final ConcurrentHashMap<RoomCode, Room> rooms = new ConcurrentHashMap<>();

    @Override
    public boolean create(Room room) {
        return rooms.putIfAbsent(room.code(), room) == null;
    }

    @Override
    public Optional<Room> find(RoomCode code) {
        return Optional.ofNullable(rooms.get(code));
    }

    @Override
    public Collection<Room> findAll() {
        return List.copyOf(rooms.values());
    }

    @Override
    public boolean delete(RoomCode code) {
        return rooms.remove(code) != null;
    }
}
