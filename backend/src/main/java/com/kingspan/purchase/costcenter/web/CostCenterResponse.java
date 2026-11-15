package com.kingspan.purchase.costcenter.web;

import com.kingspan.purchase.costcenter.application.CostCenterView;
import java.time.Instant;

record CostCenterResponse(Long id, String code, String name, Instant createdAt) {

    static CostCenterResponse from(CostCenterView view) {
        return new CostCenterResponse(view.id(), view.code(), view.name(), view.createdAt());
    }
}
