package com.kingspan.purchase.request.application;

import com.kingspan.purchase.request.domain.HistoryAction;
import com.kingspan.purchase.request.domain.RequestStatus;
import com.kingspan.purchase.request.domain.StepLevel;
import com.kingspan.purchase.request.infra.PurchaseRequestEntity;
import com.kingspan.purchase.request.infra.RequestEventEntity;
import com.kingspan.purchase.request.infra.RequestEventRepository;
import com.kingspan.purchase.shared.domain.Actor;
import com.kingspan.purchase.shared.web.RequestId;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/** Grava o evento append-only na transação do caso de uso, com o estado já alterado como destino, e o lê de volta. */
@Component
class RequestHistory {

    private final RequestEventRepository events;

    RequestHistory(RequestEventRepository events) {
        this.events = events;
    }

    void record(
            PurchaseRequestEntity request,
            Actor actor,
            HistoryAction action,
            RequestStatus from,
            StepLevel stepLevel,
            String comment,
            Instant now) {
        events.save(new RequestEventEntity(
                request.getId(),
                request.getCurrentVersionNumber(),
                actor.userId(),
                actor.role(),
                action,
                from,
                request.getStatus(),
                stepLevel,
                comment,
                now,
                RequestId.current()));
    }

    /** Em ordem cronológica, de todas as versões; quem chama já verificou a visibilidade. */
    List<RequestEventView> of(Long requestId) {
        return events.findByRequestIdOrderByOccurredAtAscIdAsc(requestId).stream()
                .map(RequestEventView::from)
                .toList();
    }

    static String normalizeComment(String comment) {
        return comment == null || comment.isBlank() ? null : comment.strip();
    }
}
