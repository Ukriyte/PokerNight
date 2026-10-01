package com.pokernight.application.room;

import com.pokernight.engine.GameEngine;
import com.pokernight.engine.GameRules;

@FunctionalInterface
interface GameEngineFactory {
    GameEngine create(GameRules rules);
}
