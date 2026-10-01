package com.pokernight.application.room;

import com.pokernight.engine.state.PublicGameState;

import java.util.List;

public record GameCommandResult(
    RoomCode roomCode,
    PublicGameState state,
    List<GameplayEvent> events
) {
    public GameCommandResult {
        events = List.copyOf(events);
    }
}
