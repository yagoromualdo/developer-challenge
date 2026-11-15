package com.kingspan.purchase.request.application;

import com.kingspan.purchase.costcenter.application.CostCenterService;
import com.kingspan.purchase.request.domain.ActionPolicy;
import com.kingspan.purchase.request.domain.HistoryAction;
import com.kingspan.purchase.request.domain.RequestAction;
import com.kingspan.purchase.request.domain.RequestItem;
import com.kingspan.purchase.request.domain.RequestStatus;
import com.kingspan.purchase.request.infra.PurchaseRequestEntity;
import com.kingspan.purchase.request.infra.PurchaseRequestRepository;
import com.kingspan.purchase.request.infra.RequestEventEntity;
import com.kingspan.purchase.request.infra.RequestEventRepository;
import com.kingspan.purchase.request.infra.RequestItemEntity;
import com.kingspan.purchase.request.infra.RequestSnapshotMapper;
import com.kingspan.purchase.request.infra.RequestVersionEntity;
import com.kingspan.purchase.request.infra.RequestVisibility;
import com.kingspan.purchase.shared.domain.Actor;
import com.kingspan.purchase.shared.error.BusinessRuleException;
import com.kingspan.purchase.shared.error.ErrorCode;
import com.kingspan.purchase.shared.error.ResourceNotFoundException;
import com.kingspan.purchase.shared.web.RequestId;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Criação, edição e detalhe; as verificações seguem a ordem do README 4.3: 404 → 403 → 409 → 422. */
@Service
public class PurchaseRequestService {

    private static final Logger log = LoggerFactory.getLogger(PurchaseRequestService.class);

    private final PurchaseRequestRepository requests;
    private final RequestEventRepository events;
    private final CostCenterService costCenters;
    private final ActionPolicy actionPolicy;
    private final Clock clock;

    public PurchaseRequestService(
            PurchaseRequestRepository requests,
            RequestEventRepository events,
            CostCenterService costCenters,
            ActionPolicy actionPolicy,
            Clock clock) {
        this.requests = requests;
        this.events = events;
        this.costCenters = costCenters;
        this.actionPolicy = actionPolicy;
        this.clock = clock;
    }

    /** {@code costCenterId} nulo usa o do ator; só um ADMIN pode abrir a solicitação em outro centro de custo. */
    @Transactional
    public RequestDetailView create(Actor actor, Long costCenterId, RequestDraft draft) {
        Long targetCostCenter = resolveCostCenter(actor, costCenterId);
        Instant now = clock.instant();
        PurchaseRequestEntity request = requests.saveAndFlush(new PurchaseRequestEntity(
                actor.userId(), targetCostCenter, RequestStatus.DRAFT, newVersion(draft, now), now));
        recordEvent(request, actor, HistoryAction.CREATE, null, now);
        log.info(
                "Solicitação {} criada em DRAFT por {} (centro de custo {}, total {})",
                request.getId(),
                actor.userId(),
                targetCostCenter,
                draft.total());
        return detail(actor, request);
    }

    /**
     * Em {@code CHANGES_REQUESTED} a versão atual já foi submetida e é imutável (ADR 002): a edição cria a versão de
     * trabalho v+1; as edições seguintes, antes do reenvio, alteram essa mesma versão.
     */
    @Transactional
    public RequestDetailView edit(Actor actor, Long requestId, long lockVersion, RequestDraft draft) {
        PurchaseRequestEntity request = findVisible(actor, requestId);
        requireRoleAllows(actor, RequestAction.EDIT);
        requireCurrentLockVersion(request, lockVersion);
        actionPolicy.check(actor, RequestSnapshotMapper.toSnapshot(request), RequestAction.EDIT, null);

        Instant now = clock.instant();
        RequestVersionEntity current = request.getCurrentVersion();
        if (current.getSubmittedAt() != null) {
            request.addVersion(newVersion(draft, now), now);
        } else {
            current.updateContent(
                    draft.title(),
                    draft.description(),
                    draft.category(),
                    draft.neededBy(),
                    draft.total().amount(),
                    toItemEntities(draft.items()));
            request.touch(now);
        }
        // O flush incrementa o @Version agora: a resposta já leva o novo lockVersion, e uma corrida residual vira 409.
        requests.flush();
        recordEvent(request, actor, HistoryAction.EDIT, request.getStatus(), now);
        log.info(
                "Solicitação {} editada por {} (versão {}, total {})",
                requestId,
                actor.userId(),
                request.getCurrentVersionNumber(),
                draft.total());
        return detail(actor, request);
    }

    @Transactional(readOnly = true)
    public RequestDetailView get(Actor actor, Long requestId) {
        return detail(actor, findVisible(actor, requestId));
    }

    // Mesma mensagem para inexistente e fora da visibilidade: o 404 não revela que a solicitação existe (ADR 003).
    private PurchaseRequestEntity findVisible(Actor actor, Long requestId) {
        return requests.findOne(RequestVisibility.withId(requestId).and(RequestVisibility.visibleTo(actor)))
                .orElseThrow(() -> new ResourceNotFoundException("Solicitação " + requestId + " não encontrada."));
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

    private Long resolveCostCenter(Actor actor, Long requested) {
        if (requested == null) {
            if (actor.costCenterId() == null) {
                throw new BusinessRuleException(
                        ErrorCode.COST_CENTER_REQUIRED,
                        "Não é possível criar a solicitação: seu usuário não pertence a um centro de custo, então"
                                + " informe o costCenterId.");
            }
            return actor.costCenterId();
        }
        if (!actor.isAdmin() && !requested.equals(actor.costCenterId())) {
            throw new BusinessRuleException(
                    ErrorCode.COST_CENTER_NOT_ALLOWED,
                    "Não é possível criar a solicitação no centro de custo " + requested
                            + ": somente um ADMIN pode abrir solicitações fora do próprio centro de custo.");
        }
        if (!costCenters.exists(requested)) {
            throw new BusinessRuleException(
                    ErrorCode.COST_CENTER_NOT_FOUND,
                    "Não é possível criar a solicitação: o centro de custo " + requested + " não existe.");
        }
        return requested;
    }

    private RequestDetailView detail(Actor actor, PurchaseRequestEntity request) {
        return RequestDetailView.from(
                request, actionPolicy.allowedActions(actor, RequestSnapshotMapper.toSnapshot(request)));
    }

    private static RequestVersionEntity newVersion(RequestDraft draft, Instant now) {
        return new RequestVersionEntity(
                draft.title(),
                draft.description(),
                draft.category(),
                draft.neededBy(),
                draft.total().amount(),
                toItemEntities(draft.items()),
                now);
    }

    private static List<RequestItemEntity> toItemEntities(List<RequestItem> items) {
        return items.stream()
                .map(item -> new RequestItemEntity(
                        item.description(), item.quantity(), item.unitPrice().amount()))
                .toList();
    }

    private void recordEvent(
            PurchaseRequestEntity request, Actor actor, HistoryAction action, RequestStatus from, Instant now) {
        events.save(new RequestEventEntity(
                request.getId(),
                request.getCurrentVersionNumber(),
                actor.userId(),
                actor.role(),
                action,
                from,
                request.getStatus(),
                null,
                null,
                now,
                RequestId.current()));
    }
}
