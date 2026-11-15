package com.kingspan.purchase.request.infra;

import com.kingspan.purchase.request.domain.ApprovalStep;
import com.kingspan.purchase.request.domain.RequestSnapshot;
import java.util.List;

/**
 * Converte o agregado persistido na visão que a {@code ActionPolicy} avalia. Só copia dados: etapa atual,
 * segregação e invariantes da etapa continuam no domínio.
 */
public final class RequestSnapshotMapper {

    private RequestSnapshotMapper() {}

    /** Usa só as etapas da versão atual, para aprovações de versões anteriores não contarem (ADR 002). */
    public static RequestSnapshot toSnapshot(PurchaseRequestEntity request) {
        List<ApprovalStep> steps = request.getCurrentVersion().getSteps().stream()
                .map(RequestSnapshotMapper::toStep)
                .toList();
        return new RequestSnapshot(request.getOwnerId(), request.getCostCenterId(), request.getStatus(), steps);
    }

    public static ApprovalStep toStep(ApprovalStepEntity step) {
        return new ApprovalStep(step.getLevel(), step.getStatus(), step.getDecidedBy());
    }
}
