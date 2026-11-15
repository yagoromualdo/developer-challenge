package com.kingspan.purchase.request.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Comentário opcional, gravado no histórico; o limite é o da coluna {@code request_event.comment}. */
record CancelPurchaseRequestRequest(
        @NotNull(message = "é obrigatório") @PositiveOrZero(message = "não pode ser negativo")
        Long lockVersion,

        @Size(max = COMMENT_MAX_LENGTH, message = "deve ter no máximo {max} caracteres")
        String comment) {

    static final int COMMENT_MAX_LENGTH = 2000;
}
