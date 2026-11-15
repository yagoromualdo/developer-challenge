package com.kingspan.purchase.request.web;

import com.kingspan.purchase.request.application.IdempotencyService;
import com.kingspan.purchase.request.application.IdempotentResponse;
import com.kingspan.purchase.request.application.PurchaseRequestService;
import com.kingspan.purchase.shared.domain.Actor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

/** Sem {@code @PreAuthorize}: a autorização fina fica no service, na ordem 404 → 403 → 409 → 422. */
@RestController
@RequestMapping("/requests")
class PurchaseRequestController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";

    private final PurchaseRequestService requestService;
    private final IdempotencyService idempotency;
    private final JsonMapper json;

    PurchaseRequestController(PurchaseRequestService requestService, IdempotencyService idempotency, JsonMapper json) {
        this.requestService = requestService;
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

    @GetMapping("/{id}")
    PurchaseRequestResponse get(Actor actor, @PathVariable Long id) {
        return PurchaseRequestResponse.from(requestService.get(actor, id));
    }

    @PutMapping("/{id}")
    PurchaseRequestResponse edit(
            Actor actor, @PathVariable Long id, @Valid @RequestBody UpdatePurchaseRequestRequest request) {
        return PurchaseRequestResponse.from(requestService.edit(actor, id, request.lockVersion(), request.toDraft()));
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
