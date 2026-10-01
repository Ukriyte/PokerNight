package com.pokernight.application.persistence;

import com.pokernight.application.room.HandHistoryEntry;
import com.pokernight.application.room.RoomConfig;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Safe-boundary snapshot. An active hand is intentionally never serialized. */
public record RoomSnapshot(
    int schemaVersion,
    String roomCode,
    String hostId,
    String hostName,
    RoomConfig config,
    List<MemberSnapshot> members,
    Map<String, Long> playerChips,
    long completedHandNumber,
    int previousDealerSeat,
    List<HandHistoryEntry> history,
    Instant createdAt,
    Instant lastActivityAt
) {
    public static final int CURRENT_SCHEMA = 1;
    public RoomSnapshot {
        members = List.copyOf(members);
        playerChips = Map.copyOf(playerChips);
        history = List.copyOf(history);
    }
}
