package com.kingspan.purchase.request.application;

import com.kingspan.purchase.budget.application.BudgetService;
import com.kingspan.purchase.request.domain.ActionPolicy;
import com.kingspan.purchase.request.domain.HistoryAction;
import com.kingspan.purchase.request.domain.RequestAction;
import com.kingspan.purchase.request.domain.RequestSnapshot;
import com.kingspan.purchase.request.domain.RequestStateMachine;
import com.kingspan.purchase.request.domain.RequestStatus;
import com.kingspan.purchase.request.domain.StepStatus;
import com.kingspan.purchase.request.infra.ApprovalStepEntity;
import com.kingspan.purchase.request.infra.PurchaseRequestEntity;
import com.kingspan.purchase.request.infra.PurchaseRequestRepository;
import com.kingspan.purchase.request.infra.RequestSnapshotMapper;
import com.kingspan.purchase.request.infra.RequestVersionEntity;
import com.kingspan.purchase.shared.domain.Actor;
import com.kingspan.purchase.shared.web.RequestId;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decisões do aprovador sobre a etapa atual (aprovar, rejeitar, devolver), na ordem 404 → 403 → 409 → 422; o
 * orçamento é a última checagem e só existe na aprovação da última etapa.
 */
@Service
public class ApprovalService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalService.class);

    private final PurchaseRequestRepository requests;
    private final RequestActionGuard guard;
    private final RequestHistory history;
    private final ActionPolicy actionPolicy;
    private final BudgetService budgets;
    private final Clock clock;

    public ApprovalService(
            PurchaseRequestRepository requests,
            RequestActionGuard guard,
            RequestHistory history,
            ActionPolicy actionPolicy,
            BudgetService budgets,
            Clock clock) {
        this.requests = requests;
        this.guard = guard;
        this.history = history;
        this.actionPolicy = actionPolicy;
        this.budgets = budgets;
        this.clock = clock;
    }

    /** Na última etapa, consome o orçamento do mês de competência da versão na mesma transação (ADR 001). */
    @Transactional
    public RequestDetailView approve(Actor actor, Long requestId, long lockVersion, String comment) {
        return decide(actor, requestId, RequestAction.APPROVE, lockVersion, comment);
    }

    @Transactional
    public RequestDetailView reject(Actor actor, Long requestId, long lockVersion, String comment) {
        return decide(actor, requestId, RequestAction.REJECT, lockVersion, comment);
    }

    @Transactional
    public RequestDetailView requestChanges(Actor actor, Long requestId, long lockVersion, String comment) {
        return decide(actor, requestId, RequestAction.REQUEST_CHANGES, lockVersion, comment);
    }

    private RequestDetailView decide(
            Actor actor, Long requestId, RequestAction action, long lockVersion, String comment) {
        PurchaseRequestEntity request = guard.loadForAction(actor, requestId, action, lockVersion, comment);
        Instant now = clock.instant();
        RequestStatus from = request.getStatus();
        RequestVersionEntity version = request.getCurrentVersion();
        RequestSnapshot snapshot = RequestSnapshotMapper.toSnapshot(request);
        ApprovalStepEntity step = version.getSteps().get(snapshot.currentStepIndex());
        String note = RequestHistory.normalizeComment(comment);

        step.decide(StepStatus.decidedBy(action), actor.userId(), now, note);
        request.moveTo(RequestStateMachine.next(from, action, snapshot.currentStepPosition()), now);
        // Flush antes do orçamento: na corrida pela mesma etapa, o @Version dá 409 ao perdedor sem tocar no saldo.
        requests.flush();
        if (request.getStatus() == RequestStatus.APPROVED) {
            budgets.consume(request.getCostCenterId(), version.getCompetenceMonth(), version.getTotal());
        }
        history.record(request, actor, HistoryAction.of(action), from, step.getLevel(), note, now);
        log.info(
                "Solicitação {}: {} na etapa {} por {} ({}) de {} para {} (versão {}, requestId {})",
                requestId,
                action,
                step.getLevel(),
                actor.userId(),
                actor.role(),
                from,
                request.getStatus(),
                request.getCurrentVersionNumber(),
                RequestId.current());
        return RequestDetailView.from(
                request, actionPolicy.allowedActions(actor, RequestSnapshotMapper.toSnapshot(request)));
    }
}
