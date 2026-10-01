package com.pokernight.engine;

import com.pokernight.engine.betting.BettingActionResult;
import com.pokernight.engine.betting.BettingRound;
import com.pokernight.engine.betting.BettingRoundSnapshot;
import com.pokernight.engine.betting.BettingStatus;
import com.pokernight.engine.betting.PlayerBetSnapshot;
import com.pokernight.engine.evaluation.EvaluatedHand;
import com.pokernight.engine.evaluation.HandEvaluator;
import com.pokernight.engine.event.GameEvent;
import com.pokernight.engine.hand.DealtHand;
import com.pokernight.engine.hand.HandDealer;
import com.pokernight.engine.hand.HoleCards;
import com.pokernight.engine.model.GamePhase;
import com.pokernight.engine.model.PlayerAction;
import com.pokernight.engine.model.PlayerDecision;
import com.pokernight.engine.pot.ContributionLedger;
import com.pokernight.engine.pot.Pot;
import com.pokernight.engine.pot.PotBreakdown;
import com.pokernight.engine.pot.PotCalculator;
import com.pokernight.engine.state.GameStateMachine;
import com.pokernight.engine.state.PrivatePlayerState;
import com.pokernight.engine.state.PublicGameState;
import com.pokernight.engine.state.PublicPlayerState;
import com.pokernight.engine.table.PlayerSeat;
import com.pokernight.engine.table.PositionRotator;
import com.pokernight.engine.table.TablePositions;
import com.pokernight.engine.table.TableSeats;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Thin aggregate coordinator. Poker rules remain in the focused table, hand,
 * betting, evaluation, pot, and state components that this class sequences.
 */
public final class GameEngine {
    private final GameRules rules;
    private final TableSeats seats = new TableSeats();
    private final PositionRotator positions = new PositionRotator();
    private final HandDealer dealer;
    private final HandEvaluator evaluator;
    private final GameStateMachine stateMachine = new GameStateMachine();
    private final Map<String, MutablePlayer> players = new LinkedHashMap<>();
    private final List<GameEvent> pendingEvents = new ArrayList<>();

    private long handNumber;
    private TablePositions currentPositions;
    private DealtHand currentHand;
    private BettingRound bettingRound;
    private ContributionLedger contributions;
    private List<String> handPlayerIds = List.of();
    private final Set<String> foldedPlayerIds = new HashSet<>();
    private Map<String, HoleCards> revealedHoleCards = Map.of();

    public GameEngine(GameRules rules) {
        this(rules, new HandDealer(), new HandEvaluator());
    }

    public GameEngine(GameRules rules, HandDealer dealer, HandEvaluator evaluator) {
        this.rules = rules;
        this.dealer = dealer;
        this.evaluator = evaluator;
    }

    public void seatPlayer(String playerId, String displayName, int seatNumber, long startingChips) {
        requireBetweenHands();
        if (startingChips <= 0) throw new IllegalArgumentException("starting chips must be positive");
        if (players.containsKey(playerId)) throw new IllegalStateException("player already exists: " + playerId);
        seats.seat(new PlayerSeat(seatNumber, playerId, displayName));
        players.put(playerId, new MutablePlayer(playerId, displayName, seatNumber, startingChips));
    }

    public void removePlayer(String playerId) {
        requireBetweenHands();
        seats.remove(playerId);
        players.remove(playerId);
    }

    /** Restores rotation metadata at a safe between-hands persistence boundary. */
    public void restoreProgress(long completedHandNumber, int previousDealerSeat) {
        requireBetweenHands();
        if (completedHandNumber < 0) throw new IllegalArgumentException("hand number must not be negative");
        this.handNumber = completedHandNumber;
        this.positions.restorePreviousDealerSeat(previousDealerSeat);
    }

