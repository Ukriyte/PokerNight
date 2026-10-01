package com.pokernight.engine.betting;

import com.pokernight.engine.model.PlayerAction;
import com.pokernight.engine.model.PlayerDecision;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BettingRoundTest {
    @Test
    void postsBlindsAndCompletesAfterEveryoneMatchesAndBigBlindChecks() {
        BettingRound round = round(Map.of("p1", 1_000L, "p2", 1_000L, "p3", 1_000L));
        round.postForcedBet("p2", 25);
        round.postForcedBet("p3", 50);
        round.begin("p1");

        assertThat(round.validActions("p1")).contains(PlayerAction.CALL, PlayerAction.RAISE, PlayerAction.FOLD)
            .doesNotContain(PlayerAction.CHECK);
        round.act(new PlayerDecision("p1", PlayerAction.CALL, null));
        round.act(new PlayerDecision("p2", PlayerAction.CALL, null));
        BettingActionResult result = round.act(new PlayerDecision("p3", PlayerAction.CHECK, null));

        assertThat(result.state().complete()).isTrue();
        assertThat(result.state().players()).extracting(PlayerBetSnapshot::streetContribution)
            .containsExactly(50L, 50L, 50L);
    }

    @Test
    void fullRaiseReopensActionForPlayersWhoAlreadyActed() {
        BettingRound round = round(Map.of("p1", 1_000L, "p2", 1_000L, "p3", 1_000L));
        round.begin("p1");

        round.act(new PlayerDecision("p1", PlayerAction.CHECK, null));
        round.act(new PlayerDecision("p2", PlayerAction.RAISE, 100L));
        round.act(new PlayerDecision("p3", PlayerAction.CALL, null));

        assertThat(round.snapshot().currentPlayerId()).isEqualTo("p1");
        assertThat(round.validActions("p1")).contains(PlayerAction.RAISE, PlayerAction.CALL);
        assertThat(round.act(new PlayerDecision("p1", PlayerAction.CALL, null)).state().complete()).isTrue();
    }

    @Test
    void shortAllInRequiresMatchingButDoesNotRestoreRaiseRights() {
        BettingRound round = round(Map.of("p1", 500L, "p2", 500L, "p3", 150L));
        round.begin("p1");

        round.act(new PlayerDecision("p1", PlayerAction.RAISE, 100L));
        round.act(new PlayerDecision("p2", PlayerAction.CALL, null));
        round.act(new PlayerDecision("p3", PlayerAction.ALL_IN, null));

        assertThat(round.snapshot().currentPlayerId()).isEqualTo("p1");
        assertThat(round.validActions("p1")).contains(PlayerAction.CALL).doesNotContain(PlayerAction.RAISE);
        round.act(new PlayerDecision("p1", PlayerAction.CALL, null));
        assertThat(round.validActions("p2")).contains(PlayerAction.CALL).doesNotContain(PlayerAction.RAISE);
        assertThat(round.act(new PlayerDecision("p2", PlayerAction.CALL, null)).state().complete()).isTrue();
    }

    @Test
    void rejectsAnActionFromTheWrongPlayer() {
        BettingRound round = round(Map.of("p1", 500L, "p2", 500L, "p3", 500L));
        round.begin("p1");

        assertThatThrownBy(() -> round.act(new PlayerDecision("p2", PlayerAction.CHECK, null)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not this player's turn");
    }

    private static BettingRound round(Map<String, Long> stacks) {
        return new BettingRound(List.of("p1", "p2", "p3"), stacks, 50);
    }
}
