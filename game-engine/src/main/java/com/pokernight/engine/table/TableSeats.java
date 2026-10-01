package com.pokernight.engine.table;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Owns seat occupancy and clockwise table order. It contains no game rules. */
public final class TableSeats {
    public static final int MAX_SEATS = 6;

    private final Map<Integer, PlayerSeat> bySeat = new HashMap<>();
    private final Map<String, Integer> seatByPlayer = new HashMap<>();

    public void seat(PlayerSeat seat) {
        if (bySeat.containsKey(seat.number())) {
            throw new IllegalStateException("seat " + seat.number() + " is occupied");
        }
        if (seatByPlayer.containsKey(seat.playerId())) {
            throw new IllegalStateException("player is already seated: " + seat.playerId());
        }
        bySeat.put(seat.number(), seat);
        seatByPlayer.put(seat.playerId(), seat.number());
    }

    public Optional<PlayerSeat> remove(String playerId) {
        Integer number = seatByPlayer.remove(playerId);
        return number == null ? Optional.empty() : Optional.ofNullable(bySeat.remove(number));
    }

    public Optional<PlayerSeat> player(String playerId) {
        Integer number = seatByPlayer.get(playerId);
        return number == null ? Optional.empty() : Optional.ofNullable(bySeat.get(number));
    }

    public List<PlayerSeat> occupiedClockwise() {
        List<PlayerSeat> result = new ArrayList<>(bySeat.values());
        result.sort((left, right) -> Integer.compare(left.number(), right.number()));
        return Collections.unmodifiableList(result);
    }

    public List<PlayerSeat> clockwiseFromExclusive(int seatNumber, Collection<String> eligiblePlayerIds) {
        List<PlayerSeat> result = new ArrayList<>();
        for (int offset = 1; offset <= MAX_SEATS; offset++) {
            int candidate = Math.floorMod(seatNumber + offset, MAX_SEATS);
            PlayerSeat seat = bySeat.get(candidate);
            if (seat != null && eligiblePlayerIds.contains(seat.playerId())) {
                result.add(seat);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public int size() {
        return bySeat.size();
    }
}
