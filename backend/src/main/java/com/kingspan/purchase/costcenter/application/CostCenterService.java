package com.kingspan.purchase.costcenter.application;

import com.kingspan.purchase.costcenter.infra.CostCenterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Porta de entrada da feature de centros de custo para as outras features (que não importam a infra dela). */
@Service
public class CostCenterService {

    private final CostCenterRepository costCenters;

    public CostCenterService(CostCenterRepository costCenters) {
        this.costCenters = costCenters;
    }

    @Transactional(readOnly = true)
    public boolean exists(Long costCenterId) {
        return costCenters.existsById(costCenterId);
    }
}
