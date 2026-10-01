package com.pokernight.web.session;

import java.time.Instant;

public record SessionSnapshot(String token, ApiPrincipal principal, Instant expiresAt) { }
