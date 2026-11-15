package com.kingspan.purchase.request.web;

import com.kingspan.purchase.request.application.ApprovalService;
import com.kingspan.purchase.request.application.IdempotencyService;
import com.kingspan.purchase.request.application.IdempotentResponse;
import com.kingspan.purchase.request.application.PurchaseRequestService;
import com.kingspan.purchase.request.application.RequestFilter;
import com.kingspan.purchase.request.application.RequestSort;
import com.kingspan.purchase.request.domain.Category;
import com.kingspan.purchase.request.domain.RequestStatus;
import com.kingspan.purchase.shared.domain.Actor;
import com.kingspan.purchase.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

/** Sem {@code @PreAuthorize}: a autorização fina fica no service, na ordem 404 → 403 → 409 → 422. */
@RestController
@RequestMapping("/requests")
class PurchaseRequestController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";
    static final int MAX_PAGE_SIZE = 100;

    private final PurchaseRequestService requestService;
    private final ApprovalService approvalService;
    private final IdempotencyService idempotency;
    private final JsonMapper json;

    PurchaseRequestController(
            PurchaseRequestService requestService,
            ApprovalService approvalService,
            IdempotencyService idempotency,
            JsonMapper json) {
        this.requestService = requestService;
        this.approvalService = approvalService;
        this.idempotency = idempotency;
        this.json = json;
    }

    /** Com {@code Idempotency-Key}, a repetição do mesmo corpo devolve a resposta salva em vez de criar outra. */
    @PostMapping
    ResponseEntity<PurchaseRequestResponse> create(
            Actor actor,
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false)
                    @Pattern(regexp = "\\p{Graph}{1,255}", message = "deve ter de 1 a 255 caracteres ASCII visíveis")
                    String idempotencyKey,
            @Valid @RequestBody CreatePurchaseRequestRequest request) {
        if (idempotencyKey == null) {
            return created(HttpStatus.CREATED.value(), createNow(actor, request), false);
        }
        IdempotentResponse result = idempotency.execute(
                actor.userId(),
                idempotencyKey,
                request.fingerprint(),
                () -> IdempotentResponse.fresh(
                        HttpStatus.CREATED.value(), json.writeValueAsString(createNow(actor, request))));
        return created(
                result.status(), json.readValue(result.body(), PurchaseRequestResponse.class), result.replayed());
    }

    /** {@code status} e {@code category} aceitam vários valores ({@code status=APPROVED,REJECTED} ou repetidos). */
    @GetMapping
    PageResponse<PurchaseRequestSummaryResponse> list(
            Actor actor,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size,
            @RequestParam(defaultValue = RequestSort.DEFAULT) String sort,
            @RequestParam(required = false) Set<RequestStatus> status,
            @RequestParam(required = false) Set<Category> category,
            @RequestParam(required = false) Long costCenterId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo) {
        RequestFilter filter = new RequestFilter(status, category, costCenterId, createdFrom, createdTo);
        return PageResponse.from(
                requestService.list(actor, filter, RequestSort.parse(sort), page, size),
                PurchaseRequestSummaryResponse::from);
    }

    @GetMapping("/{id}")
    PurchaseRequestResponse get(Actor actor, @PathVariable Long id) {
        return PurchaseRequestResponse.from(requestService.get(actor, id));
    }

    @GetMapping("/{id}/versions")
    List<RequestVersionSummaryResponse> listVersions(Actor actor, @PathVariable Long id) {
        return requestService.listVersions(actor, id).stream()
                .map(RequestVersionSummaryResponse::from)
                .toList();
    }

    @GetMapping("/{id}/versions/{versionNumber}")
    RequestVersionResponse getVersion(Actor actor, @PathVariable Long id, @PathVariable int versionNumber) {
        return RequestVersionResponse.from(requestService.getVersion(actor, id, versionNumber));
    }

    @GetMapping("/{id}/history")
    List<RequestEventResponse> history(Actor actor, @PathVariable Long id) {
        return requestService.history(actor, id).stream()
                .map(RequestEventResponse::from)
                .toList();
    }

    @PutMapping("/{id}")
    PurchaseRequestResponse edit(
            Actor actor, @PathVariable Long id, @Valid @RequestBody UpdatePurchaseRequestRequest request) {
        return PurchaseRequestResponse.from(requestService.edit(actor, id, request.lockVersion(), request.toDraft()));
    }

    @PostMapping("/{id}/submit")
    PurchaseRequestResponse submit(
            Actor actor, @PathVariable Long id, @Valid @RequestBody SubmitPurchaseRequestRequest request) {
        return PurchaseRequestResponse.from(requestService.submit(actor, id, request.lockVersion()));
    }

    @PostMapping("/{id}/cancel")
    PurchaseRequestResponse cancel(
            Actor actor, @PathVariable Long id, @Valid @RequestBody CancelPurchaseRequestRequest request) {
        return PurchaseRequestResponse.from(requestService.cancel(actor, id, request.lockVersion(), request.comment()));
    }

    @PostMapping("/{id}/approve")
    PurchaseRequestResponse approve(
            Actor actor, @PathVariable Long id, @Valid @RequestBody StepDecisionRequest request) {
        return PurchaseRequestResponse.from(
                approvalService.approve(actor, id, request.lockVersion(), request.comment()));
    }

    @PostMapping("/{id}/reject")
    PurchaseRequestResponse reject(
            Actor actor, @PathVariable Long id, @Valid @RequestBody StepDecisionRequest request) {
        return PurchaseRequestResponse.from(
                approvalService.reject(actor, id, request.lockVersion(), request.comment()));
    }

    @PostMapping("/{id}/request-changes")
    PurchaseRequestResponse requestChanges(
            Actor actor, @PathVariable Long id, @Valid @RequestBody StepDecisionRequest request) {
        return PurchaseRequestResponse.from(
                approvalService.requestChanges(actor, id, request.lockVersion(), request.comment()));
    }

    private PurchaseRequestResponse createNow(Actor actor, CreatePurchaseRequestRequest request) {
        return PurchaseRequestResponse.from(requestService.create(actor, request.costCenterId(), request.toDraft()));
    }

    private static ResponseEntity<PurchaseRequestResponse> created(
            int status, PurchaseRequestResponse body, boolean replayed) {
        return ResponseEntity.status(status)
                .location(URI.create("/requests/" + body.id()))
                .header(IDEMPOTENT_REPLAYED, String.valueOf(replayed))
                .body(body);
    }
}
