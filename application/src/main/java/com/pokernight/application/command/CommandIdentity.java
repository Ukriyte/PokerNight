package com.pokernight.application.command;

import com.pokernight.application.room.RoomCode;

/** A client command is unique for one authenticated actor in one room. */
public record CommandIdentity(RoomCode roomCode, String actorId, String commandId) {
    public CommandIdentity {
        if (roomCode == null) throw new IllegalArgumentException("roomCode must not be null");
        if (actorId == null || actorId.isBlank()) throw new IllegalArgumentException("actorId must not be blank");
        if (commandId == null || commandId.isBlank()) throw new IllegalArgumentException("commandId must not be blank");
        if (commandId.length() > 100) throw new IllegalArgumentException("commandId must not exceed 100 characters");
    }
}
