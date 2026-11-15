package com.kingspan.purchase.request.application;

import com.kingspan.purchase.request.domain.Category;
import com.kingspan.purchase.request.domain.Money;
import com.kingspan.purchase.request.domain.RequestItem;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Conteúdo editável de uma versão, já validado na borda; não tem total porque ele é sempre calculado aqui. */
public record RequestDraft(
        String title, String description, Category category, LocalDate neededBy, List<RequestItem> items) {

    public RequestDraft {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(category, "category");
        items = List.copyOf(Objects.requireNonNull(items, "items"));
    }

    public Money total() {
        return RequestItem.totalOf(items);
    }
}
