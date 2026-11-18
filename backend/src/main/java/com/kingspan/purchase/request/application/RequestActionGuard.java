package com.kingspan.purchase.request.application;

import com.kingspan.purchase.request.domain.ActionPolicy;
import com.kingspan.purchase.request.domain.RequestAction;
import com.kingspan.purchase.request.infra.PurchaseRequestEntity;
import com.kingspan.purchase.request.infra.PurchaseRequestRepository;
import com.kingspan.purchase.request.infra.RequestSnapshotMapper;
import com.kingspan.purchase.request.infra.RequestVisibility;
import com.kingspan.purchase.shared.domain.Actor;
import com.kingspan.purchase.shared.error.ResourceNotFoundException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/** Ordem do README 4.3 para toda ação: visibilidade (404) → papel (403) → {@code lockVersion} (409) → regra (422). */
@Component
class RequestActionGuard {

    private final PurchaseRequestRepository requests;
    private final ActionPolicy actionPolicy;

    RequestActionGuard(PurchaseRequestRepository requests, ActionPolicy actionPolicy) {
        this.requests = requests;
        this.actionPolicy = actionPolicy;
    }

    /**
     * Precisa ser a primeira leitura da solicitação na transação: se ela já estiver no contexto de persistência, o
     * Hibernate devolve a instância em cache e a snapshot pode voltar a misturar commits diferentes (ADR 001).
     */
    // Mesma mensagem para inexistente e fora da visibilidade: o 404 não revela que a solicitação existe (ADR 003).
    PurchaseRequestEntity findVisible(Actor actor, Long requestId) {
        // Snapshot coerente num único SELECT; o findOne abaixo devolve a mesma instância gerenciada, sem reler o estado.
        requests.findCurrentVersionWithSteps(requestId);
        return requests.findOne(RequestVisibility.withId(requestId).and(RequestVisibility.visibleTo(actor)))
                .orElseThrow(() -> new ResourceNotFoundException("Solicitação " + requestId + " não encontrada."));
    }

    /** O {@code comment} só é avaliado nas ações que o exigem (rejeitar e devolver). */
    PurchaseRequestEntity loadForAction(
            Actor actor, Long requestId, RequestAction action, long lockVersion, String comment) {
        PurchaseRequestEntity request = findVisible(actor, requestId);
        requireRoleAllows(actor, action);
        requireCurrentLockVersion(request, lockVersion);
        actionPolicy.check(actor, RequestSnapshotMapper.toSnapshot(request), action, comment);
        return request;
    }

    private static void requireRoleAllows(Actor actor, RequestAction action) {
        if (!ActionPolicy.roleAllows(actor.role(), action)) {
            throw new AccessDeniedException("O papel " + actor.role() + " não permite " + action.phrase() + ".");
        }
    }

    private static void requireCurrentLockVersion(PurchaseRequestEntity request, long lockVersion) {
        if (request.getLockVersion() != lockVersion) {
            throw new OptimisticLockingFailureException("lockVersion " + lockVersion + " diverge do atual "
                    + request.getLockVersion() + " na solicitação " + request.getId());
        }
    }
}
