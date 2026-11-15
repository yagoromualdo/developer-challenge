package com.kingspan.purchase.request.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Corpo de {@code approve}, {@code reject} e {@code request-changes}. O mínimo do comentário ao rejeitar ou devolver é
 * regra de negócio (422 {@code COMMENT_REQUIRED}, na {@code ActionPolicy}); aqui só o limite da coluna.
 */
record StepDecisionRequest(
        @NotNull(message = "é obrigatório") @PositiveOrZero(message = "não pode ser negativo")
        Long lockVersion,

        @Size(max = COMMENT_MAX_LENGTH, message = "deve ter no máximo {max} caracteres")
        String comment) {

    static final int COMMENT_MAX_LENGTH = 2000;
}
