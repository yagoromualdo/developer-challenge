package com.kingspan.purchase.request.application;

import com.kingspan.purchase.request.domain.ActionPolicy;
import com.kingspan.purchase.request.domain.RequestAction;
import com.kingspan.purchase.request.domain.RequestSnapshot;
import com.kingspan.purchase.request.domain.RequestStatus;
import com.kingspan.purchase.request.infra.PurchaseRequestRepository;
import com.kingspan.purchase.request.infra.RequestSnapshotMapper;
import com.kingspan.purchase.request.infra.RequestSpecifications;
import com.kingspan.purchase.request.infra.RequestVersionEntity;
import com.kingspan.purchase.request.infra.RequestVisibility;
import com.kingspan.purchase.shared.domain.Actor;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caixa de aprovação (README 2.4): o banco só traz os candidatos (pendentes e visíveis ao ator), e quem decide é a
 * {@link ActionPolicy}, a mesma do 422 e do {@code allowedActions}. O filtro em memória dispensa repetir as regras em
 * SQL; o custo é ler todos os pendentes visíveis, aceitável nesta escala, e por isso a caixa não é paginada.
 */
@Service
public class ApprovalInboxService {

    // Quem espera há mais tempo aparece primeiro; o id desempata submissões no mesmo instante.
    private static final Comparator<ApprovalInboxItemView> OLDEST_SUBMISSION_FIRST = Comparator.comparing(
                    (ApprovalInboxItemView item) -> item.summary().submittedAt())
            .thenComparing(item -> item.summary().id());

    private final PurchaseRequestRepository requests;
    private final ActionPolicy actionPolicy;

    public ApprovalInboxService(PurchaseRequestRepository requests, ActionPolicy actionPolicy) {
        this.requests = requests;
        this.actionPolicy = actionPolicy;
    }

    @Transactional(readOnly = true)
    public List<ApprovalInboxItemView> inbox(Actor actor) {
        // Só ids na primeira consulta: a segunda monta solicitação, versão e etapas num SELECT coerente (ADR 001).
        List<Long> candidateIds = requests.findIds(RequestVisibility.visibleTo(actor)
                .and(RequestSpecifications.statusIn(Set.of(RequestStatus.PENDING_APPROVAL))));
        if (candidateIds.isEmpty()) {
            return List.of();
        }
        return requests.findCurrentVersionsWithSteps(candidateIds).stream()
                .map(version -> new Candidate(version, RequestSnapshotMapper.toSnapshot(version)))
                .filter(candidate -> actionPolicy.isAllowed(actor, candidate.snapshot(), RequestAction.APPROVE))
                .map(candidate -> ApprovalInboxItemView.from(candidate.version(), candidate.snapshot()))
                .sorted(OLDEST_SUBMISSION_FIRST)
                .toList();
    }

    private record Candidate(RequestVersionEntity version, RequestSnapshot snapshot) {}
}
