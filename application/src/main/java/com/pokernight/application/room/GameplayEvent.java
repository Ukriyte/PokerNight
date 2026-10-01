package com.pokernight.application.room;

import com.pokernight.engine.event.GameEvent;
import com.pokernight.engine.state.PublicGameState;

public sealed interface GameplayEvent {
    record Engine(GameEvent event) implements GameplayEvent { }
    record NewGameStarted(PublicGameState state) implements GameplayEvent { }
}
