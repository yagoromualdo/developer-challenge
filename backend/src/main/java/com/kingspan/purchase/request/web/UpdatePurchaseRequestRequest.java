package com.kingspan.purchase.request.web;

import static com.kingspan.purchase.request.domain.RequestLimits.DESCRIPTION_MAX_LENGTH;
import static com.kingspan.purchase.request.domain.RequestLimits.MAX_ITEMS;
import static com.kingspan.purchase.request.domain.RequestLimits.TITLE_MAX_LENGTH;

import com.kingspan.purchase.request.application.RequestDraft;
import com.kingspan.purchase.request.domain.Category;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * Substitui o conteúdo inteiro da versão. O centro de custo não muda depois da criação; o {@code lockVersion} vem do
 * detalhe e evita que uma edição sobrescreva outra feita no meio tempo (409).
 */
record UpdatePurchaseRequestRequest(
        @NotBlank(message = "é obrigatório")
        @Size(max = TITLE_MAX_LENGTH, message = "deve ter no máximo {max} caracteres")
        String title,

        @NotBlank(message = "é obrigatória")
        @Size(max = DESCRIPTION_MAX_LENGTH, message = "deve ter no máximo {max} caracteres")
        String description,

        @NotNull(message = "é obrigatória") Category category,
        @Future(message = "deve ser uma data futura") LocalDate neededBy,

        @NotEmpty(message = "deve ter pelo menos um item")
        @Size(max = MAX_ITEMS, message = "deve ter no máximo {max} itens")
        List<@NotNull(message = "não pode ser nulo") @Valid RequestItemPayload> items,

        @NotNull(message = "é obrigatório") @PositiveOrZero(message = "não pode ser negativo")
        Long lockVersion) {

    RequestDraft toDraft() {
        return new RequestDraft(
                title,
                description,
                category,
                neededBy,
                items.stream().map(RequestItemPayload::toDomain).toList());
    }
}
