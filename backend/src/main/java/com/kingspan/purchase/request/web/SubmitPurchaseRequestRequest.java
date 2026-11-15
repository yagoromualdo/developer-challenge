package com.kingspan.purchase.request.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** O {@code lockVersion} vem do detalhe: submeter sobre uma versão que mudou no meio tempo responde 409. */
record SubmitPurchaseRequestRequest(
        @NotNull(message = "é obrigatório") @PositiveOrZero(message = "não pode ser negativo")
        Long lockVersion) {}
