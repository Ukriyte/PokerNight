package com.pokernight.web.socket;

import com.pokernight.engine.model.PlayerAction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class GameSocketModels {
    private GameSocketModels() { }

    public record PlayerActionCommand(
        @NotBlank @Size(max = 100) String commandId,
        @NotNull PlayerAction action,
        @Positive Long amount
    ) { }

    public record GameControlCommand(@NotBlank @Size(max = 100) String commandId) { }

    public record CommandReceipt(
        String commandId,
        boolean replayed,
        String phase,
        long handNumber
    ) { }

    public record SocketError(String code, String message) { }
}
