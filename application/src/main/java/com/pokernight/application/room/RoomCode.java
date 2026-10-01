package com.pokernight.application.room;

import java.util.Locale;

public record RoomCode(String value) {
    public RoomCode {
        if (value == null || !value.toUpperCase(Locale.ROOT).matches("[0-9A-F]{6}")) {
            throw new IllegalArgumentException("room code must be six hexadecimal characters");
        }
        value = value.toUpperCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return value;
    }
}
