package com.kingspan.purchase.request.application;

import com.kingspan.purchase.costcenter.application.CostCenterService;
import com.kingspan.purchase.request.domain.ActionPolicy;
import com.kingspan.purchase.request.domain.ApprovalChainBuilder;
import com.kingspan.purchase.request.domain.HistoryAction;
import com.kingspan.purchase.request.domain.Money;
import com.kingspan.purchase.request.domain.RequestAction;
import com.kingspan.purchase.request.domain.RequestItem;
import com.kingspan.purchase.request.domain.RequestStateMachine;
import com.kingspan.purchase.request.domain.RequestStatus;
import com.kingspan.purchase.request.domain.StepLevel;
import com.kingspan.purchase.request.infra.PurchaseRequestEntity;
import com.kingspan.purchase.request.infra.PurchaseRequestRepository;
import com.kingspan.purchase.request.infra.RequestItemEntity;
import com.kingspan.purchase.request.infra.RequestSnapshotMapper;
import com.kingspan.purchase.request.infra.RequestSpecifications;
import com.kingspan.purchase.request.infra.RequestVersionEntity;
import com.kingspan.purchase.request.infra.RequestVisibility;
import com.kingspan.purchase.shared.domain.Actor;
import com.kingspan.purchase.shared.domain.BusinessTime;
import com.kingspan.purchase.shared.error.BusinessRuleException;
import com.kingspan.purchase.shared.error.ErrorCode;
import com.kingspan.purchase.shared.web.RequestId;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Criação, edição, submissão, cancelamento, detalhe e listagem; as ações seguem a ordem do README 4.3: 404 → 403 → 409
 * → 422.
 */
@Service
public class PurchaseRequestService {

    private static final Logger log = LoggerFactory.getLogger(PurchaseRequestService.class);

    private final PurchaseRequestRepository requests;
    private final RequestActionGuard guard;
    private final RequestHistory history;
    private final CostCenterService costCenters;
    private final ActionPolicy actionPolicy;
    private final ApprovalChainBuilder chainBuilder;
    private final Clock clock;

    public PurchaseRequestService(
            PurchaseRequestRepository requests,
            RequestActionGuard guard,
            RequestHistory history,
            CostCenterService costCenters,
            ActionPolicy actionPolicy,
            ApprovalChainBuilder chainBuilder,
            Clock clock) {
        this.requests = requests;
        this.guard = guard;
        this.history = history;
        this.costCenters = costCenters;
        this.actionPolicy = actionPolicy;
        this.chainBuilder = chainBuilder;
        this.clock = clock;
    }

    /** {@code costCenterId} nulo usa o do ator; só um ADMIN pode abrir a solicitação em outro centro de custo. */
    @Transactional
    public RequestDetailView create(Actor actor, Long costCenterId, RequestDraft draft) {
        Long targetCostCenter = resolveCostCenter(actor, costCenterId);
        Instant now = clock.instant();
        PurchaseRequestEntity request = requests.saveAndFlush(new PurchaseRequestEntity(
                actor.userId(), targetCostCenter, RequestStatus.DRAFT, newVersion(draft, now), now));
        history.record(request, actor, HistoryAction.CREATE, null, null, null, now);
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
        PurchaseRequestEntity request = guard.loadForAction(actor, requestId, RequestAction.EDIT, lockVersion, null);
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
        history.record(request, actor, HistoryAction.EDIT, request.getStatus(), null, null, now);
        log.info(
                "Solicitação {} editada por {} (versão {}, total {})",
                requestId,
                actor.userId(),
                request.getCurrentVersionNumber(),
                draft.total());
        return detail(actor, request);
    }

    /**
     * Congela a versão atual, monta a cadeia pelo total e categoria dela e grava o mês de competência: o mês da
     * submissão em {@code America/Sao_Paulo}, pelo {@link Clock}, que o orçamento usa na aprovação final.
     */
    @Transactional
    public RequestDetailView submit(Actor actor, Long requestId, long lockVersion) {
        PurchaseRequestEntity request = guard.loadForAction(actor, requestId, RequestAction.SUBMIT, lockVersion, null);
        Instant now = clock.instant();
        RequestStatus from = request.getStatus();
        RequestVersionEntity version = versionToSubmit(request, now);
        List<StepLevel> chain = chainBuilder.build(Money.of(version.getTotal()), version.getCategory());
        version.markSubmitted(now, BusinessTime.competenceOf(now));
        version.defineChain(chain);
        request.moveTo(RequestStateMachine.next(from, RequestAction.SUBMIT), now);
        requests.flush();
        history.record(request, actor, HistoryAction.SUBMIT, from, null, null, now);
        log.info(
                "Solicitação {} submetida por {} (versão {}, cadeia {}, competência {}, requestId {})",
                requestId,
                actor.userId(),
                request.getCurrentVersionNumber(),
                chain,
                version.getCompetenceMonth(),
                RequestId.current());
        return detail(actor, request);
    }

