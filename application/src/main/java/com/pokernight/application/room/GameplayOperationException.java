package com.pokernight.application.room;

public final class GameplayOperationException extends RuntimeException {
    private final GameplayFailure failure;

    public GameplayOperationException(GameplayFailure failure, String message) {
        super(message);
        this.failure = failure;
    }

    public GameplayFailure failure() {
        return failure;
    }
}
