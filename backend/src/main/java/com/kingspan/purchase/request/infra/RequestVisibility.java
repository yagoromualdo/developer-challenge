package com.kingspan.purchase.request.infra;

import com.kingspan.purchase.request.domain.RequestStatus;
import com.kingspan.purchase.shared.domain.Actor;
import java.util.Objects;
import org.springframework.data.jpa.domain.Specification;

/**
 * Visibilidade das solicitações (README 2.2), única para detalhe, edição, listagem, histórico, versões e dashboard:
 * cada um a combina com o próprio filtro. Quem está fora dela recebe 404, como se a solicitação não existisse (ADR
 * 003).
 */
public final class RequestVisibility {

    private RequestVisibility() {}

    /**
     * {@code SOLICITANTE} vê as próprias; {@code APROVADOR}, também as do seu centro de custo; {@code ADMIN}, todas. Um
     * {@code DRAFT} só é visível para o dono, qualquer que seja o papel.
     */
    public static Specification<PurchaseRequestEntity> visibleTo(Actor actor) {
        Objects.requireNonNull(actor, "actor");
        Specification<PurchaseRequestEntity> owned = ownedBy(actor.userId());
        return switch (actor.role()) {
            case SOLICITANTE -> owned;
            case APROVADOR ->
                actor.costCenterId() == null
                        ? owned
                        : owned.or(inCostCenter(actor.costCenterId()).and(notDraft()));
            case ADMIN -> owned.or(notDraft());
        };
    }

    public static Specification<PurchaseRequestEntity> withId(Long requestId) {
        return (root, query, cb) -> cb.equal(root.get("id"), requestId);
    }

    private static Specification<PurchaseRequestEntity> ownedBy(Long userId) {
        return (root, query, cb) -> cb.equal(root.get("ownerId"), userId);
    }

    private static Specification<PurchaseRequestEntity> inCostCenter(Long costCenterId) {
        return (root, query, cb) -> cb.equal(root.get("costCenterId"), costCenterId);
    }

    private static Specification<PurchaseRequestEntity> notDraft() {
        return (root, query, cb) -> cb.notEqual(root.get("status"), RequestStatus.DRAFT);
    }
}
