package com.kingspan.purchase.request.infra;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKeyEntity, Long> {

    Optional<IdempotencyKeyEntity> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    // SQL nativo: a entidade é @Immutable, e o Hibernate recusa DELETE em HQL sobre entidades imutáveis.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value = "DELETE FROM idempotency_key WHERE user_id = :userId AND key = :key AND created_at < :cutoff",
            nativeQuery = true)
    int deleteExpired(@Param("userId") Long userId, @Param("key") String key, @Param("cutoff") Instant cutoff);
}
