package com.kingspan.purchase.request.web;

import com.kingspan.purchase.request.application.ApprovalInboxService;
import com.kingspan.purchase.shared.domain.Actor;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Aberto a qualquer papel: para quem não pode aprovar nada (ex.: {@code SOLICITANTE}) a caixa vem vazia, não 403. */
@RestController
@RequestMapping("/approvals")
class ApprovalInboxController {

    private final ApprovalInboxService inboxService;

    ApprovalInboxController(ApprovalInboxService inboxService) {
        this.inboxService = inboxService;
    }

    @GetMapping("/inbox")
    List<ApprovalInboxItemResponse> inbox(Actor actor) {
        return inboxService.inbox(actor).stream()
                .map(ApprovalInboxItemResponse::from)
                .toList();
    }
}
