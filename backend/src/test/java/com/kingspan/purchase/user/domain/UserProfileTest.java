package com.kingspan.purchase.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kingspan.purchase.shared.domain.ApproverLevel;
import com.kingspan.purchase.shared.domain.Role;
import com.kingspan.purchase.shared.error.BusinessRuleException;
import com.kingspan.purchase.shared.error.ErrorCode;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class UserProfileTest {

    private static final Long CC = 10L;

    @Test
    void deveExigirNivelParaAprovador() {
        assertViolation(() -> new UserProfile(Role.APROVADOR, null, CC), ErrorCode.APPROVER_LEVEL_REQUIRED);
    }

    @Test
    void deveRejeitarNivelForaDoPapelAprovador() {
        assertViolation(
                () -> new UserProfile(Role.SOLICITANTE, ApproverLevel.NIVEL_1, CC),
                ErrorCode.APPROVER_LEVEL_NOT_ALLOWED);
        assertViolation(
                () -> new UserProfile(Role.ADMIN, ApproverLevel.NIVEL_2, null), ErrorCode.APPROVER_LEVEL_NOT_ALLOWED);
    }

    @Test
    void deveExigirCentroDeCustoForaDeAdmin() {
        assertViolation(() -> new UserProfile(Role.SOLICITANTE, null, null), ErrorCode.COST_CENTER_REQUIRED);
        assertThat(new UserProfile(Role.ADMIN, null, null).costCenterId()).isNull();
    }

    @Test
    void deveManterOsValoresAtuaisQuandoAEdicaoNaoInformaOCampo() {
        UserProfile approver = new UserProfile(Role.APROVADOR, ApproverLevel.NIVEL_1, CC);

        assertThat(approver.change(null, null, null)).isEqualTo(approver);
        assertThat(approver.change(null, ApproverLevel.NIVEL_2, null))
                .isEqualTo(new UserProfile(Role.APROVADOR, ApproverLevel.NIVEL_2, CC));
        assertThat(approver.change(null, null, 20L))
                .isEqualTo(new UserProfile(Role.APROVADOR, ApproverLevel.NIVEL_1, 20L));
    }

    @Test
    void deveDescartarONivelAoRebaixarAprovador() {
        UserProfile approver = new UserProfile(Role.APROVADOR, ApproverLevel.NIVEL_2, CC);

        assertThat(approver.change(Role.SOLICITANTE, null, null))
                .isEqualTo(new UserProfile(Role.SOLICITANTE, null, CC));
    }

    @Test
    void deveExigirNivelAoPromoverSolicitanteAAprovador() {
        UserProfile requester = new UserProfile(Role.SOLICITANTE, null, CC);

        assertViolation(() -> requester.change(Role.APROVADOR, null, null), ErrorCode.APPROVER_LEVEL_REQUIRED);
        assertThat(requester.change(Role.APROVADOR, ApproverLevel.NIVEL_1, null))
                .isEqualTo(new UserProfile(Role.APROVADOR, ApproverLevel.NIVEL_1, CC));
    }

    @Test
    void deveExigirCentroDeCustoAoRebaixarAdminGlobal() {
        UserProfile globalAdmin = new UserProfile(Role.ADMIN, null, null);

        assertViolation(() -> globalAdmin.change(Role.SOLICITANTE, null, null), ErrorCode.COST_CENTER_REQUIRED);
        assertThat(globalAdmin.change(Role.SOLICITANTE, null, CC))
                .isEqualTo(new UserProfile(Role.SOLICITANTE, null, CC));
    }

    private static void assertViolation(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(
                        BusinessRuleException.class, ex -> assertThat(ex.code()).isEqualTo(code))
                .message()
                .isNotBlank();
    }
}
