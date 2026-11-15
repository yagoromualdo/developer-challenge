package com.kingspan.purchase.request.domain;

import com.kingspan.purchase.shared.domain.Actor;
import com.kingspan.purchase.shared.domain.Role;
import com.kingspan.purchase.shared.error.BusinessRuleException;
import com.kingspan.purchase.shared.error.ErrorCode;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Única avaliação de ações: gera o 422, o {@code allowedActions} e o filtro da caixa de aprovação (o orçamento fica
 * no UPDATE condicional, ADR 001).
 */
public final class ActionPolicy {

    public static final int MIN_COMMENT_LENGTH = 10;

    private final List<ApprovalRule> approvalRules;

    public ActionPolicy(List<ApprovalRule> approvalRules) {
        Objects.requireNonNull(approvalRules, "approvalRules");
        if (approvalRules.isEmpty()) {
            throw new IllegalArgumentException("É preciso ao menos uma regra de aprovação");
        }
        this.approvalRules = List.copyOf(approvalRules);
    }

    public static ActionPolicy standard() {
        // A ordem define o motivo exibido quando mais de uma regra falha.
        return new ActionPolicy(List.of(
                new NotOwnerRule(),
                new SameCostCenterRule(),
                new SufficientLevelRule(),
                new NoOtherStepApprovedRule()));
    }

    /** Checagem só de papel, feita pelo service antes do {@code lockVersion} para responder 403 (README 4.3). */
    public static boolean roleAllows(Role role, RequestAction action) {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(action, "action");
        return !action.isStepDecision() || role == Role.APROVADOR || role == Role.ADMIN;
    }

    public Set<RequestAction> allowedActions(Actor actor, RequestSnapshot request) {
        Set<RequestAction> allowed = EnumSet.noneOf(RequestAction.class);
        for (RequestAction action : RequestAction.values()) {
            if (isAllowed(actor, request, action)) {
                allowed.add(action);
            }
        }
        return allowed;
    }

    /** Não avalia o comentário, que só chega no corpo da requisição. */
    public boolean isAllowed(Actor actor, RequestSnapshot request, RequestAction action) {
        return roleAllows(actor.role(), action)
                && evaluate(actor, request, action).isEmpty();
    }

    /** Lança o 422 da primeira regra violada; não checa o papel, já tratado com {@link #roleAllows}. */
    public void check(Actor actor, RequestSnapshot request, RequestAction action, String comment) {
        Optional<RuleViolation> violation = evaluate(actor, request, action).or(() -> checkComment(action, comment));
        if (violation.isPresent()) {
            throw new BusinessRuleException(violation.get().code(), message(action, violation.get()));
        }
    }

    private Optional<RuleViolation> evaluate(Actor actor, RequestSnapshot request, RequestAction action) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(action, "action");
        if (!RequestStateMachine.isAllowed(request.status(), action)) {
            // Mesmo motivo da máquina de estados, para o 422 ser idêntico pelos dois caminhos.
            return Optional.of(new RuleViolation(
                    ErrorCode.INVALID_TRANSITION,
                    RequestStateMachine.invalidTransitionReason(request.status(), action)));
        }
        return switch (action) {
            case EDIT, SUBMIT -> ownerOnly(actor, request);
            case CANCEL -> ownerOrAdmin(actor, request);
            case APPROVE, REJECT, REQUEST_CHANGES -> eligibleApprover(actor, request);
        };
    }

    private static Optional<RuleViolation> ownerOnly(Actor actor, RequestSnapshot request) {
        if (request.isOwnedBy(actor.userId())) {
            return Optional.empty();
        }
        return Optional.of(
                new RuleViolation(ErrorCode.NOT_REQUEST_OWNER, "somente o dono da solicitação pode fazer isso."));
    }

    private static Optional<RuleViolation> ownerOrAdmin(Actor actor, RequestSnapshot request) {
        if (actor.isAdmin() || request.isOwnedBy(actor.userId())) {
            return Optional.empty();
        }
        return Optional.of(new RuleViolation(
                ErrorCode.NOT_REQUEST_OWNER, "somente o dono da solicitação ou um ADMIN pode cancelá-la."));
    }

    private Optional<RuleViolation> eligibleApprover(Actor actor, RequestSnapshot request) {
        ApprovalStep currentStep = request.currentStep()
                .orElseThrow(() -> new IllegalStateException(
                        "Solicitação em " + request.status() + " sem etapa pendente na versão atual"));
        for (ApprovalRule rule : approvalRules) {
            Optional<RuleViolation> violation = rule.check(actor, request, currentStep);
            if (violation.isPresent()) {
                return violation;
            }
        }
        return Optional.empty();
    }

    private static Optional<RuleViolation> checkComment(RequestAction action, String comment) {
        if (!action.requiresComment()) {
            return Optional.empty();
        }
        // strip() para que espaços não contem; codePointCount para que um emoji conte como um caractere.
        String text = comment == null ? "" : comment.strip();
        int length = text.codePointCount(0, text.length());
        if (length >= MIN_COMMENT_LENGTH) {
            return Optional.empty();
        }
        return Optional.of(new RuleViolation(
                ErrorCode.COMMENT_REQUIRED,
                "o comentário é obrigatório e precisa ter pelo menos " + MIN_COMMENT_LENGTH + " caracteres (recebido: "
                        + length + ")."));
    }

    private static String message(RequestAction action, RuleViolation violation) {
        return "Não é possível " + action.phrase() + ": " + violation.reason();
    }
}
