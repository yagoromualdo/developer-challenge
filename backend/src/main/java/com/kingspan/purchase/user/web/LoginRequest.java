package com.kingspan.purchase.user.web;

import jakarta.validation.constraints.NotBlank;

record LoginRequest(
        @NotBlank(message = "é obrigatório") String email,
        @NotBlank(message = "é obrigatória") String password) {}