    public void startHand() {
        requireBetweenHands();
        List<String> active = seats.occupiedClockwise().stream()
            .map(PlayerSeat::playerId)
            .filter(id -> players.get(id).chips > 0)
            .toList();
        if (active.size() < 2) throw new IllegalStateException("at least two players with chips are required");

        stateMachine.transitionTo(GamePhase.STARTING_HAND);
        handNumber++;
        foldedPlayerIds.clear();
        revealedHoleCards = Map.of();
        handPlayerIds = List.copyOf(active);
        currentPositions = positions.next(seats, active);
        currentHand = dealer.deal(currentPositions.clockwisePlayerIds(), currentPositions.smallBlindId());
        contributions = new ContributionLedger();
        handPlayerIds.forEach(contributions::registerPlayer);

        addEvent(new GameEvent.HandStarted(handNumber, currentPositions));
        handPlayerIds.forEach(id -> addEvent(new GameEvent.CardsDealt(id, currentHand.holeCardsFor(id).orElseThrow())));
        stateMachine.transitionTo(GamePhase.PRE_FLOP);
        beginPreFlop();
    }

    public void submit(PlayerDecision decision) {
        requireHandInProgress();
        BettingActionResult result = bettingRound.act(decision);
        syncPlayerChips(result.state());
        contributions.record(decision.playerId(), result.committedChips());
        if (decision.action() == PlayerAction.FOLD) foldedPlayerIds.add(decision.playerId());
        addEvent(new GameEvent.PlayerActed(
            decision.playerId(), decision.action(), decision.amount(), result.committedChips()
        ));

        if (result.state().complete()) {
            if (result.state().lastStandingPlayerId() != null) {
                completeByFold(result.state().lastStandingPlayerId());
            } else {
                advanceAfterBettingRound();
            }
        } else {
            emitActionRequired();
        }
    }

    public PublicGameState publicState() {
        BettingRoundSnapshot betting = bettingRound == null ? null : bettingRound.snapshot();
        Map<String, PlayerBetSnapshot> bets = new HashMap<>();
        if (betting != null) betting.players().forEach(player -> bets.put(player.playerId(), player));

        List<PublicPlayerState> publicPlayers = seats.occupiedClockwise().stream().map(seat -> {
            MutablePlayer player = players.get(seat.playerId());
            PlayerBetSnapshot bet = bets.get(player.playerId);
            BettingStatus status = foldedPlayerIds.contains(player.playerId)
                ? BettingStatus.FOLDED
                : bet == null ? BettingStatus.ACTIVE : bet.status();
            return new PublicPlayerState(
                player.playerId, player.displayName, player.seatNumber, player.chips,
                bet == null ? 0 : bet.streetContribution(), status,
                currentPositions != null && player.playerId.equals(currentPositions.dealerId()),
                currentPositions != null && player.playerId.equals(currentPositions.smallBlindId()),
                currentPositions != null && player.playerId.equals(currentPositions.bigBlindId())
            );
        }).toList();

        boolean inProgress = isHandInProgress();
        return new PublicGameState(
            stateMachine.phase(), handNumber, inProgress, publicPlayers,
            currentHand == null ? List.of() : currentHand.communityCards(),
            revealedHoleCards,
            contributions == null ? 0 : contributions.total(),
            inProgress && betting != null ? betting.currentBet() : 0,
            inProgress && betting != null ? betting.minimumRaise() : rules.blinds().bigBlind(),
            inProgress && betting != null ? betting.currentPlayerId() : null
        );
    }

    public PrivatePlayerState privateState(String playerId) {
        if (!players.containsKey(playerId)) throw new IllegalArgumentException("unknown player: " + playerId);
        Set<PlayerAction> actions = isHandInProgress() && bettingRound != null
            ? bettingRound.validActions(playerId)
            : Set.of();
        return new PrivatePlayerState(
            publicState(), playerId,
            currentHand == null ? Optional.empty() : currentHand.holeCardsFor(playerId),
            actions
        );
    }

    /** Returns committed domain events once; adapters route them after the command succeeds. */
    public List<GameEvent> drainEvents() {
        List<GameEvent> result = List.copyOf(pendingEvents);
        pendingEvents.clear();
        return result;
    }

