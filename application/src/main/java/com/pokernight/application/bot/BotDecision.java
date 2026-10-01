package com.pokernight.application.bot;

import com.pokernight.engine.model.PlayerAction;
import com.pokernight.engine.model.PlayerDecision;

public record BotDecision(PlayerAction action, Long amount) {
    public PlayerDecision forPlayer(String playerId) {
        return new PlayerDecision(playerId, action, amount);
    }
}
