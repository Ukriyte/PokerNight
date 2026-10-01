package com.pokernight.engine.betting;

import com.pokernight.engine.model.PlayerAction;
import com.pokernight.engine.model.PlayerDecision;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * State machine for one betting street. It owns turn order and street-level chips only.
 * Hand-wide pot construction is deliberately handled by ContributionLedger/PotCalculator.
 */
public final class BettingRound {
    private final List<String> order;
    private final Map<String, MutablePlayerBet> players = new LinkedHashMap<>();
    private final Set<String> actedSinceFullRaise = new HashSet<>();
    private final Set<String> raiseRights = new HashSet<>();

    private long currentBet;
    private long minimumRaise;
    private int currentIndex = -1;
    private boolean started;
    private boolean complete;

    public BettingRound(List<String> clockwiseOrder, Map<String, Long> playerStacks, long minimumRaise) {
        if (clockwiseOrder.size() < 2 || new HashSet<>(clockwiseOrder).size() != clockwiseOrder.size()) {
            throw new IllegalArgumentException("betting order requires at least two unique players");
        }
        if (!playerStacks.keySet().equals(new HashSet<>(clockwiseOrder))) {
            throw new IllegalArgumentException("stacks must be supplied for exactly the players in betting order");
        }
        if (minimumRaise <= 0) {
            throw new IllegalArgumentException("minimum raise must be positive");
        }

        this.order = List.copyOf(clockwiseOrder);
        this.minimumRaise = minimumRaise;
        this.order.forEach(id -> {
            long chips = playerStacks.get(id);
            if (chips < 0) throw new IllegalArgumentException("player chips cannot be negative");
            players.put(id, new MutablePlayerBet(id, chips));
            raiseRights.add(id);
        });
    }

    public long postForcedBet(String playerId, long requestedAmount) {
        ensureNotStarted();
        if (requestedAmount <= 0) throw new IllegalArgumentException("forced bet must be positive");
        MutablePlayerBet player = requirePlayer(playerId);
        long committed = commit(player, Math.min(requestedAmount, player.remainingChips));
        currentBet = Math.max(currentBet, player.streetContribution);
        return committed;
    }

    public void begin(String firstPlayerId) {
        ensureNotStarted();
        int index = order.indexOf(firstPlayerId);
        if (index < 0) throw new IllegalArgumentException("first player is not in this betting round");
        started = true;
        currentIndex = index;
        if (!needsAction(players.get(firstPlayerId))) {
            advanceFrom(currentIndex);
        }
        updateCompletion();
    }

    public Set<PlayerAction> validActions(String playerId) {
        if (!started || complete || !playerId.equals(currentPlayerId())) return Set.of();

        MutablePlayerBet player = requirePlayer(playerId);
        long callAmount = callAmount(player);
        EnumSet<PlayerAction> actions = EnumSet.of(PlayerAction.FOLD);
        if (callAmount == 0) actions.add(PlayerAction.CHECK);
        if (callAmount > 0 && player.remainingChips > 0) actions.add(PlayerAction.CALL);
        long maximumTarget = player.streetContribution + player.remainingChips;
        if (raiseRights.contains(playerId) && maximumTarget >= currentBet + minimumRaise) {
            actions.add(PlayerAction.RAISE);
        }
        if (player.remainingChips > 0) actions.add(PlayerAction.ALL_IN);
        return Set.copyOf(actions);
    }

    public BettingActionResult act(PlayerDecision decision) {
        ensureStartedAndOpen();
        if (!decision.playerId().equals(currentPlayerId())) {
            throw new IllegalStateException("not this player's turn: " + decision.playerId());
        }
        if (!validActions(decision.playerId()).contains(decision.action())) {
            throw new IllegalArgumentException("action is not valid: " + decision.action());
        }

        MutablePlayerBet player = requirePlayer(decision.playerId());
        long committed = switch (decision.action()) {
            case FOLD -> {
                player.status = BettingStatus.FOLDED;
                yield 0;
            }
            case CHECK -> 0;
            case CALL -> commit(player, Math.min(callAmount(player), player.remainingChips));
            case RAISE -> performRaise(player, decision.amount());
            case ALL_IN -> performAllIn(player);
        };

        actedSinceFullRaise.add(player.playerId);
        raiseRights.remove(player.playerId);
        int previousIndex = currentIndex;
        updateCompletion();
        if (!complete) advanceFrom(previousIndex);
        updateCompletion();
        return new BettingActionResult(committed, snapshot());
    }

