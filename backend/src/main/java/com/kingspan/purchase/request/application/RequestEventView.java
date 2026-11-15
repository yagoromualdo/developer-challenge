package com.kingspan.purchase.request.application;

import com.kingspan.purchase.request.domain.HistoryAction;
import com.kingspan.purchase.request.domain.RequestStatus;
import com.kingspan.purchase.request.domain.StepLevel;
import com.kingspan.purchase.request.infra.RequestEventEntity;
import com.kingspan.purchase.shared.domain.Role;
import java.time.Instant;

/** Evento do histórico (README 2.5); {@code httpRequestId} é o {@code X-Request-Id} da requisição que o gerou. */
public record RequestEventView(
        Long id,
        int versionNumber,
        Long actorId,
        Role actorRole,
        HistoryAction action,
        RequestStatus fromStatus,
        RequestStatus toStatus,
        StepLevel stepLevel,
        String comment,
        Instant occurredAt,
        String httpRequestId) {

    static RequestEventView from(RequestEventEntity event) {
        return new RequestEventView(
                event.getId(),
                event.getVersionNumber(),
                event.getActorId(),
                event.getActorRole(),
                event.getAction(),
                event.getFromStatus(),
                event.getToStatus(),
                event.getStepLevel(),
                event.getComment(),
                event.getOccurredAt(),
                event.getHttpRequestId());
    }
}