    private void beginPreFlop() {
        bettingRound = createRound(contenders());
        long smallBlind = bettingRound.postForcedBet(currentPositions.smallBlindId(), rules.blinds().smallBlind());
        long bigBlind = bettingRound.postForcedBet(currentPositions.bigBlindId(), rules.blinds().bigBlind());
        contributions.record(currentPositions.smallBlindId(), smallBlind);
        contributions.record(currentPositions.bigBlindId(), bigBlind);
        bettingRound.begin(firstEligibleFrom(currentPositions.firstToActPreFlopId(), contenders()));
        syncPlayerChips(bettingRound.snapshot());
        continueOrRequestAction();
    }

    private void beginPostFlopRound() {
        List<String> contenders = contenders();
        bettingRound = createRound(contenders);
        bettingRound.begin(firstEligibleFrom(currentPositions.firstToActPostFlopId(), contenders));
        continueOrRequestAction();
    }

    private BettingRound createRound(List<String> contenders) {
        Map<String, Long> stacks = new LinkedHashMap<>();
        contenders.forEach(id -> stacks.put(id, players.get(id).chips));
        return new BettingRound(contenders, stacks, rules.blinds().bigBlind());
    }

    private void continueOrRequestAction() {
        if (bettingRound.snapshot().complete()) advanceAfterBettingRound();
        else emitActionRequired();
    }

    private void advanceAfterBettingRound() {
        switch (stateMachine.phase()) {
            case PRE_FLOP -> {
                currentHand.dealFlop();
                stateMachine.transitionTo(GamePhase.FLOP);
                addEvent(new GameEvent.StreetDealt(GamePhase.FLOP, currentHand.communityCards()));
                beginPostFlopRound();
            }
            case FLOP -> {
                currentHand.dealTurn();
                stateMachine.transitionTo(GamePhase.TURN);
                addEvent(new GameEvent.StreetDealt(GamePhase.TURN, currentHand.communityCards()));
                beginPostFlopRound();
            }
            case TURN -> {
                currentHand.dealRiver();
                stateMachine.transitionTo(GamePhase.RIVER);
                addEvent(new GameEvent.StreetDealt(GamePhase.RIVER, currentHand.communityCards()));
                beginPostFlopRound();
            }
            case RIVER -> completeAtShowdown();
            default -> throw new IllegalStateException("cannot advance betting from " + stateMachine.phase());
        }
    }

    private void completeByFold(String winnerId) {
        stateMachine.transitionTo(GamePhase.SHOWDOWN);
        long totalPot = contributions.total();
        players.get(winnerId).chips = Math.addExact(players.get(winnerId).chips, totalPot);
        finishHand(Map.of(winnerId, totalPot), Map.of(), totalPot);
    }

    private void completeAtShowdown() {
        stateMachine.transitionTo(GamePhase.SHOWDOWN);
        PotBreakdown breakdown = PotCalculator.calculate(contributions.snapshot(), foldedPlayerIds);
        Map<String, Long> payouts = new LinkedHashMap<>();
        Map<String, EvaluatedHand> evaluated = new HashMap<>();

        breakdown.refunds().forEach((id, amount) -> award(id, amount, payouts));
        for (Pot pot : breakdown.pots()) {
            List<String> winners = winnersFor(pot.eligiblePlayerIds(), evaluated);
            long share = pot.amount() / winners.size();
            long remainder = pot.amount() % winners.size();
            List<String> orderedWinners = currentPositions.clockwisePlayerIds().stream()
                .filter(winners::contains)
                .toList();
            for (int i = 0; i < orderedWinners.size(); i++) {
                award(orderedWinners.get(i), share + (i < remainder ? 1 : 0), payouts);
            }
        }
        finishHand(payouts, evaluated, contributions.total());
    }