    public BettingRoundSnapshot snapshot() {
        List<PlayerBetSnapshot> snapshots = order.stream()
            .map(players::get)
            .map(MutablePlayerBet::snapshot)
            .toList();
        return new BettingRoundSnapshot(
            snapshots, currentPlayerId(), currentBet, minimumRaise, complete, lastStandingPlayerId()
        );
    }

    private long performRaise(MutablePlayerBet player, Long targetAmount) {
        if (targetAmount == null) throw new IllegalArgumentException("raise target is required");
        long maximumTarget = player.streetContribution + player.remainingChips;
        long minimumTarget = currentBet + minimumRaise;
        if (targetAmount < minimumTarget || targetAmount > maximumTarget) {
            throw new IllegalArgumentException("raise target must be between " + minimumTarget + " and " + maximumTarget);
        }

        long previousBet = currentBet;
        long committed = commit(player, targetAmount - player.streetContribution);
        currentBet = targetAmount;
        minimumRaise = currentBet - previousBet;
        reopenActionAfterFullRaise(player.playerId);
        return committed;
    }

    private long performAllIn(MutablePlayerBet player) {
        long previousBet = currentBet;
        long committed = commit(player, player.remainingChips);
        if (player.streetContribution > currentBet) {
            currentBet = player.streetContribution;
            long raiseSize = currentBet - previousBet;
            if (raiseSize >= minimumRaise) {
                minimumRaise = raiseSize;
                reopenActionAfterFullRaise(player.playerId);
            }
        }
        return committed;
    }

    private void reopenActionAfterFullRaise(String raiserId) {
        actedSinceFullRaise.clear();
        raiseRights.clear();
        players.values().stream()
            .filter(player -> player.status == BettingStatus.ACTIVE)
            .map(player -> player.playerId)
            .filter(id -> !id.equals(raiserId))
            .forEach(raiseRights::add);
    }

    private long commit(MutablePlayerBet player, long amount) {
        if (amount < 0 || amount > player.remainingChips) {
            throw new IllegalArgumentException("invalid chip commitment: " + amount);
        }
        player.remainingChips -= amount;
        player.streetContribution += amount;
        if (player.remainingChips == 0) player.status = BettingStatus.ALL_IN;
        return amount;
    }

    private long callAmount(MutablePlayerBet player) {
        return Math.max(0, currentBet - player.streetContribution);
    }

    private boolean needsAction(MutablePlayerBet player) {
        return player.status == BettingStatus.ACTIVE
            && (!actedSinceFullRaise.contains(player.playerId) || player.streetContribution < currentBet);
    }

    private void advanceFrom(int previousIndex) {
        for (int offset = 1; offset <= order.size(); offset++) {
            int candidate = (previousIndex + offset) % order.size();
            if (needsAction(players.get(order.get(candidate)))) {
                currentIndex = candidate;
                return;
            }
        }
        currentIndex = -1;
    }

    private void updateCompletion() {
        long contenders = players.values().stream()
            .filter(player -> player.status != BettingStatus.FOLDED)
            .count();
        complete = contenders <= 1 || players.values().stream().noneMatch(this::needsAction);
        if (complete) currentIndex = -1;
    }

    private String lastStandingPlayerId() {
        List<String> contenders = players.values().stream()
            .filter(player -> player.status != BettingStatus.FOLDED)
            .map(player -> player.playerId)
            .toList();
        return contenders.size() == 1 ? contenders.getFirst() : null;
    }

    private String currentPlayerId() {
        return currentIndex < 0 ? null : order.get(currentIndex);
    }

    private MutablePlayerBet requirePlayer(String playerId) {
        MutablePlayerBet player = players.get(playerId);
        if (player == null) throw new IllegalArgumentException("unknown player: " + playerId);
        return player;
    }

    private void ensureNotStarted() {
        if (started) throw new IllegalStateException("betting round has already started");
    }

    private void ensureStartedAndOpen() {
        if (!started) throw new IllegalStateException("betting round has not started");
        if (complete) throw new IllegalStateException("betting round is complete");
    }

    private static final class MutablePlayerBet {
        private final String playerId;
        private long remainingChips;
        private long streetContribution;
        private BettingStatus status;

        private MutablePlayerBet(String playerId, long remainingChips) {
            this.playerId = playerId;
            this.remainingChips = remainingChips;
            this.status = remainingChips == 0 ? BettingStatus.ALL_IN : BettingStatus.ACTIVE;
        }

        private PlayerBetSnapshot snapshot() {
            return new PlayerBetSnapshot(playerId, remainingChips, streetContribution, status);
        }
    }
}
