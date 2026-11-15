package com.kingspan.purchase.request.web;

import static com.kingspan.purchase.request.domain.RequestLimits.ITEM_DESCRIPTION_MAX_LENGTH;
import static com.kingspan.purchase.request.domain.RequestLimits.MAX_ITEM_QUANTITY;
import static com.kingspan.purchase.request.domain.RequestLimits.UNIT_PRICE_INTEGER_DIGITS;

import com.kingspan.purchase.request.domain.Money;
import com.kingspan.purchase.request.domain.RequestItem;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** {@code unitPrice} como string ({@code "4899.90"}) ou número; mais de 2 casas é 400, nunca arredondado. */
record RequestItemPayload(
        @NotBlank(message = "é obrigatória")
        @Size(max = ITEM_DESCRIPTION_MAX_LENGTH, message = "deve ter no máximo {max} caracteres")
        String description,

        @NotNull(message = "é obrigatória")
        @Positive(message = "deve ser um inteiro maior que zero")
        @Max(value = MAX_ITEM_QUANTITY, message = "deve ser no máximo {value}")
        Integer quantity,

        @NotNull(message = "é obrigatório")
        @DecimalMin(value = "0", inclusive = false, message = "deve ser maior que zero")
        @Digits(
                integer = UNIT_PRICE_INTEGER_DIGITS,
                fraction = 2,
                message = "deve ter no máximo {integer} dígitos inteiros e {fraction} casas decimais")
        BigDecimal unitPrice) {

    RequestItem toDomain() {
        return new RequestItem(description, quantity, Money.of(unitPrice));
    }
}
