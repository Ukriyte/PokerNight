package com.pokernight.engine.table;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TableSeatsAndPositionsTest {
    @Test
    void enforcesUniqueSeatsAndPlayers() {
        TableSeats seats = table();

        assertThatThrownBy(() -> seats.seat(new PlayerSeat(0, "p4", "Four")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("occupied");
        assertThatThrownBy(() -> seats.seat(new PlayerSeat(1, "p1", "One again")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already seated");
    }

    @Test
    void rotatesDealerAndAssignsThreeHandedPositions() {
        TableSeats seats = table();
        PositionRotator rotator = new PositionRotator();

        TablePositions first = rotator.next(seats, Set.of("p1", "p2", "p3"));
        TablePositions second = rotator.next(seats, Set.of("p1", "p2", "p3"));

        assertThat(first.dealerId()).isEqualTo("p1");
        assertThat(first.smallBlindId()).isEqualTo("p2");
        assertThat(first.bigBlindId()).isEqualTo("p3");
        assertThat(first.firstToActPreFlopId()).isEqualTo("p1");
        assertThat(first.firstToActPostFlopId()).isEqualTo("p2");
        assertThat(second.dealerId()).isEqualTo("p2");
    }

    @Test
    void usesHeadsUpDealerAsSmallBlindAndFirstPreFlopActor() {
        TableSeats seats = table();
        PositionRotator rotator = new PositionRotator();

        TablePositions positions = rotator.next(seats, Set.of("p1", "p3"));

        assertThat(positions.dealerId()).isEqualTo("p1");
        assertThat(positions.smallBlindId()).isEqualTo("p1");
        assertThat(positions.bigBlindId()).isEqualTo("p3");
        assertThat(positions.firstToActPreFlopId()).isEqualTo("p1");
        assertThat(positions.firstToActPostFlopId()).isEqualTo("p3");
    }

    private static TableSeats table() {
        TableSeats seats = new TableSeats();
        seats.seat(new PlayerSeat(0, "p1", "One"));
        seats.seat(new PlayerSeat(2, "p2", "Two"));
        seats.seat(new PlayerSeat(5, "p3", "Three"));
        return seats;
    }
}
