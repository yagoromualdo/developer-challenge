package com.kingspan.purchase.request.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kingspan.purchase.shared.error.BusinessRuleException;
import com.kingspan.purchase.shared.error.ErrorCode;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Cobre a matriz completa status × ação (6 × 6 = 36 combinações) da tabela 4.2 do README: 10 pares válidos e 26
 * inválidos. A expectativa é escrita aqui de forma independente da implementação.
 */
class RequestStateMachineTest {

    /** Pares (status, ação) válidos segundo a tabela 4.2 (mais EDIT, permitido só em DRAFT e CHANGES_REQUESTED). */
    private static final Set<String> VALID_PAIRS = Set.of(
            "DRAFT:EDIT",
            "DRAFT:SUBMIT",
            "DRAFT:CANCEL",
            "PENDING_APPROVAL:APPROVE",
            "PENDING_APPROVAL:REJECT",
            "PENDING_APPROVAL:REQUEST_CHANGES",
            "PENDING_APPROVAL:CANCEL",
            "CHANGES_REQUESTED:EDIT",
            "CHANGES_REQUESTED:SUBMIT",
            "CHANGES_REQUESTED:CANCEL");

    static Stream<Arguments> todasAsCombinacoes() {
        return Arrays.stream(RequestStatus.values())
                .flatMap(status -> Arrays.stream(RequestAction.values()).map(action -> Arguments.of(status, action)));
    }

    static Stream<Arguments> combinacoesEmEstadoFinal() {
        return todasAsCombinacoes().filter(args -> ((RequestStatus) args.get()[0]).isFinal());
    }

    @Test
    void matrizDeveTer36CombinacoesSendo10Validas() {
        assertThat(todasAsCombinacoes()).hasSize(36);
        assertThat(VALID_PAIRS).hasSize(10);
    }

    @ParameterizedTest(name = "{0} + {1}")
    @MethodSource("todasAsCombinacoes")
    void devePermitirSomenteOsParesDaTabela(RequestStatus from, RequestAction action) {
        assertThat(RequestStateMachine.isAllowed(from, action)).isEqualTo(VALID_PAIRS.contains(from + ":" + action));
    }

