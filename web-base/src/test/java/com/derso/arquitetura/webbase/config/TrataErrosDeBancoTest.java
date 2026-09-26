package com.derso.arquitetura.webbase.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class TrataErrosDeBancoTest {

    private final TrataErrosDeBanco trataErros = new TrataErrosDeBanco();

    @Test
    void violacaoDeIntegridadeRetorna409SemVazarConstraint() {
        DataIntegrityViolationException e = new DataIntegrityViolationException(
            "duplicate key value violates unique constraint \"uk_cliente_cpf\"");

        ResponseEntity<ErroDTO> resposta = trataErros.violacaoDeIntegridade(e);

        assertEquals(HttpStatus.CONFLICT, resposta.getStatusCode());
        assertFalse(resposta.getBody().message().contains("uk_cliente_cpf"));
    }

}
