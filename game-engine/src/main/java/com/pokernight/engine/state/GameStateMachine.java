package com.pokernight.engine.state;

import com.pokernight.engine.model.GamePhase;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class GameStateMachine {
    private static final Map<GamePhase, Set<GamePhase>> TRANSITIONS = transitions();

    private GamePhase phase = GamePhase.WAITING_FOR_PLAYERS;

    public GamePhase phase() {
        return phase;
    }

    public boolean canTransitionTo(GamePhase next) {
        return TRANSITIONS.getOrDefault(phase, Set.of()).contains(next);
    }

    public void transitionTo(GamePhase next) {
        if (!canTransitionTo(next)) {
            throw new IllegalStateException("invalid transition: " + phase + " -> " + next);
        }
        phase = next;
    }

    private static Map<GamePhase, Set<GamePhase>> transitions() {
        Map<GamePhase, Set<GamePhase>> result = new EnumMap<>(GamePhase.class);
        result.put(GamePhase.WAITING_FOR_PLAYERS, EnumSet.of(GamePhase.STARTING_HAND));
        result.put(GamePhase.STARTING_HAND, EnumSet.of(GamePhase.PRE_FLOP));
        result.put(GamePhase.PRE_FLOP, EnumSet.of(GamePhase.FLOP, GamePhase.SHOWDOWN));
        result.put(GamePhase.FLOP, EnumSet.of(GamePhase.TURN, GamePhase.SHOWDOWN));
        result.put(GamePhase.TURN, EnumSet.of(GamePhase.RIVER, GamePhase.SHOWDOWN));
        result.put(GamePhase.RIVER, EnumSet.of(GamePhase.SHOWDOWN));
        result.put(GamePhase.SHOWDOWN, EnumSet.of(GamePhase.HAND_COMPLETE));
        result.put(GamePhase.HAND_COMPLETE, EnumSet.of(GamePhase.STARTING_HAND, GamePhase.GAME_COMPLETE));
        result.put(GamePhase.GAME_COMPLETE, EnumSet.noneOf(GamePhase.class));
        return Map.copyOf(result);
    }
}
