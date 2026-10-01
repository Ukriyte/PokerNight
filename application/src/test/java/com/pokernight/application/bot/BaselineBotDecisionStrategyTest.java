package com.pokernight.application.bot;

import com.pokernight.engine.hand.HoleCards;
import com.pokernight.engine.model.Card;
import com.pokernight.engine.model.GamePhase;
import com.pokernight.engine.model.PlayerAction;
import com.pokernight.engine.model.Rank;
import com.pokernight.engine.model.Suit;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BaselineBotDecisionStrategyTest {
    @Test
    void decisionsAreLegalAndInjectedRandomnessControlsAggression() {
        var aggressive = new BaselineBotDecisionStrategy(bound -> 0);
        var passive = new BaselineBotDecisionStrategy(bound -> 50);
        var context = context(Set.of(PlayerAction.FOLD, PlayerAction.CALL, PlayerAction.RAISE));

        assertThat(aggressive.decide(context)).isEqualTo(new BotDecision(PlayerAction.RAISE, 100L));
        assertThat(passive.decide(context)).isEqualTo(new BotDecision(PlayerAction.CALL, null));
    }

    @Test
    void checkingIsPreferredWhenNoAggressiveChoiceIsMade() {
        var strategy = new BaselineBotDecisionStrategy(bound -> 50);
        assertThat(strategy.decide(context(Set.of(PlayerAction.CHECK, PlayerAction.RAISE))).action())
            .isEqualTo(PlayerAction.CHECK);
    }

    private static BotDecisionContext context(Set<PlayerAction> actions) {
        return new BotDecisionContext(
            "bot", new HoleCards(card(Rank.ACE, Suit.SPADES), card(Rank.KING, Suit.HEARTS)),
            List.of(), GamePhase.PRE_FLOP, actions, 50, 50, 50, 100, 950, 2
        );
    }

    private static Card card(Rank rank, Suit suit) {
        return new Card(rank, suit);
    }
}
