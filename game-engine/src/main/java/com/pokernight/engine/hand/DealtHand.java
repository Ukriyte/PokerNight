package com.pokernight.engine.hand;

import com.pokernight.engine.deck.Deck;
import com.pokernight.engine.model.Card;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Owns the cards for one hand and enforces flop/turn/river dealing order. */
public final class DealtHand {
    private final Deck deck;
    private final Map<String, HoleCards> holeCards;
    private final List<Card> communityCards = new ArrayList<>(5);
    private final List<Card> burnCards = new ArrayList<>(3);

    DealtHand(Deck deck, Map<String, HoleCards> holeCards) {
        this.deck = deck;
        this.holeCards = Map.copyOf(new LinkedHashMap<>(holeCards));
    }

    public Optional<HoleCards> holeCardsFor(String playerId) {
        return Optional.ofNullable(holeCards.get(playerId));
    }

    public Map<String, HoleCards> allHoleCards() {
        return holeCards;
    }

    public List<Card> dealFlop() {
        requireCommunityCardCount(0, "flop");
        burnCards.add(deck.deal());
        communityCards.add(deck.deal());
        communityCards.add(deck.deal());
        communityCards.add(deck.deal());
        return List.copyOf(communityCards);
    }

    public Card dealTurn() {
        requireCommunityCardCount(3, "turn");
        burnCards.add(deck.deal());
        Card turn = deck.deal();
        communityCards.add(turn);
        return turn;
    }

    public Card dealRiver() {
        requireCommunityCardCount(4, "river");
        burnCards.add(deck.deal());
        Card river = deck.deal();
        communityCards.add(river);
        return river;
    }

    public List<Card> communityCards() {
        return List.copyOf(communityCards);
    }

    public List<Card> burnCards() {
        return List.copyOf(burnCards);
    }

    private void requireCommunityCardCount(int expected, String street) {
        if (communityCards.size() != expected) {
            throw new IllegalStateException("cannot deal " + street + " with " + communityCards.size() + " community cards");
        }
    }
}
