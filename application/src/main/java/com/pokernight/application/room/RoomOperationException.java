package com.pokernight.application.room;

public final class RoomOperationException extends RuntimeException {
    private final RoomFailure failure;

    public RoomOperationException(RoomFailure failure, String message) {
        super(message);
        this.failure = failure;
    }

    public RoomFailure failure() {
        return failure;
    }
}
