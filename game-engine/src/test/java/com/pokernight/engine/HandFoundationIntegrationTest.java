package com.pokernight.engine;

import com.pokernight.engine.betting.BettingActionResult;
import com.pokernight.engine.betting.BettingRound;
import com.pokernight.engine.deck.Deck;
import com.pokernight.engine.hand.DealtHand;
import com.pokernight.engine.hand.HandDealer;
import com.pokernight.engine.model.Card;
import com.pokernight.engine.model.PlayerAction;
import com.pokernight.engine.model.PlayerDecision;
import com.pokernight.engine.pot.ContributionLedger;
import com.pokernight.engine.pot.PotBreakdown;
import com.pokernight.engine.pot.PotCalculator;
import com.pokernight.engine.table.PlayerSeat;
import com.pokernight.engine.table.PositionRotator;
import com.pokernight.engine.table.TablePositions;
import com.pokernight.engine.table.TableSeats;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class HandFoundationIntegrationTest {
    @Test
    void componentsComposeWithoutSharingInternalState() {
        TableSeats seats = seats();
        TablePositions positions = new PositionRotator().next(seats, Set.of("p1", "p2", "p3"));
        DealtHand hand = new HandDealer(new Deck(cards -> { }))
            .deal(positions.clockwisePlayerIds(), positions.smallBlindId());

        ContributionLedger ledger = new ContributionLedger();
        positions.clockwisePlayerIds().forEach(ledger::registerPlayer);
        BettingRound preFlop = new BettingRound(
            positions.clockwisePlayerIds(),
            Map.of("p1", 1_000L, "p2", 1_000L, "p3", 1_000L),
            50
        );
        ledger.record(positions.smallBlindId(), preFlop.postForcedBet(positions.smallBlindId(), 25));
        ledger.record(positions.bigBlindId(), preFlop.postForcedBet(positions.bigBlindId(), 50));
        preFlop.begin(positions.firstToActPreFlopId());

        record(ledger, "p1", preFlop.act(new PlayerDecision("p1", PlayerAction.CALL, null)));
        record(ledger, "p2", preFlop.act(new PlayerDecision("p2", PlayerAction.CALL, null)));
        record(ledger, "p3", preFlop.act(new PlayerDecision("p3", PlayerAction.CHECK, null)));

        hand.dealFlop();
        hand.dealTurn();
        hand.dealRiver();
        PotBreakdown pots = PotCalculator.calculate(ledger.snapshot(), Set.of());

        long chipsStillInStacks = preFlop.snapshot().players().stream()
            .mapToLong(player -> player.remainingChips())
            .sum();
        assertThat(chipsStillInStacks + ledger.total()).isEqualTo(3_000L);
        assertThat(pots.totalAccountedFor()).isEqualTo(150L);
        assertThat(allCards(hand)).doesNotHaveDuplicates().hasSize(14);
    }

    private static void record(ContributionLedger ledger, String playerId, BettingActionResult result) {
        ledger.record(playerId, result.committedChips());
    }

    private static List<Card> allCards(DealtHand hand) {
        List<Card> cards = new ArrayList<>();
        hand.allHoleCards().values().forEach(hole -> cards.addAll(hole.asList()));
        cards.addAll(hand.communityCards());
        cards.addAll(hand.burnCards());
        assertThat(new HashSet<>(cards)).hasSameSizeAs(cards);
        return cards;
    }

    private static TableSeats seats() {
        TableSeats seats = new TableSeats();
        seats.seat(new PlayerSeat(0, "p1", "One"));
        seats.seat(new PlayerSeat(1, "p2", "Two"));
        seats.seat(new PlayerSeat(2, "p3", "Three"));
        return seats;
    }
}
