package com.kingspan.purchase.costcenter.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

record CreateCostCenterRequest(
        @NotBlank(message = "é obrigatório")
        @Size(max = 30, message = "deve ter no máximo {max} caracteres")
        @Pattern(regexp = "[A-Za-z0-9_-]*", message = "deve conter apenas letras, números, '-' e '_'")
        String code,

        @NotBlank(message = "é obrigatório") @Size(max = 120, message = "deve ter no máximo {max} caracteres")
        String name) {}
