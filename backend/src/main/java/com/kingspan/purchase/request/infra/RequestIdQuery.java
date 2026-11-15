package com.kingspan.purchase.request.infra;

import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** Fragmento do {@link PurchaseRequestRepository}: ids que atendem a uma Specification, sem carregar entidades. */
public interface RequestIdQuery {

    /**
     * Não põe solicitações no contexto de persistência: uma leitura seguinte com {@code join fetch} monta a foto
     * coerente em vez de reaproveitar instâncias lidas antes (ADR 001).
     */
    List<Long> findIds(Specification<PurchaseRequestEntity> spec);
}
