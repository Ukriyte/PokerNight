package com.pokernight.application.room;

import java.util.Collection;
import java.util.Optional;

interface RoomRepository {
    boolean create(Room room);

    Optional<Room> find(RoomCode code);

    Collection<Room> findAll();

    boolean delete(RoomCode code);
}
