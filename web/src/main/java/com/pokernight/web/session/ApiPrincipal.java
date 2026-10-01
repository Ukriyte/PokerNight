package com.pokernight.web.session;

import com.pokernight.application.room.RoomCode;

public record ApiPrincipal(String subjectId, RoomCode roomCode, SessionRole role) { }
