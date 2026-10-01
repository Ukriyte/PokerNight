package com.pokernight.application.room;

import com.pokernight.application.persistence.RoomSnapshot;
import com.pokernight.application.persistence.RoomSnapshotStore;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RoomSnapshotRecoveryTest {
    @Test
    void restoresLobbyBotsSeatsAndStacksIntoFreshMemory() {
        var store = new MemoryStore();
        var firstProcess = new RoomManager(store);
        RoomView created = firstProcess.createRoom("host", "Host",
            new RoomConfig(2_000, RoomConfig.standard().blinds(), 4, 1));
        firstProcess.requestToJoin(created.code(), "p1", "Alice");
        firstProcess.approvePlayer(created.code(), "host", "p1", 0);

        var restartedProcess = new RoomManager(store);
        RoomView restored = restartedProcess.getRoom(created.code());

        assertThat(restored.hostId()).isEqualTo("host");
        assertThat(restored.botPlayerIds()).hasSize(1);
        assertThat(restored.members()).singleElement().satisfies(member -> {
            assertThat(member.playerId()).isEqualTo("p1");
            assertThat(member.status()).isEqualTo(MemberStatus.APPROVED);
            assertThat(member.seatNumber()).isZero();
        });
        assertThat(restored.game().players()).hasSize(2)
            .allSatisfy(player -> assertThat(player.chips()).isEqualTo(2_000));
        assertThat(restored.game().handInProgress()).isFalse();
    }

    private static final class MemoryStore implements RoomSnapshotStore {
        private final Map<RoomCode, RoomSnapshot> values = new LinkedHashMap<>();
        @Override public List<RoomSnapshot> loadAll() { return List.copyOf(values.values()); }
        @Override public void save(RoomSnapshot snapshot) {
            values.put(new RoomCode(snapshot.roomCode()), snapshot);
        }
        @Override public void delete(RoomCode roomCode) { values.remove(roomCode); }
    }
}
