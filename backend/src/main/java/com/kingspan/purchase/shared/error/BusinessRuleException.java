package com.kingspan.purchase.shared.error;

/** Violação de regra de negócio; vira 422 com {@code code} e mensagem explicativa em pt-BR. */
public class BusinessRuleException extends RuntimeException {

    private final ErrorCode code;

    public BusinessRuleException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
