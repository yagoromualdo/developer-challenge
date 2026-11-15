package com.kingspan.purchase.request.domain;

import static com.kingspan.purchase.request.domain.RequestAction.APPROVE;
import static com.kingspan.purchase.request.domain.RequestAction.CANCEL;
import static com.kingspan.purchase.request.domain.RequestAction.EDIT;
import static com.kingspan.purchase.request.domain.RequestAction.REJECT;
import static com.kingspan.purchase.request.domain.RequestAction.REQUEST_CHANGES;
import static com.kingspan.purchase.request.domain.RequestAction.SUBMIT;
import static com.kingspan.purchase.request.domain.RequestStatus.APPROVED;
import static com.kingspan.purchase.request.domain.RequestStatus.CANCELLED;
import static com.kingspan.purchase.request.domain.RequestStatus.CHANGES_REQUESTED;
import static com.kingspan.purchase.request.domain.RequestStatus.DRAFT;
import static com.kingspan.purchase.request.domain.RequestStatus.PENDING_APPROVAL;
import static com.kingspan.purchase.request.domain.RequestStatus.REJECTED;

import com.kingspan.purchase.shared.error.BusinessRuleException;
import com.kingspan.purchase.shared.error.ErrorCode;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Máquina de estados table-driven da solicitação (README, tabela 4.2).
 *
 * <p>A tabela responde apenas "esta ação é possível neste estado e para onde leva". Quem pode executá-la e as demais
 * condições (nível, segregação, comentário, orçamento) são guards avaliados fora daqui, pela política de ações.
 */
public final class RequestStateMachine {

    private static final Map<RequestStatus, Map<RequestAction, Destination>> TRANSITIONS = buildTable();

    private RequestStateMachine() {}

    private static Map<RequestStatus, Map<RequestAction, Destination>> buildTable() {
        Map<RequestStatus, Map<RequestAction, Destination>> table = new EnumMap<>(RequestStatus.class);
        for (RequestStatus status : RequestStatus.values()) {
            table.put(status, new EnumMap<>(RequestAction.class));
        }

        // Edição não está na 4.2, mas a seção 4.1 só permite editar em DRAFT e CHANGES_REQUESTED; o estado não muda.
        table.get(DRAFT).put(EDIT, Destination.fixed(DRAFT));
        table.get(DRAFT).put(SUBMIT, Destination.fixed(PENDING_APPROVAL));
        table.get(DRAFT).put(CANCEL, Destination.fixed(CANCELLED));

        table.get(PENDING_APPROVAL).put(APPROVE, Destination.byStep(PENDING_APPROVAL, APPROVED));
        table.get(PENDING_APPROVAL).put(REJECT, Destination.fixed(REJECTED));
        table.get(PENDING_APPROVAL).put(REQUEST_CHANGES, Destination.fixed(CHANGES_REQUESTED));
        table.get(PENDING_APPROVAL).put(CANCEL, Destination.fixed(CANCELLED));

        table.get(CHANGES_REQUESTED).put(EDIT, Destination.fixed(CHANGES_REQUESTED));
        table.get(CHANGES_REQUESTED).put(SUBMIT, Destination.fixed(PENDING_APPROVAL));
        table.get(CHANGES_REQUESTED).put(CANCEL, Destination.fixed(CANCELLED));

        // APPROVED, REJECTED e CANCELLED ficam sem entradas: estados finais não aceitam nenhuma ação.
        table.replaceAll((status, actions) -> Collections.unmodifiableMap(actions));
        return Collections.unmodifiableMap(table);
    }

    /** Indica se a tabela tem uma transição para a ação no estado informado (sem avaliar guards). */
    public static boolean isAllowed(RequestStatus from, RequestAction action) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(action, "action");
        return TRANSITIONS.get(from).containsKey(action);
    }

    /** Ações que a tabela permite no estado informado (sem avaliar guards). */
    public static Set<RequestAction> actionsFrom(RequestStatus from) {
        Objects.requireNonNull(from, "from");
        Set<RequestAction> actions = TRANSITIONS.get(from).keySet();
        return actions.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(EnumSet.copyOf(actions));
    }

    /**
     * Destino da ação no estado informado.
     *
     * @param position posição da etapa atual; só altera o resultado de {@link RequestAction#APPROVE}
     * @throws BusinessRuleException com {@link ErrorCode#INVALID_TRANSITION} quando a ação não é possível no estado
     */
    public static RequestStatus next(RequestStatus from, RequestAction action, StepPosition position) {
        Objects.requireNonNull(position, "position");
        if (!isAllowed(from, action)) {
            throw new BusinessRuleException(ErrorCode.INVALID_TRANSITION, invalidTransitionMessage(from, action));
        }
        return TRANSITIONS.get(from).get(action).resolve(position);
    }

    /** Destino das ações que não dependem da posição da etapa (todas menos {@link RequestAction#APPROVE}). */
    public static RequestStatus next(RequestStatus from, RequestAction action) {
        if (action == APPROVE) {
            throw new IllegalArgumentException("O destino de APPROVE depende da posição da etapa");
        }
        return next(from, action, StepPosition.INTERMEDIATE);
    }

    private static String invalidTransitionMessage(RequestStatus from, RequestAction action) {
        return "Não é possível " + action.phrase() + ": " + invalidTransitionReason(from, action);
    }

    static String invalidTransitionReason(RequestStatus from, RequestAction action) {
        if (from.isFinal()) {
            return "ela está em " + from + ", um estado final que não aceita mais nenhuma ação.";
        }
        return "ela está em " + from + ", e essa ação só é permitida em " + statusesAllowing(action) + ".";
    }

    private static String statusesAllowing(RequestAction action) {
        return TRANSITIONS.entrySet().stream()
                .filter(entry -> entry.getValue().containsKey(action))
                .map(entry -> entry.getKey().name())
                .collect(Collectors.joining(" ou "));
    }

    /** Destino de uma transição; só a aprovação depende da posição da etapa. */
    private record Destination(RequestStatus whenIntermediate, RequestStatus whenLast) {

        static Destination fixed(RequestStatus to) {
            return new Destination(to, to);
        }

        static Destination byStep(RequestStatus whenIntermediate, RequestStatus whenLast) {
            return new Destination(whenIntermediate, whenLast);
        }

        RequestStatus resolve(StepPosition position) {
            return position == StepPosition.LAST ? whenLast : whenIntermediate;
        }
    }
}
