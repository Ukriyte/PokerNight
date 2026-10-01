package com.pokernight.engine.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlayerDecisionTest {
    @Test
    void raiseRequiresAnAmount() {
        assertThatThrownBy(() -> new PlayerDecision("p1", PlayerAction.RAISE, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("requires an amount");
    }
}
