package com.kingspan.purchase.request.infra;

import com.kingspan.purchase.request.domain.Category;
import com.kingspan.purchase.request.domain.RequestStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

/**
 * Filtros e ordenação da listagem, combinados com {@link RequestVisibility#visibleTo}. Título, categoria e total vêm
 * da versão atual, por um único join filtrado compartilhado entre filtro e ordenação.
 */
public final class RequestSpecifications {

    private RequestSpecifications() {}

    public static Specification<PurchaseRequestEntity> statusIn(Set<RequestStatus> statuses) {
        return (root, query, cb) -> root.get("status").in(statuses);
    }

    public static Specification<PurchaseRequestEntity> categoryIn(Set<Category> categories) {
        return (root, query, cb) -> currentVersion(root, cb).get("category").in(categories);
    }

    public static Specification<PurchaseRequestEntity> inCostCenter(Long costCenterId) {
        return (root, query, cb) -> cb.equal(root.get("costCenterId"), costCenterId);
    }

    public static Specification<PurchaseRequestEntity> createdAtOrAfter(Instant from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    public static Specification<PurchaseRequestEntity> createdBefore(Instant to) {
        return (root, query, cb) -> cb.lessThan(root.get("createdAt"), to);
    }

    /**
     * A ordenação fica na Specification, e não no {@code Sort} do {@code Pageable}: o {@code Sort} por
     * {@code versions.total} criaria um join próprio, sobre todas as versões, e duplicaria linhas. O {@code id} desempata
     * para a paginação ser estável. A query de contagem descarta o {@code orderBy}.
     */
    public static Specification<PurchaseRequestEntity> orderedBy(RequestSortField field, Sort.Direction direction) {
        return (root, query, cb) -> {
            if (!isCountQuery(query)) {
                query.orderBy(
                        order(cb, sortExpression(field, root, cb), direction), order(cb, root.get("id"), direction));
            }
            return cb.conjunction();
        };
    }

    private static Expression<?> sortExpression(
            RequestSortField field, Root<PurchaseRequestEntity> root, CriteriaBuilder cb) {
        return switch (field) {
            case CREATED_AT -> root.get("createdAt");
            case UPDATED_AT -> root.get("updatedAt");
            case STATUS -> root.get("status");
            case TOTAL -> currentVersion(root, cb).get("total");
            case TITLE -> cb.lower(currentVersion(root, cb).get("title"));
        };
    }

    private static Order order(CriteriaBuilder cb, Expression<?> expression, Sort.Direction direction) {
        return direction.isAscending() ? cb.asc(expression) : cb.desc(expression);
    }

    @SuppressWarnings("unchecked")
    private static Join<PurchaseRequestEntity, RequestVersionEntity> currentVersion(
            Root<PurchaseRequestEntity> root, CriteriaBuilder cb) {
        return root.getJoins().stream()
                .filter(join -> join.getAttribute().getName().equals("versions"))
                .map(join -> (Join<PurchaseRequestEntity, RequestVersionEntity>) join)
                .findFirst()
                .orElseGet(() -> {
                    Join<PurchaseRequestEntity, RequestVersionEntity> version = root.join("versions");
                    version.on(cb.equal(version.get("versionNumber"), root.get("currentVersionNumber")));
                    return version;
                });
    }

    private static boolean isCountQuery(CriteriaQuery<?> query) {
        return List.of(Long.class, long.class).contains(query.getResultType());
    }
}
