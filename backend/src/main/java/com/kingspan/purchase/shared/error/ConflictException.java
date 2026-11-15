package com.kingspan.purchase.shared.error;

/** Conflito que não é de concorrência otimista (ex.: {@code Idempotency-Key} reutilizada); vira 409 com {@code code}. */
public class ConflictException extends RuntimeException {

    private final ErrorCode code;

    public ConflictException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
