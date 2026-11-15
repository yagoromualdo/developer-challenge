package com.kingspan.purchase.costcenter.application;

import com.kingspan.purchase.costcenter.infra.CostCenterEntity;
import com.kingspan.purchase.costcenter.infra.CostCenterRepository;
import com.kingspan.purchase.shared.error.BusinessRuleException;
import com.kingspan.purchase.shared.error.ErrorCode;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Porta de entrada da feature de centros de custo para as outras features (que não importam a infra dela). */
@Service
public class CostCenterService {

    private final CostCenterRepository costCenters;
    private final Clock clock;

    public CostCenterService(CostCenterRepository costCenters, Clock clock) {
        this.costCenters = costCenters;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public boolean exists(Long costCenterId) {
        return costCenters.existsById(costCenterId);
    }

    @Transactional(readOnly = true)
    public String codeOf(Long costCenterId) {
        return costCenters
                .findById(costCenterId)
                .map(CostCenterEntity::getCode)
                .orElseThrow(() -> new IllegalStateException("Centro de custo " + costCenterId + " não existe"));
    }

    @Transactional(readOnly = true)
    public List<CostCenterView> list() {
        return costCenters.findAll(Sort.by("code")).stream()
                .map(CostCenterView::from)
                .toList();
    }

    /** O código é gravado em maiúsculas para que "cc-ti" e "CC-TI" não virem dois centros de custo. */
    @Transactional
    public CostCenterView create(String code, String name) {
        String normalizedCode = code.toUpperCase(Locale.ROOT);
        if (costCenters.existsByCode(normalizedCode)) {
            throw codeAlreadyExists(normalizedCode);
        }
        try {
            return CostCenterView.from(
                    costCenters.saveAndFlush(new CostCenterEntity(normalizedCode, name.trim(), clock.instant())));
        } catch (DataIntegrityViolationException ex) {
            // Duas criações simultâneas com o mesmo código: a UNIQUE decide e o perdedor recebe o 422.
            throw codeAlreadyExists(normalizedCode);
        }
    }

    private static BusinessRuleException codeAlreadyExists(String code) {
        return new BusinessRuleException(
                ErrorCode.COST_CENTER_CODE_ALREADY_EXISTS,
                "Não é possível criar o centro de custo: o código " + code + " já está em uso.");
    }
}
