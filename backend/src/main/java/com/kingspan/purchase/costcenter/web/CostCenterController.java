package com.kingspan.purchase.costcenter.web;

import com.kingspan.purchase.costcenter.application.CostCenterService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Listagem para qualquer usuário autenticado; o POST é exclusivo de ADMIN (SecurityFilterChain). */
@RestController
@RequestMapping("/cost-centers")
class CostCenterController {

    private final CostCenterService costCenterService;

    CostCenterController(CostCenterService costCenterService) {
        this.costCenterService = costCenterService;
    }

    @GetMapping
    List<CostCenterResponse> list() {
        return costCenterService.list().stream().map(CostCenterResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CostCenterResponse create(@Valid @RequestBody CreateCostCenterRequest request) {
        return CostCenterResponse.from(costCenterService.create(request.code(), request.name()));
    }
}
