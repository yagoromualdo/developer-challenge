package com.kingspan.purchase.request.domain;

import java.util.List;
import java.util.Objects;

/** Item de uma versão da solicitação; o total da versão é sempre a soma dos subtotais, nunca o valor do cliente. */
public record RequestItem(String description, int quantity, Money unitPrice) {

    public RequestItem {
        Objects.requireNonNull(description, "description é obrigatório");
        Objects.requireNonNull(unitPrice, "unitPrice é obrigatório");
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantidade precisa ser maior que zero: " + quantity);
        }
        if (unitPrice.isZero()) {
            throw new IllegalArgumentException("Preço unitário precisa ser maior que zero");
        }
    }

    public Money subtotal() {
        return unitPrice.times(quantity);
    }

    public static Money totalOf(List<RequestItem> items) {
        Objects.requireNonNull(items, "items é obrigatório");
        return items.stream().map(RequestItem::subtotal).reduce(Money.ZERO, Money::plus);
    }
}
