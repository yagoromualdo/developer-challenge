package com.kingspan.purchase.shared.error;

/** Recurso inexistente ou fora da visibilidade do usuário; vira 404 com {@code code} NOT_FOUND. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
