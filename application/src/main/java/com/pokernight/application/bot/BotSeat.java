package com.pokernight.application.bot;

/** Stable room participant created by the bot lifecycle, not by the decision strategy. */
public record BotSeat(String playerId, String displayName, int seatNumber) {
    public BotSeat {
        if (playerId == null || playerId.isBlank()) throw new IllegalArgumentException("playerId must not be blank");
        if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("displayName must not be blank");
        if (seatNumber < 0) throw new IllegalArgumentException("seatNumber must not be negative");
    }
}
