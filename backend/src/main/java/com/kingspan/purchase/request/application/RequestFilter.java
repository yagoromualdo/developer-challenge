package com.kingspan.purchase.request.application;

import com.kingspan.purchase.request.domain.Category;
import com.kingspan.purchase.request.domain.RequestStatus;
import com.kingspan.purchase.shared.error.InvalidInputException;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Set;

/**
 * Filtros da listagem; coleções vazias não filtram. {@code createdFrom} e {@code createdTo} são dias inclusivos no fuso
 * de negócio ({@code America/Sao_Paulo}).
 */
public record RequestFilter(
        Set<RequestStatus> statuses,
        Set<Category> categories,
        Long costCenterId,
        LocalDate createdFrom,
        LocalDate createdTo) {

    public RequestFilter {
        statuses = copy(statuses);
        categories = copy(categories);
        if (createdFrom != null && createdTo != null && createdTo.isBefore(createdFrom)) {
            throw new InvalidInputException(
                    "createdTo", "deve ser igual ou posterior a createdFrom (" + createdFrom + ")");
        }
    }

    private static <T> Set<T> copy(Collection<T> values) {
        return values == null ? Set.of() : Set.copyOf(values);
    }
}
