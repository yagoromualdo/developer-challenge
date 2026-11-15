package com.kingspan.purchase.request.application;

import java.util.Objects;

/** Resposta HTTP já serializada; {@code replayed} indica que veio da tabela {@code idempotency_key}. */
public record IdempotentResponse(int status, String body, boolean replayed) {

    public IdempotentResponse {
        Objects.requireNonNull(body, "body");
    }

    public static IdempotentResponse fresh(int status, String body) {
        return new IdempotentResponse(status, body, false);
    }
}