    @ParameterizedTest(name = "{0} + {1} ({2}) -> {3}")
    @CsvSource({
        "DRAFT,             EDIT,            INTERMEDIATE, DRAFT",
        "DRAFT,             EDIT,            LAST,         DRAFT",
        "DRAFT,             SUBMIT,          INTERMEDIATE, PENDING_APPROVAL",
        "DRAFT,             SUBMIT,          LAST,         PENDING_APPROVAL",
        "DRAFT,             CANCEL,          INTERMEDIATE, CANCELLED",
        "DRAFT,             CANCEL,          LAST,         CANCELLED",
        "PENDING_APPROVAL,  APPROVE,         INTERMEDIATE, PENDING_APPROVAL",
        "PENDING_APPROVAL,  APPROVE,         LAST,         APPROVED",
        "PENDING_APPROVAL,  REJECT,          INTERMEDIATE, REJECTED",
        "PENDING_APPROVAL,  REJECT,          LAST,         REJECTED",
        "PENDING_APPROVAL,  REQUEST_CHANGES, INTERMEDIATE, CHANGES_REQUESTED",
        "PENDING_APPROVAL,  REQUEST_CHANGES, LAST,         CHANGES_REQUESTED",
        "PENDING_APPROVAL,  CANCEL,          INTERMEDIATE, CANCELLED",
        "PENDING_APPROVAL,  CANCEL,          LAST,         CANCELLED",
        "CHANGES_REQUESTED, EDIT,            INTERMEDIATE, CHANGES_REQUESTED",
        "CHANGES_REQUESTED, EDIT,            LAST,         CHANGES_REQUESTED",
        "CHANGES_REQUESTED, SUBMIT,          INTERMEDIATE, PENDING_APPROVAL",
        "CHANGES_REQUESTED, SUBMIT,          LAST,         PENDING_APPROVAL",
        "CHANGES_REQUESTED, CANCEL,          INTERMEDIATE, CANCELLED",
        "CHANGES_REQUESTED, CANCEL,          LAST,         CANCELLED",
    })
    void deveLevarAoDestinoDaTabelaQuandoATransicaoEValida(
            RequestStatus from, RequestAction action, StepPosition position, RequestStatus expected) {
        assertThat(RequestStateMachine.next(from, action, position)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} + {1} -> INVALID_TRANSITION")
    @CsvSource(
            delimiter = '|',
            value = {
                "DRAFT             | APPROVE         | Não é possível aprovar a solicitação: ela está em DRAFT, e essa ação só é permitida em PENDING_APPROVAL.",
                "DRAFT             | REJECT          | Não é possível rejeitar a solicitação: ela está em DRAFT, e essa ação só é permitida em PENDING_APPROVAL.",
                "DRAFT             | REQUEST_CHANGES | Não é possível devolver a solicitação para ajuste: ela está em DRAFT, e essa ação só é permitida em PENDING_APPROVAL.",
                "PENDING_APPROVAL  | EDIT            | Não é possível editar a solicitação: ela está em PENDING_APPROVAL, e essa ação só é permitida em DRAFT ou CHANGES_REQUESTED.",
                "PENDING_APPROVAL  | SUBMIT          | Não é possível submeter a solicitação: ela está em PENDING_APPROVAL, e essa ação só é permitida em DRAFT ou CHANGES_REQUESTED.",
                "CHANGES_REQUESTED | APPROVE         | Não é possível aprovar a solicitação: ela está em CHANGES_REQUESTED, e essa ação só é permitida em PENDING_APPROVAL.",
                "CHANGES_REQUESTED | REJECT          | Não é possível rejeitar a solicitação: ela está em CHANGES_REQUESTED, e essa ação só é permitida em PENDING_APPROVAL.",
                "CHANGES_REQUESTED | REQUEST_CHANGES | Não é possível devolver a solicitação para ajuste: ela está em CHANGES_REQUESTED, e essa ação só é permitida em PENDING_APPROVAL.",
            })
    void deveLancarInvalidTransitionExplicandoOndeAAcaoEPermitida(
            RequestStatus from, RequestAction action, String expectedMessage) {
        assertInvalidTransition(from, action, expectedMessage);
    }

    @ParameterizedTest(name = "{0} + {1} -> bloqueado (estado final)")
    @MethodSource("combinacoesEmEstadoFinal")
    void deveBloquearQualquerAcaoEmEstadoFinal(RequestStatus from, RequestAction action) {
        String expectedMessage = "Não é possível " + fraseDaAcao(action) + ": ela está em " + from
                + ", um estado final que não aceita mais nenhuma ação.";

        assertInvalidTransition(from, action, expectedMessage);
    }

    /** isFinal() e a tabela descrevem o mesmo fato; se divergirem, a mensagem de erro passa a mentir o motivo. */
    @ParameterizedTest(name = "{0}")
    @EnumSource(RequestStatus.class)
    void estadoFinalDeveSerExatamenteOEstadoSemAcoesNaTabela(RequestStatus status) {
        assertThat(RequestStateMachine.actionsFrom(status).isEmpty()).isEqualTo(status.isFinal());
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(RequestStatus.class)
    void deveListarAsAcoesPossiveisEmCadaEstado(RequestStatus status) {
        Set<RequestAction> expected = EnumSet.noneOf(RequestAction.class);
        for (RequestAction action : RequestAction.values()) {
            if (VALID_PAIRS.contains(status + ":" + action)) {
                expected.add(action);
            }
        }

        assertThat(RequestStateMachine.actionsFrom(status)).containsExactlyInAnyOrderElementsOf(expected);
    }

    private static void assertInvalidTransition(RequestStatus from, RequestAction action, String expectedMessage) {
        for (StepPosition position : StepPosition.values()) {
            assertThatThrownBy(() -> RequestStateMachine.next(from, action, position))
                    .isInstanceOfSatisfying(
                            BusinessRuleException.class,
                            ex -> assertThat(ex.code()).isEqualTo(ErrorCode.INVALID_TRANSITION))
                    .hasMessage(expectedMessage);
        }
    }

    private static String fraseDaAcao(RequestAction action) {
        return switch (action) {
            case EDIT -> "editar a solicitação";
            case SUBMIT -> "submeter a solicitação";
            case APPROVE -> "aprovar a solicitação";
            case REJECT -> "rejeitar a solicitação";
            case REQUEST_CHANGES -> "devolver a solicitação para ajuste";
            case CANCEL -> "cancelar a solicitação";
        };
    }
}
