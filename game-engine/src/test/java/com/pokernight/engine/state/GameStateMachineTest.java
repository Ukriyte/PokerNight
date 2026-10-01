package com.pokernight.engine.state;

import com.pokernight.engine.model.GamePhase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GameStateMachineTest {
    @Test
    void followsACompleteHandLifecycle() {
        GameStateMachine machine = new GameStateMachine();

        machine.transitionTo(GamePhase.STARTING_HAND);
        machine.transitionTo(GamePhase.PRE_FLOP);
        machine.transitionTo(GamePhase.FLOP);
        machine.transitionTo(GamePhase.TURN);
        machine.transitionTo(GamePhase.RIVER);
        machine.transitionTo(GamePhase.SHOWDOWN);
        machine.transitionTo(GamePhase.HAND_COMPLETE);

        assertThat(machine.phase()).isEqualTo(GamePhase.HAND_COMPLETE);
        assertThat(machine.canTransitionTo(GamePhase.STARTING_HAND)).isTrue();
    }

    @Test
    void rejectsSkippingFromWaitingDirectlyToFlop() {
        GameStateMachine machine = new GameStateMachine();

        assertThatThrownBy(() -> machine.transitionTo(GamePhase.FLOP))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("WAITING_FOR_PLAYERS -> FLOP");
    }
}
