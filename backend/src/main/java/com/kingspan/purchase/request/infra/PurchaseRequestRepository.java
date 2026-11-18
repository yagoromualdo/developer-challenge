package com.kingspan.purchase.request.infra;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface PurchaseRequestRepository
        extends JpaRepository<PurchaseRequestEntity, Long>,
                JpaSpecificationExecutor<PurchaseRequestEntity>,
                RequestIdQuery,
                RequestStatusCountQuery {

    /** Versões atuais de uma página da listagem numa só consulta, sem carregar as coleções uma a uma (N+1). */
    @Query("""
            select v from RequestVersionEntity v
            where v.request.id in :requestIds and v.versionNumber = v.request.currentVersionNumber
            """)
    List<RequestVersionEntity> findCurrentVersions(Collection<Long> requestIds);

    /**
     * Solicitação, versão atual e etapas num único SELECT. Em READ COMMITTED, consultas separadas (lazy) podem misturar
     * o status anterior com as etapas já decididas por uma transação concorrente (ADR 001).
     */
    @Query("""
            select v from RequestVersionEntity v
            join fetch v.request r
            left join fetch v.steps
            where r.id = :requestId and v.versionNumber = r.currentVersionNumber
            """)
    Optional<RequestVersionEntity> findCurrentVersionWithSteps(Long requestId);

    /** A mesma snapshot coerente de {@link #findCurrentVersionWithSteps}, para várias solicitações de uma vez. */
    @Query("""
            select v from RequestVersionEntity v
            join fetch v.request r
            left join fetch v.steps
            where r.id in :requestIds and v.versionNumber = r.currentVersionNumber
            """)
    List<RequestVersionEntity> findCurrentVersionsWithSteps(Collection<Long> requestIds);
}
