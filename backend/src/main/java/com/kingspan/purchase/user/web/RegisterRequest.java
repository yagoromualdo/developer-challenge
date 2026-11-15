package com.kingspan.purchase.user.web;

import com.kingspan.purchase.user.application.AuthService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Não há campo de papel: campos extras como {@code role} são ignorados e o cadastro cria sempre SOLICITANTE. */
record RegisterRequest(
        @NotBlank(message = "é obrigatório") @Size(max = 120, message = "deve ter no máximo {max} caracteres")
        String name,

        @NotBlank(message = "é obrigatório")
        @Email(message = "deve ser um e-mail válido")
        @Size(max = 254, message = "deve ter no máximo {max} caracteres")
        String email,

        @NotNull(message = "é obrigatória")
        @Size(min = 8, message = "deve ter pelo menos {min} caracteres")
        @MaxUtf8Bytes(AuthService.MAX_PASSWORD_BYTES)
        String password,

        @NotNull(message = "é obrigatório") @Positive(message = "deve ser positivo")
        Long costCenterId) {}
