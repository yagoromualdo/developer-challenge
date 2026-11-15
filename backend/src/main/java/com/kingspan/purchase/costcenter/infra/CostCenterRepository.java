package com.kingspan.purchase.costcenter.infra;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CostCenterRepository extends JpaRepository<CostCenterEntity, Long> {

    boolean existsByCode(String code);
}
