package com.kingspan.purchase.request.infra;

import com.kingspan.purchase.request.domain.RequestStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.data.jpa.domain.Specification;

/** Implementação do fragmento, ligada ao repositório pelo sufixo {@code Impl} (Spring Data). */
class RequestStatusCountQueryImpl implements RequestStatusCountQuery {

    private final EntityManager entityManager;

    RequestStatusCountQueryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Map<RequestStatus, Long> countByStatus(Specification<PurchaseRequestEntity> spec) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<PurchaseRequestEntity> root = query.from(PurchaseRequestEntity.class);
        Expression<RequestStatus> status = root.get("status");
        Expression<Long> count = cb.count(root);
        Predicate predicate = spec.toPredicate(root, query, cb);
        query.multiselect(status, count).groupBy(status);
        if (predicate != null) {
            query.where(predicate);
        }
        Map<RequestStatus, Long> counts = new EnumMap<>(RequestStatus.class);
        for (Tuple row : entityManager.createQuery(query).getResultList()) {
            counts.put(row.get(status), row.get(count));
        }
        return counts;
    }
}
