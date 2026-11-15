package com.kingspan.purchase.request.infra;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKeyEntity, Long> {

    Optional<IdempotencyKeyEntity> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);
}
