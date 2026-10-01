package com.pokernight.application.persistence;

import com.pokernight.application.room.MemberStatus;

public record MemberSnapshot(String playerId, String displayName, MemberStatus status, Integer seatNumber) { }
