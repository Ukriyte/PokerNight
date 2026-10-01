package com.pokernight.web.socket;

/** Stable wire envelope so clients can discriminate polymorphic STOMP messages. */
public record SocketEnvelope(String type, Object payload) {
    public SocketEnvelope {
        if (type == null || type.isBlank()) throw new IllegalArgumentException("type must not be blank");
        if (payload == null) throw new IllegalArgumentException("payload must not be null");
    }
}
