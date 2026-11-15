package com.kingspan.purchase.budget.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Aceita {@code "15000.00"} (string, como o resto da API) ou número; mais de 2 casas é 400, nunca arredondado. */
record DefineBudgetRequest(
        @NotNull(message = "é obrigatório")
        @DecimalMin(value = "0.00", message = "não pode ser negativo")
        @Digits(
                integer = 17,
                fraction = 2,
                message = "deve ter no máximo {integer} dígitos inteiros e {fraction} casas decimais")
        BigDecimal amount) {}
