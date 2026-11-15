package com.kingspan.purchase.request.infra;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestEventRepository extends JpaRepository<RequestEventEntity, Long> {

    // O id desempata eventos no mesmo instante (mesma ordem do índice idx_request_event_request).
    List<RequestEventEntity> findByRequestIdOrderByOccurredAtAscIdAsc(Long requestId);
}
