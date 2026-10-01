package com.pokernight.web.socket;

import com.pokernight.web.session.ApiPrincipal;

import java.security.Principal;

public record StompPrincipal(ApiPrincipal apiPrincipal) implements Principal {
    @Override
    public String getName() {
        return apiPrincipal.subjectId();
    }
}
