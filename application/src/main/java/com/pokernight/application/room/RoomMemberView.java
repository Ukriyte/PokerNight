package com.pokernight.application.room;

public record RoomMemberView(
    String playerId,
    String displayName,
    MemberStatus status,
    Integer seatNumber
) { }
