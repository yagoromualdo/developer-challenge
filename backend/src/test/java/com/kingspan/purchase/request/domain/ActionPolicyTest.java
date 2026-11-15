package com.kingspan.purchase.request.domain;

import static com.kingspan.purchase.request.domain.RequestAction.APPROVE;
import static com.kingspan.purchase.request.domain.RequestAction.CANCEL;
import static com.kingspan.purchase.request.domain.RequestAction.EDIT;
import static com.kingspan.purchase.request.domain.RequestAction.REJECT;
import static com.kingspan.purchase.request.domain.RequestAction.REQUEST_CHANGES;
import static com.kingspan.purchase.request.domain.RequestAction.SUBMIT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kingspan.purchase.shared.domain.Actor;
import com.kingspan.purchase.shared.domain.ApproverLevel;
import com.kingspan.purchase.shared.domain.Role;
import com.kingspan.purchase.shared.error.BusinessRuleException;
import com.kingspan.purchase.shared.error.ErrorCode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ActionPolicyTest {

    private static final long CC = 10L;
    private static final long OTHER_CC = 20L;
    private static final String VALID_COMMENT = "Faltou o orçamento do fornecedor.";

    private static final Actor OWNER = new Actor(1L, Role.SOLICITANTE, CC, null);
    private static final Actor OTHER_REQUESTER = new Actor(7L, Role.SOLICITANTE, CC, null);
    private static final Actor N1 = new Actor(2L, Role.APROVADOR, CC, ApproverLevel.NIVEL_1);
    private static final Actor N2 = new Actor(3L, Role.APROVADOR, CC, ApproverLevel.NIVEL_2);
    private static final Actor N2_OTHER_CC = new Actor(4L, Role.APROVADOR, OTHER_CC, ApproverLevel.NIVEL_2);
    private static final Actor GLOBAL_ADMIN = new Actor(5L, Role.ADMIN, null, null);
    private static final Actor ADMIN_OTHER_CC = new Actor(6L, Role.ADMIN, OTHER_CC, null);

    private final ActionPolicy policy = ActionPolicy.standard();

    private static RequestSnapshot withStatus(RequestStatus status) {
        return new RequestSnapshot(OWNER.userId(), CC, status, List.of());
    }

    private static RequestSnapshot atLevel1() {
        return pending(ApprovalStep.pending(StepLevel.NIVEL_1), ApprovalStep.pending(StepLevel.NIVEL_2), adminStep());
    }

    private static RequestSnapshot atLevel2() {
        return pending(approved(StepLevel.NIVEL_1, N1), ApprovalStep.pending(StepLevel.NIVEL_2), adminStep());
    }

    private static RequestSnapshot atAdminStep() {
        return pending(approved(StepLevel.NIVEL_1, N1), approved(StepLevel.NIVEL_2, N2), adminStep());
    }

    private static RequestSnapshot pending(ApprovalStep... steps) {
        return new RequestSnapshot(OWNER.userId(), CC, RequestStatus.PENDING_APPROVAL, List.of(steps));
    }

    private static RequestSnapshot ownedBy(Actor owner, ApprovalStep... steps) {
        return new RequestSnapshot(owner.userId(), CC, RequestStatus.PENDING_APPROVAL, List.of(steps));
    }

    private static ApprovalStep approved(StepLevel level, Actor by) {
        return ApprovalStep.approvedBy(level, by.userId());
    }

    private static ApprovalStep adminStep() {
        return ApprovalStep.pending(StepLevel.ADMIN);
    }

    private void assertViolation(
            Actor actor, RequestSnapshot request, RequestAction action, String comment, ErrorCode code) {
        assertThatThrownBy(() -> policy.check(actor, request, action, comment))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class, e -> assertThat(e.code()).isEqualTo(code))
                .hasMessageStartingWith("Não é possível " + action.phrase() + ": ");
    }

    @Nested
    class PapelPermiteOTipoDeAcao {

        @ParameterizedTest
        @EnumSource(
                value = RequestAction.class,
                names = {"EDIT", "SUBMIT", "CANCEL"})
        void qualquerPapelPodeEditarSubmeterECancelar(RequestAction action) {
            for (Role role : Role.values()) {
                assertThat(ActionPolicy.roleAllows(role, action))
                        .as(role.name())
                        .isTrue();
            }
        }

        @ParameterizedTest
        @EnumSource(
                value = RequestAction.class,
                names = {"APPROVE", "REJECT", "REQUEST_CHANGES"})
        void somenteAprovadorEAdminDecidemEtapas(RequestAction action) {
            assertThat(ActionPolicy.roleAllows(Role.SOLICITANTE, action)).isFalse();
            assertThat(ActionPolicy.roleAllows(Role.APROVADOR, action)).isTrue();
            assertThat(ActionPolicy.roleAllows(Role.ADMIN, action)).isTrue();
        }
    }

    static Stream<Arguments> cenariosDeAllowedActions() {
        return Stream.of(
                Arguments.of("dono em DRAFT", OWNER, withStatus(RequestStatus.DRAFT), Set.of(EDIT, SUBMIT, CANCEL)),
                Arguments.of(
                        "dono em CHANGES_REQUESTED",
                        OWNER,
                        withStatus(RequestStatus.CHANGES_REQUESTED),
                        Set.of(EDIT, SUBMIT, CANCEL)),
                Arguments.of("dono em PENDING_APPROVAL", OWNER, atLevel1(), Set.of(CANCEL)),
                Arguments.of("NIVEL_1 na etapa NIVEL_1", N1, atLevel1(), Set.of(APPROVE, REJECT, REQUEST_CHANGES)),
                Arguments.of("NIVEL_2 na etapa NIVEL_1", N2, atLevel1(), Set.of(APPROVE, REJECT, REQUEST_CHANGES)),
                Arguments.of("NIVEL_1 na etapa NIVEL_2", N1, atLevel2(), Set.of()),
                Arguments.of("NIVEL_2 na etapa ADMIN", N2, atAdminStep(), Set.of()),
                Arguments.of("NIVEL_2 de outro centro", N2_OTHER_CC, atLevel1(), Set.of()),
                Arguments.of(
                        "ADMIN global na etapa NIVEL_1",
                        GLOBAL_ADMIN,
                        atLevel1(),
                        Set.of(APPROVE, REJECT, REQUEST_CHANGES, CANCEL)),
                Arguments.of(
                        "ADMIN na etapa ADMIN",
                        ADMIN_OTHER_CC,
                        atAdminStep(),
                        Set.of(APPROVE, REJECT, REQUEST_CHANGES, CANCEL)),
                Arguments.of("outro solicitante em PENDING_APPROVAL", OTHER_REQUESTER, atLevel1(), Set.of()),
                Arguments.of(
                        "outro solicitante em CHANGES_REQUESTED",
                        OTHER_REQUESTER,
                        withStatus(RequestStatus.CHANGES_REQUESTED),
                        Set.of()),
                Arguments.of("dono em APPROVED", OWNER, withStatus(RequestStatus.APPROVED), Set.of()),
                Arguments.of("ADMIN em REJECTED", GLOBAL_ADMIN, withStatus(RequestStatus.REJECTED), Set.of()),
                Arguments.of("ADMIN em CANCELLED", GLOBAL_ADMIN, withStatus(RequestStatus.CANCELLED), Set.of()));
    }

    @ParameterizedTest(name = "{0} → {3}")
    @MethodSource("cenariosDeAllowedActions")
    void deveMontarAllowedActions(String cenario, Actor actor, RequestSnapshot request, Set<RequestAction> expected) {
        assertThat(policy.allowedActions(actor, request)).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void deveDevolverAllowedActionsNaOrdemDoEnum() {
        assertThat(policy.allowedActions(GLOBAL_ADMIN, atLevel1()))
                .containsExactly(APPROVE, REJECT, REQUEST_CHANGES, CANCEL);
    }

    @Test
    void allowedActionsDeveConcordarComAValidacaoEmTodasAsCombinacoes() {
        List<Actor> actors = List.of(OWNER, OTHER_REQUESTER, N1, N2, N2_OTHER_CC, GLOBAL_ADMIN, ADMIN_OTHER_CC);
        List<RequestSnapshot> requests = new ArrayList<>(List.of(atLevel1(), atLevel2(), atAdminStep()));
        Arrays.stream(RequestStatus.values())
                .filter(status -> status != RequestStatus.PENDING_APPROVAL)
                .forEach(status -> requests.add(withStatus(status)));
        requests.add(ownedBy(N2, ApprovalStep.pending(StepLevel.NIVEL_1)));

        int combinations = 0;
        for (Actor actor : actors) {
            for (RequestSnapshot request : requests) {
                Set<RequestAction> allowed = policy.allowedActions(actor, request);
                for (RequestAction action : RequestAction.values()) {
                    boolean passesCheck = ActionPolicy.roleAllows(actor.role(), action)
                            && passes(() -> policy.check(actor, request, action, VALID_COMMENT));
                    assertThat(allowed.contains(action))
                            .as("%s / %s / %s", actor, request.status(), action)
                            .isEqualTo(passesCheck);
                    assertThat(policy.isAllowed(actor, request, action)).isEqualTo(passesCheck);
                    combinations++;
                }
            }
        }
        assertThat(combinations).isEqualTo(7 * 9 * 6);
    }

    private static boolean passes(Runnable check) {
        try {
            check.run();
            return true;
        } catch (BusinessRuleException e) {
            return false;
        }
    }

    @Nested
    class Elegibilidade {

        @Test
        void nivel2DeveAprovarEtapaNivel1() {
            assertThatCode(() -> policy.check(N2, atLevel1(), APPROVE, null)).doesNotThrowAnyException();
        }

        @Test
        void nivel1DeveAprovarEtapaNivel1() {
            assertThatCode(() -> policy.check(N1, atLevel1(), APPROVE, null)).doesNotThrowAnyException();
        }

        @ParameterizedTest
        @MethodSource("com.kingspan.purchase.request.domain.ActionPolicyTest#todasAsEtapas")
        void adminDeveAprovarQualquerEtapaDeQualquerCentroDeCusto(String etapa, RequestSnapshot request) {
            assertThatCode(() -> policy.check(GLOBAL_ADMIN, request, APPROVE, null))
                    .doesNotThrowAnyException();
            assertThatCode(() -> policy.check(ADMIN_OTHER_CC, request, APPROVE, null))
                    .doesNotThrowAnyException();
        }

        @Test
        void etapaAdminSoDeveSerAprovadaPorAdmin() {
            Actor n2SemHistorico = new Actor(8L, Role.APROVADOR, CC, ApproverLevel.NIVEL_2);

            assertThatThrownBy(() -> policy.check(n2SemHistorico, atAdminStep(), APPROVE, null))
                    .isInstanceOfSatisfying(
                            BusinessRuleException.class,
                            e -> assertThat(e.code()).isEqualTo(ErrorCode.APPROVER_NOT_ELIGIBLE))
                    .hasMessage("Não é possível aprovar a solicitação: a etapa atual é ADMIN e exige um ADMIN;"
                            + " você é APROVADOR NIVEL_2.");
        }

        @Test
        void nivel1NaoDeveAprovarEtapaNivel2() {
            Actor n1SemHistorico = new Actor(8L, Role.APROVADOR, CC, ApproverLevel.NIVEL_1);

            assertThatThrownBy(() -> policy.check(n1SemHistorico, atLevel2(), APPROVE, null))
                    .isInstanceOfSatisfying(
                            BusinessRuleException.class,
                            e -> assertThat(e.code()).isEqualTo(ErrorCode.APPROVER_NOT_ELIGIBLE))
                    .hasMessageContaining("a etapa atual é NIVEL_2 e exige um APROVADOR NIVEL_2 ou um ADMIN")
                    .hasMessageContaining("você é APROVADOR NIVEL_1");
        }

        @ParameterizedTest
        @EnumSource(
                value = RequestAction.class,
                names = {"APPROVE", "REJECT", "REQUEST_CHANGES"})
        void aprovadorDeOutroCentroDeCustoNaoDeveDecidir(RequestAction action) {
            assertViolation(N2_OTHER_CC, atLevel1(), action, VALID_COMMENT, ErrorCode.APPROVER_NOT_ELIGIBLE);
            assertThatThrownBy(() -> policy.check(N2_OTHER_CC, atLevel1(), action, VALID_COMMENT))
                    .hasMessageContaining("centro de custo 10")
                    .hasMessageContaining("(20)");
        }

        @Test
        void solicitanteNaoDeveSerElegivelMesmoSeOServiceNaoChecarOPapel() {
            assertViolation(OTHER_REQUESTER, atLevel1(), APPROVE, null, ErrorCode.APPROVER_NOT_ELIGIBLE);
        }
    }

    static Stream<Arguments> todasAsEtapas() {
        return Stream.of(
                Arguments.of("NIVEL_1", atLevel1()),
                Arguments.of("NIVEL_2", atLevel2()),
                Arguments.of("ADMIN", atAdminStep()));
    }

    @Nested
    class SegregacaoDeFuncoes {

        @ParameterizedTest
        @EnumSource(
                value = RequestAction.class,
                names = {"APPROVE", "REJECT", "REQUEST_CHANGES"})
        void aprovadorNaoDeveDecidirAPropriaSolicitacao(RequestAction action) {
            RequestSnapshot ownRequest = ownedBy(N2, ApprovalStep.pending(StepLevel.NIVEL_1));

            assertViolation(N2, ownRequest, action, VALID_COMMENT, ErrorCode.SELF_APPROVAL_FORBIDDEN);
        }

        @Test
        void adminNaoDeveAprovarAPropriaSolicitacao() {
            RequestSnapshot ownRequest = ownedBy(GLOBAL_ADMIN, ApprovalStep.pending(StepLevel.ADMIN));

            assertThatThrownBy(() -> policy.check(GLOBAL_ADMIN, ownRequest, APPROVE, null))
                    .isInstanceOfSatisfying(
                            BusinessRuleException.class,
                            e -> assertThat(e.code()).isEqualTo(ErrorCode.SELF_APPROVAL_FORBIDDEN))
                    .hasMessage("Não é possível aprovar a solicitação: você é o dono desta solicitação, e ninguém"
                            + " pode decidir o próprio pedido (segregação de funções).");
        }

        @Test
        void mesmoAprovadorNaoDeveAprovarDuasEtapasDaMesmaVersao() {
            RequestSnapshot n2ApprovedLevel1 =
                    pending(approved(StepLevel.NIVEL_1, N2), ApprovalStep.pending(StepLevel.NIVEL_2));

            assertThatThrownBy(() -> policy.check(N2, n2ApprovedLevel1, APPROVE, null))
                    .isInstanceOfSatisfying(
                            BusinessRuleException.class,
                            e -> assertThat(e.code()).isEqualTo(ErrorCode.DUPLICATE_APPROVER))
                    .hasMessage("Não é possível aprovar a solicitação: você já aprovou a etapa NIVEL_1 desta versão"
                            + " (segregação de funções).");
        }

        @Test
        void adminQueAprovouNivel2NaoDeveAprovarEtapaAdmin() {
            RequestSnapshot adminApprovedLevel2 =
                    pending(approved(StepLevel.NIVEL_1, N1), approved(StepLevel.NIVEL_2, GLOBAL_ADMIN), adminStep());

            assertViolation(GLOBAL_ADMIN, adminApprovedLevel2, APPROVE, null, ErrorCode.DUPLICATE_APPROVER);
            assertThatCode(() -> policy.check(ADMIN_OTHER_CC, adminApprovedLevel2, APPROVE, null))
                    .doesNotThrowAnyException();
        }

        @ParameterizedTest
        @EnumSource(
                value = RequestAction.class,
                names = {"REJECT", "REQUEST_CHANGES"})
        void quemAprovouOutraEtapaTambemNaoDeveRejeitarNemDevolver(RequestAction action) {
            RequestSnapshot n2ApprovedLevel1 =
                    pending(approved(StepLevel.NIVEL_1, N2), ApprovalStep.pending(StepLevel.NIVEL_2));

            assertViolation(N2, n2ApprovedLevel1, action, VALID_COMMENT, ErrorCode.DUPLICATE_APPROVER);
        }

        @Test
        void donoTemPrioridadeSobreAsDemaisViolacoes() {
            Actor ownerFromOtherCc = new Actor(9L, Role.APROVADOR, OTHER_CC, ApproverLevel.NIVEL_1);
            RequestSnapshot request = ownedBy(ownerFromOtherCc, adminStep());

            assertViolation(ownerFromOtherCc, request, APPROVE, null, ErrorCode.SELF_APPROVAL_FORBIDDEN);
        }

        @Test
        void quandoNinguemPodeAprovarAEtapaAtualNaoEErroDaPolitica() {
            // Sem o outro ADMIN, a etapa só ficaria pendente, sem erro (README 2.3, regra 7).
            RequestSnapshot request = ownedBy(GLOBAL_ADMIN, approved(StepLevel.NIVEL_1, N1), adminStep());

            for (Actor actor : List.of(OWNER, N1, N2, N2_OTHER_CC, GLOBAL_ADMIN)) {
                assertThat(policy.isAllowed(actor, request, APPROVE))
                        .as(actor.toString())
                        .isFalse();
            }
            assertThat(policy.isAllowed(ADMIN_OTHER_CC, request, APPROVE)).isTrue();
        }
    }

    @Nested
    class Comentario {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "curto", "123456789", "   123456789   ", "\t\n123456789\n"})
        void rejeitarSemComentarioDeDezCaracteresDeveFalhar(String comment) {
            assertViolation(N1, atLevel1(), REJECT, comment, ErrorCode.COMMENT_REQUIRED);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "curto", "123456789"})
        void devolverSemComentarioDeDezCaracteresDeveFalhar(String comment) {
            assertViolation(N1, atLevel1(), REQUEST_CHANGES, comment, ErrorCode.COMMENT_REQUIRED);
        }

        @Test
        void mensagemDeveInformarOMinimoEOTamanhoRecebido() {
            assertThatThrownBy(() -> policy.check(N1, atLevel1(), REJECT, "  curto  "))
                    .hasMessage("Não é possível rejeitar a solicitação: o comentário é obrigatório e precisa ter pelo"
                            + " menos 10 caracteres (recebido: 5).");
        }

        @ParameterizedTest
        @ValueSource(strings = {"1234567890", "  1234567890  ", "Preço acima do mercado."})
        void comentarioComDezCaracteresOuMaisDeveSerAceito(String comment) {
            assertThatCode(() -> policy.check(N1, atLevel1(), REJECT, comment)).doesNotThrowAnyException();
            assertThatCode(() -> policy.check(N1, atLevel1(), REQUEST_CHANGES, comment))
                    .doesNotThrowAnyException();
        }

        @Test
        void emojiDeveContarComoUmCaractere() {
            // 9 letras + 1 emoji (2 chars UTF-16) = 10 caracteres.
            assertThatCode(() -> policy.check(N1, atLevel1(), REJECT, "Sem nota!😀"))
                    .doesNotThrowAnyException();
            assertViolation(N1, atLevel1(), REJECT, "Sem nota😀", ErrorCode.COMMENT_REQUIRED);
        }

        @ParameterizedTest
        @NullAndEmptySource
        void comentarioDeveSerOpcionalAoAprovar(String comment) {
            assertThatCode(() -> policy.check(N1, atLevel1(), APPROVE, comment)).doesNotThrowAnyException();
        }

        @Test
        void elegibilidadeDeveSerVerificadaAntesDoComentario() {
            assertViolation(N1, atLevel2(), REJECT, null, ErrorCode.APPROVER_NOT_ELIGIBLE);
        }
    }

    @Nested
    class DonoEEstado {

        @ParameterizedTest
        @EnumSource(
                value = RequestAction.class,
                names = {"EDIT", "SUBMIT"})
        void somenteODonoDeveEditarESubmeter(RequestAction action) {
            RequestSnapshot request = withStatus(RequestStatus.CHANGES_REQUESTED);

            assertViolation(N2, request, action, null, ErrorCode.NOT_REQUEST_OWNER);
            assertViolation(GLOBAL_ADMIN, request, action, null, ErrorCode.NOT_REQUEST_OWNER);
            assertThatCode(() -> policy.check(OWNER, request, action, null)).doesNotThrowAnyException();
        }

        @Test
        void somenteODonoOuAdminDeveCancelar() {
            assertViolation(N1, atLevel1(), CANCEL, null, ErrorCode.NOT_REQUEST_OWNER);
            assertThatThrownBy(() -> policy.check(N1, atLevel1(), CANCEL, null))
                    .hasMessage("Não é possível cancelar a solicitação: somente o dono da solicitação ou um ADMIN"
                            + " pode cancelá-la.");
            assertThatCode(() -> policy.check(OWNER, atLevel1(), CANCEL, null)).doesNotThrowAnyException();
            assertThatCode(() -> policy.check(GLOBAL_ADMIN, atLevel1(), CANCEL, null))
                    .doesNotThrowAnyException();
        }

        @Test
        void estadoInvalidoDeveUsarAMesmaMensagemDaMaquinaDeEstados() {
            String stateMachineMessage =
                    messageOf(() -> RequestStateMachine.next(RequestStatus.DRAFT, APPROVE, StepPosition.INTERMEDIATE));

            assertThatThrownBy(() -> policy.check(N1, withStatus(RequestStatus.DRAFT), APPROVE, null))
                    .isInstanceOfSatisfying(
                            BusinessRuleException.class,
                            e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_TRANSITION))
                    .hasMessage(stateMachineMessage);
        }

        @ParameterizedTest
        @EnumSource(
                value = RequestStatus.class,
                names = {"APPROVED", "REJECTED", "CANCELLED"})
        void estadoFinalDeveBloquearTodasAsAcoesInclusiveParaOAdmin(RequestStatus status) {
            for (RequestAction action : RequestAction.values()) {
                assertViolation(GLOBAL_ADMIN, withStatus(status), action, VALID_COMMENT, ErrorCode.INVALID_TRANSITION);
            }
        }

        @Test
        void estadoDeveSerVerificadoAntesDoDono() {
            assertViolation(N1, atLevel1(), EDIT, null, ErrorCode.INVALID_TRANSITION);
        }

        @Test
        void pendenteSemEtapaPendenteDeveSerTratadoComoDadoInconsistente() {
            RequestSnapshot broken = pending(approved(StepLevel.NIVEL_1, N1));

            assertThatThrownBy(() -> policy.check(N2, broken, APPROVE, null)).isInstanceOf(IllegalStateException.class);
        }
    }

    private static String messageOf(Runnable action) {
        try {
            action.run();
        } catch (BusinessRuleException e) {
            return e.getMessage();
        }
        throw new AssertionError("Esperava BusinessRuleException");
    }

    @Test
    void deveExigirAoMenosUmaRegraDeAprovacao() {
        assertThatThrownBy(() -> new ActionPolicy(List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allowedActionsDeveSerUmEnumSet() {
        assertThat(policy.allowedActions(OWNER, withStatus(RequestStatus.DRAFT)))
                .isInstanceOf(EnumSet.class);
    }
}
