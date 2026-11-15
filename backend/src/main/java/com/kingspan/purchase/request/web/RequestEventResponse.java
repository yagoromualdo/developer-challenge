package com.kingspan.purchase.request.web;

import com.kingspan.purchase.request.application.RequestEventView;
import com.kingspan.purchase.request.domain.HistoryAction;
import com.kingspan.purchase.request.domain.RequestStatus;
import com.kingspan.purchase.request.domain.StepLevel;
import com.kingspan.purchase.shared.domain.Role;
import java.time.Instant;

/**
 * Evento do histórico. {@code requestId} é o {@code X-Request-Id} da requisição que gerou o evento, com o mesmo nome
 * do campo nos erros (Problem Details), e não o id da solicitação, que já está na URL.
 */
record RequestEventResponse(
        Long id,
        int version,
        Long actorId,
        Role actorRole,
        HistoryAction action,
        RequestStatus fromStatus,
        RequestStatus toStatus,
        StepLevel stepLevel,
        String comment,
        Instant occurredAt,
        String requestId) {

    static RequestEventResponse from(RequestEventView view) {
        return new RequestEventResponse(
                view.id(),
                view.versionNumber(),
                view.actorId(),
                view.actorRole(),
                view.action(),
                view.fromStatus(),
                view.toStatus(),
                view.stepLevel(),
                view.comment(),
                view.occurredAt(),
                view.httpRequestId());
    }
}
