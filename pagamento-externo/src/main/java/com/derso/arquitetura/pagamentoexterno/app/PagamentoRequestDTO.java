package com.derso.arquitetura.pagamentoexterno.app;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PagamentoRequestDTO(
    @NotBlank String metodo,
    @NotNull BigDecimal valor
) {
    
}
