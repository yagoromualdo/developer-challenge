package com.kingspan.purchase.request.infra;

import com.kingspan.purchase.request.domain.RequestStatus;
import java.util.Map;
import org.springframework.data.jpa.domain.Specification;

/** Fragmento do {@link PurchaseRequestRepository}: quantas solicitações de cada status atendem a uma Specification. */
public interface RequestStatusCountQuery {

    /** Status sem nenhuma solicitação ficam fora do mapa; um único {@code GROUP BY} no banco. */
    Map<RequestStatus, Long> countByStatus(Specification<PurchaseRequestEntity> spec);
}
