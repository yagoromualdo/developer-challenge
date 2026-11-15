package com.kingspan.purchase.request.infra;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** Implementação do fragmento, ligada ao repositório pelo sufixo {@code Impl} (Spring Data). */
class RequestIdQueryImpl implements RequestIdQuery {

    private final EntityManager entityManager;

    RequestIdQueryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public List<Long> findIds(Specification<PurchaseRequestEntity> spec) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<PurchaseRequestEntity> root = query.from(PurchaseRequestEntity.class);
        Predicate predicate = spec.toPredicate(root, query, cb);
        query.select(root.get("id"));
        if (predicate != null) {
            query.where(predicate);
        }
        return entityManager.createQuery(query).getResultList();
    }
}