    private List<String> winnersFor(Set<String> eligibleIds, Map<String, EvaluatedHand> evaluated) {
        eligibleIds.forEach(id -> evaluated.computeIfAbsent(id,
            key -> evaluator.evaluate(currentHand.holeCardsFor(key).orElseThrow(), currentHand.communityCards())));
        EvaluatedHand best = eligibleIds.stream().map(evaluated::get)
            .max(Comparator.comparing(EvaluatedHand::value)).orElseThrow();
        return eligibleIds.stream()
            .filter(id -> evaluated.get(id).value().compareTo(best.value()) == 0)
            .toList();
    }

    private void finishHand(Map<String, Long> payouts, Map<String, EvaluatedHand> shownHands, long totalPot) {
        stateMachine.transitionTo(GamePhase.HAND_COMPLETE);
        bettingRound = null;
        revealedHoleCards = Map.copyOf(currentHand.allHoleCards());
        addEvent(new GameEvent.HandCompleted(payouts, shownHands, totalPot, currentHand.communityCards()));
        List<MutablePlayer> funded = players.values().stream().filter(player -> player.chips > 0).toList();
        if (funded.size() == 1) {
            stateMachine.transitionTo(GamePhase.GAME_COMPLETE);
            addEvent(new GameEvent.GameCompleted(funded.getFirst().playerId));
        }
    }

    private void award(String playerId, long amount, Map<String, Long> payouts) {
        players.get(playerId).chips = Math.addExact(players.get(playerId).chips, amount);
        payouts.merge(playerId, amount, Math::addExact);
    }

    private void emitActionRequired() {
        BettingRoundSnapshot snap = bettingRound.snapshot();
        String playerId = snap.currentPlayerId();
        PlayerBetSnapshot player = snap.players().stream()
            .filter(candidate -> candidate.playerId().equals(playerId)).findFirst().orElseThrow();
        addEvent(new GameEvent.ActionRequired(
            playerId, bettingRound.validActions(playerId), snap.currentBet(),
            player.streetContribution(), snap.minimumRaise(), contributions.total(), player.remainingChips()
        ));
    }

    private void syncPlayerChips(BettingRoundSnapshot snapshot) {
        snapshot.players().forEach(state -> players.get(state.playerId()).chips = state.remainingChips());
    }

    private List<String> contenders() {
        return handPlayerIds.stream().filter(id -> !foldedPlayerIds.contains(id)).toList();
    }

    private String firstEligibleFrom(String preferredId, List<String> eligibleIds) {
        int start = currentPositions.clockwisePlayerIds().indexOf(preferredId);
        for (int offset = 0; offset < currentPositions.clockwisePlayerIds().size(); offset++) {
            String candidate = currentPositions.clockwisePlayerIds()
                .get((start + offset) % currentPositions.clockwisePlayerIds().size());
            if (eligibleIds.contains(candidate)) return candidate;
        }
        throw new IllegalStateException("no eligible player found");
    }

    private boolean isHandInProgress() {
        return switch (stateMachine.phase()) {
            case STARTING_HAND, PRE_FLOP, FLOP, TURN, RIVER, SHOWDOWN -> true;
            default -> false;
        };
    }

    private void requireHandInProgress() {
        if (!isHandInProgress() || bettingRound == null) throw new IllegalStateException("no betting action is pending");
    }

    private void requireBetweenHands() {
        if (isHandInProgress()) throw new IllegalStateException("operation is only allowed between hands");
        if (stateMachine.phase() == GamePhase.GAME_COMPLETE) throw new IllegalStateException("game is complete");
    }

    private void addEvent(GameEvent event) {
        pendingEvents.add(event);
    }

    private static final class MutablePlayer {
        private final String playerId;
        private final String displayName;
        private final int seatNumber;
        private long chips;

        private MutablePlayer(String playerId, String displayName, int seatNumber, long chips) {
            this.playerId = playerId;
            this.displayName = displayName;
            this.seatNumber = seatNumber;
            this.chips = chips;
        }
    }
}