    /** Dono ou ADMIN, em DRAFT, PENDING_APPROVAL ou CHANGES_REQUESTED; o comentário opcional vai para o histórico. */
    @Transactional
    public RequestDetailView cancel(Actor actor, Long requestId, long lockVersion, String comment) {
        PurchaseRequestEntity request = guard.loadForAction(actor, requestId, RequestAction.CANCEL, lockVersion, null);
        Instant now = clock.instant();
        RequestStatus from = request.getStatus();
        request.moveTo(RequestStateMachine.next(from, RequestAction.CANCEL), now);
        requests.flush();
        history.record(request, actor, HistoryAction.CANCEL, from, null, RequestHistory.normalizeComment(comment), now);
        log.info(
                "Solicitação {} cancelada por {} ({}) a partir de {} (versão {}, requestId {})",
                requestId,
                actor.userId(),
                actor.role(),
                from,
                request.getCurrentVersionNumber(),
                RequestId.current());
        return detail(actor, request);
    }

    @Transactional(readOnly = true)
    public RequestDetailView get(Actor actor, Long requestId) {
        return detail(actor, guard.findVisible(actor, requestId));
    }

    /** Listagem dentro da visibilidade do ator: quem filtra por um centro de custo que não vê recebe página vazia. */
    @Transactional(readOnly = true)
    public Page<RequestSummaryView> list(Actor actor, RequestFilter filter, RequestSort sort, int page, int size) {
        Specification<PurchaseRequestEntity> spec = RequestVisibility.visibleTo(actor)
                .and(filtersOf(filter))
                .and(RequestSpecifications.orderedBy(sort.field(), sort.direction()));
        Page<PurchaseRequestEntity> found = requests.findAll(spec, PageRequest.of(page, size));
        List<Long> ids = found.map(PurchaseRequestEntity::getId).getContent();
        Map<Long, RequestVersionEntity> currentVersions = ids.isEmpty()
                ? Map.of()
                : requests.findCurrentVersions(ids).stream()
                        .collect(
                                Collectors.toMap(version -> version.getRequest().getId(), Function.identity()));
        return found.map(request -> RequestSummaryView.from(request, currentVersions.get(request.getId())));
    }

    private static Specification<PurchaseRequestEntity> filtersOf(RequestFilter filter) {
        List<Specification<PurchaseRequestEntity>> specs = new ArrayList<>();
        if (!filter.statuses().isEmpty()) {
            specs.add(RequestSpecifications.statusIn(filter.statuses()));
        }
        if (!filter.categories().isEmpty()) {
            specs.add(RequestSpecifications.categoryIn(filter.categories()));
        }
        if (filter.costCenterId() != null) {
            specs.add(RequestSpecifications.inCostCenter(filter.costCenterId()));
        }
        if (filter.createdFrom() != null) {
            specs.add(RequestSpecifications.createdAtOrAfter(startOfDay(filter.createdFrom())));
        }
        if (filter.createdTo() != null) {
            specs.add(RequestSpecifications.createdBefore(
                    startOfDay(filter.createdTo().plusDays(1))));
        }
        return Specification.allOf(specs);
    }

    private static Instant startOfDay(LocalDate day) {
        return day.atStartOfDay(BusinessTime.ZONE).toInstant();
    }

    // Reenvio sem edição: a versão atual já foi submetida e é imutável (ADR 002), então o submit cria a v+1.
    private static RequestVersionEntity versionToSubmit(PurchaseRequestEntity request, Instant now) {
        RequestVersionEntity current = request.getCurrentVersion();
        if (current.getSubmittedAt() == null) {
            return current;
        }
        request.addVersion(current.copyForNextVersion(now), now);
        return request.getCurrentVersion();
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
}
