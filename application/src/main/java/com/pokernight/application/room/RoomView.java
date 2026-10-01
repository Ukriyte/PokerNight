package com.pokernight.application.room;

import com.pokernight.engine.state.PublicGameState;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public record RoomView(
    RoomCode code,
    String hostId,
    String hostName,
    RoomConfig config,
    List<RoomMemberView> members,
    Set<String> botPlayerIds,
    PublicGameState game,
    Instant createdAt,
    Instant lastActivityAt
) {
    public RoomView {
        members = List.copyOf(members);
        botPlayerIds = Set.copyOf(botPlayerIds);
    }
}
