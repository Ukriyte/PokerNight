package com.pokernight.application.room;

import java.security.SecureRandom;

public final class SecureRoomCodeGenerator implements RoomCodeGenerator {
    private static final int CODE_SPACE = 0x1000000;
    private final SecureRandom random = new SecureRandom();

    @Override
    public RoomCode nextCode() {
        return new RoomCode("%06X".formatted(random.nextInt(CODE_SPACE)));
    }
}
