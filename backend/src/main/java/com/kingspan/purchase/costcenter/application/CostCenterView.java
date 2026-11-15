package com.kingspan.purchase.costcenter.application;

import com.kingspan.purchase.costcenter.infra.CostCenterEntity;
import java.time.Instant;

public record CostCenterView(Long id, String code, String name, Instant createdAt) {

    static CostCenterView from(CostCenterEntity entity) {
        return new CostCenterView(entity.getId(), entity.getCode(), entity.getName(), entity.getCreatedAt());
    }
}
