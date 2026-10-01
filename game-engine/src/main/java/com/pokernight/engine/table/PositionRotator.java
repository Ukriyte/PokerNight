package com.pokernight.engine.table;

import java.util.Collection;
import java.util.ArrayList;
import java.util.List;

/** Rotates the dealer button and derives blinds/first actors, including heads-up rules. */
public final class PositionRotator {
    private int previousDealerSeat = -1;

    public void restorePreviousDealerSeat(int seatNumber) {
        if (seatNumber < -1) throw new IllegalArgumentException("dealer seat must be -1 or greater");
        previousDealerSeat = seatNumber;
    }

    public TablePositions next(TableSeats seats, Collection<String> activePlayerIds) {
        if (activePlayerIds.size() < 2) {
            throw new IllegalArgumentException("at least two active players are required");
        }

        List<PlayerSeat> clockwise = seats.clockwiseFromExclusive(previousDealerSeat, activePlayerIds);
        if (clockwise.size() != activePlayerIds.size()) {
            throw new IllegalArgumentException("every active player must occupy a seat");
        }

        PlayerSeat dealer = clockwise.getFirst();
        previousDealerSeat = dealer.number();
        List<PlayerSeat> fromDealer = seats.clockwiseFromExclusive(dealer.number(), activePlayerIds);
        List<PlayerSeat> circle = new ArrayList<>(activePlayerIds.size());
        circle.add(dealer);
        circle.addAll(fromDealer);

        PlayerSeat smallBlind;
        PlayerSeat bigBlind;
        PlayerSeat preFlopActor;
        PlayerSeat postFlopActor;

        if (activePlayerIds.size() == 2) {
            smallBlind = dealer;
            bigBlind = circle.get(1);
            preFlopActor = dealer;
            postFlopActor = bigBlind;
        } else {
            smallBlind = circle.get(1);
            bigBlind = circle.get(2);
            preFlopActor = circle.get(3 % circle.size());
            postFlopActor = smallBlind;
        }

        List<String> playerOrder = seats.occupiedClockwise().stream()
            .filter(seat -> activePlayerIds.contains(seat.playerId()))
            .map(PlayerSeat::playerId)
            .toList();

        return new TablePositions(
            dealer.playerId(), smallBlind.playerId(), bigBlind.playerId(),
            preFlopActor.playerId(), postFlopActor.playerId(), playerOrder
        );
    }
}
