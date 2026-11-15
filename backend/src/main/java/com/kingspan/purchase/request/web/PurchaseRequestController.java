package com.kingspan.purchase.request.web;

import com.kingspan.purchase.request.application.PurchaseRequestService;
import com.kingspan.purchase.shared.domain.Actor;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Sem {@code @PreAuthorize}: a autorização fina fica no service, na ordem 404 → 403 → 409 → 422. */
@RestController
@RequestMapping("/requests")
class PurchaseRequestController {

    private final PurchaseRequestService requestService;

    PurchaseRequestController(PurchaseRequestService requestService) {
        this.requestService = requestService;
    }

    @PostMapping
    ResponseEntity<PurchaseRequestResponse> create(
            Actor actor, @Valid @RequestBody CreatePurchaseRequestRequest request) {
        PurchaseRequestResponse created =
                PurchaseRequestResponse.from(requestService.create(actor, request.costCenterId(), request.toDraft()));
        return ResponseEntity.created(URI.create("/requests/" + created.id())).body(created);
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
}
