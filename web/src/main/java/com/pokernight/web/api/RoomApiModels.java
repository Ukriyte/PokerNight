package com.pokernight.web.api;

import com.pokernight.application.room.MemberStatus;
import com.pokernight.application.room.RoomConfig;
import com.pokernight.application.room.RoomMemberView;
import com.pokernight.application.room.RoomView;
import com.pokernight.engine.state.PublicGameState;
import com.pokernight.engine.state.PrivatePlayerState;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Set;
import java.time.Instant;

public final class RoomApiModels {
    private RoomApiModels() { }

    public record CreateRoomRequest(
        @NotBlank @Size(max = 40) String hostName,
        @Min(50) long startingChips,
        @Min(1) long smallBlind,
        @Min(2) long bigBlind,
        @Min(2) @Max(6) int maximumPlayers,
        @Min(0) @Max(5) int botCount,
        String themeId,
        String cardPackId
    ) { }

    public record CreateRoomResponse(
        String roomCode,
        String hostToken,
        String hostUrl,
        String joinUrl,
        String tvUrl
    ) { }

    public record JoinRoomRequest(@NotBlank @Size(max = 40) String displayName) { }

    public record JoinRoomResponse(
        String playerId,
        String playerToken,
        String status
    ) { }

    public record ApprovePlayerRequest(@Min(0) @Max(5) int seatNumber) { }

    public record MemberResponse(
        String playerId,
        String displayName,
        MemberStatus status,
        Integer seatNumber
    ) {
        static MemberResponse from(RoomMemberView member) {
            return new MemberResponse(
                member.playerId(), member.displayName(), member.status(), member.seatNumber()
            );
        }
    }

    public record PublicRoomResponse(
        String roomCode,
        RoomConfig config,
        Set<String> botPlayerIds,
        PublicGameState game
    ) {
        static PublicRoomResponse from(RoomView room) {
            return new PublicRoomResponse(room.code().value(), room.config(), room.botPlayerIds(), room.game());
        }
    }

    public record LobbyResponse(String roomCode, List<MemberResponse> members) {
        public LobbyResponse {
            members = List.copyOf(members);
        }

        static LobbyResponse from(RoomView room) {
            return new LobbyResponse(
                room.code().value(), room.members().stream().map(MemberResponse::from).toList()
            );
        }
    }

    public record MeResponse(
        String playerId,
        String displayName,
        MemberStatus status,
        Integer seatNumber,
        RoomConfig config,
        PublicGameState game,
        PrivatePlayerState privateState
    ) { }

    public record HistoryEntryResponse(
        long sequence,
        long handNumber,
        String eventType,
        String message,
        Instant occurredAt
    ) { }

    public record HistoryPageResponse(
        List<HistoryEntryResponse> entries,
        Long nextCursor
    ) {
        public HistoryPageResponse { entries = List.copyOf(entries); }
    }

    public record HostBootstrapResponse(
        PublicRoomResponse room,
        LobbyResponse lobby,
        HistoryPageResponse history
    ) { }

    public record MembershipStatusNotification(
        String roomCode,
        String playerId,
        MemberStatus status,
        Integer seatNumber,
        String message
    ) { }

    public record RoomClosedNotification(String roomCode, String message) { }
}
