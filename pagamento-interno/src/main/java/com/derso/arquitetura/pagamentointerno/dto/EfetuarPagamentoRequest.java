package com.derso.arquitetura.pagamentointerno.dto;

import java.math.BigDecimal;

// Espelha PagamentoRequestDTO de pagamento-externo (módulo separado, sem tipos compartilhados).
public record EfetuarPagamentoRequest(
    String metodo,
    BigDecimal valor
) {

}
