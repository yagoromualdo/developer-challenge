package com.kingspan.purchase.request.infra;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface PurchaseRequestRepository
        extends JpaRepository<PurchaseRequestEntity, Long>, JpaSpecificationExecutor<PurchaseRequestEntity> {

    /** Versões atuais de uma página da listagem numa só consulta, sem carregar as coleções uma a uma (N+1). */
    @Query("""
            select v from RequestVersionEntity v
            where v.request.id in :requestIds and v.versionNumber = v.request.currentVersionNumber
            """)
    List<RequestVersionEntity> findCurrentVersions(Collection<Long> requestIds);
}
