package com.pokernight.application.room;

@FunctionalInterface
public interface RoomCodeGenerator {
    RoomCode nextCode();
}
