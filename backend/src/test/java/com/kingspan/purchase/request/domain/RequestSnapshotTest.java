package com.kingspan.purchase.request.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RequestSnapshotTest {

    @Test
    void etapaAtualDeveSerAPrimeiraPendente() {
        var snapshot = new RequestSnapshot(
                1L,
                10L,
                RequestStatus.PENDING_APPROVAL,
                List.of(
                        ApprovalStep.approvedBy(StepLevel.NIVEL_1, 2L),
                        ApprovalStep.pending(StepLevel.NIVEL_2),
                        ApprovalStep.pending(StepLevel.ADMIN)));

        assertThat(snapshot.currentStep()).contains(ApprovalStep.pending(StepLevel.NIVEL_2));
        assertThat(snapshot.stepApprovedBy(2L)).contains(ApprovalStep.approvedBy(StepLevel.NIVEL_1, 2L));
        assertThat(snapshot.stepApprovedBy(3L)).isEmpty();
    }

    @Test
    void naoDeveHaverEtapaAtualSemEtapas() {
        assertThat(new RequestSnapshot(1L, 10L, RequestStatus.DRAFT, List.of()).currentStep())
                .isEmpty();
    }

    @Test
    void etapasDevemSerCopiadasDefensivamente() {
        List<ApprovalStep> steps = new ArrayList<>(List.of(ApprovalStep.pending(StepLevel.NIVEL_1)));
        var snapshot = new RequestSnapshot(1L, 10L, RequestStatus.PENDING_APPROVAL, steps);

        steps.clear();

        assertThat(snapshot.steps()).hasSize(1);
        assertThatThrownBy(() -> snapshot.steps().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void etapaPendenteNaoDeveTerQuemDecidiu() {
        assertThatThrownBy(() -> new ApprovalStep(StepLevel.NIVEL_1, StepStatus.PENDING, 2L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void etapaDecididaDeveTerQuemDecidiu() {
        assertThatThrownBy(() -> new ApprovalStep(StepLevel.NIVEL_1, StepStatus.APPROVED, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void etapaRejeitadaNaoContaComoAprovada() {
        var rejected = new ApprovalStep(StepLevel.NIVEL_1, StepStatus.REJECTED, 2L);

        assertThat(rejected.wasApprovedBy(2L)).isFalse();
    }
}
