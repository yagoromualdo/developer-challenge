package com.kingspan.purchase.shared.error;

/** Entrada inválida que o Bean Validation não expressa (ex.: campo de ordenação fora da whitelist); vira 400. */
public class InvalidInputException extends RuntimeException {

    private final FieldViolation violation;

    public InvalidInputException(String field, String message) {
        super(field + ": " + message);
        this.violation = new FieldViolation(field, message);
    }

    public FieldViolation violation() {
        return violation;
    }
}
