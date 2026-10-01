package com.pokernight.engine.table;

public record PlayerSeat(int number, String playerId, String displayName) {
    public PlayerSeat {
        if (number < 0 || number >= TableSeats.MAX_SEATS) {
            throw new IllegalArgumentException("seat number must be between 0 and 5");
        }
        if (playerId == null || playerId.isBlank()) {
            throw new IllegalArgumentException("playerId must not be blank");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
    }
}
