package com.kingspan.purchase.request.web;

import com.kingspan.purchase.request.application.RequestVersionSummaryView;
import com.kingspan.purchase.request.domain.Category;
import java.time.Instant;

/** Item de {@code GET /requests/:id/versions}; {@code submittedAt} nulo indica a versão de trabalho ainda em edição. */
record RequestVersionSummaryResponse(
        int version,
        boolean current,
        String title,
        Category category,
        String total,
        Instant submittedAt,
        String competenceMonth,
        Instant createdAt) {

    static RequestVersionSummaryResponse from(RequestVersionSummaryView view) {
        return new RequestVersionSummaryResponse(
                view.versionNumber(),
                view.current(),
                view.title(),
                view.category(),
                view.total().setScale(2).toPlainString(),
                view.submittedAt(),
                view.competenceMonth() == null ? null : view.competenceMonth().toString(),
                view.createdAt());
    }
}
