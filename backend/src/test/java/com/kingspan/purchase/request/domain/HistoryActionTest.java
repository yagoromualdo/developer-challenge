package com.kingspan.purchase.request.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class HistoryActionTest {

    @ParameterizedTest
    @EnumSource(RequestAction.class)
    void deveMapearCadaAcaoParaOEventoDeMesmoNome(RequestAction action) {
        assertThat(HistoryAction.of(action).name()).isEqualTo(action.name());
    }

    @ParameterizedTest
    @EnumSource(HistoryAction.class)
    void deveTerSomenteCreateForaDasAcoesDaSolicitacao(HistoryAction event) {
        boolean isRequestAction = Arrays.stream(RequestAction.values())
                .anyMatch(action -> action.name().equals(event.name()));

        assertThat(isRequestAction).isEqualTo(event != HistoryAction.CREATE);
    }
}
