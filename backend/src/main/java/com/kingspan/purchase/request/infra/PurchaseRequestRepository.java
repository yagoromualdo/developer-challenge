package com.kingspan.purchase.request.infra;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseRequestRepository extends JpaRepository<PurchaseRequestEntity, Long> {}
