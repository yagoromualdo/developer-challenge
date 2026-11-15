package com.kingspan.purchase.request.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class StepStatusTest {

    @ParameterizedTest
    @CsvSource({"APPROVE, APPROVED", "REJECT, REJECTED", "REQUEST_CHANGES, CHANGES_REQUESTED"})
    void decisaoDoAprovadorDeveDefinirOStatusDaEtapa(RequestAction action, StepStatus expected) {
        assertThat(StepStatus.decidedBy(action)).isEqualTo(expected);
    }

    @ParameterizedTest
    @EnumSource(
            value = RequestAction.class,
            names = {"EDIT", "SUBMIT", "CANCEL"})
    void acoesQueNaoDecidemEtapaDevemSerRecusadas(RequestAction action) {
        assertThatThrownBy(() -> StepStatus.decidedBy(action)).isInstanceOf(IllegalArgumentException.class);
    }
}
