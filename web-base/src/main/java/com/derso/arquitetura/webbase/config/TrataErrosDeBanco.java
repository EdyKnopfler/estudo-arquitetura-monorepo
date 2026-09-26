package com.derso.arquitetura.webbase.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Fora de TrataErros porque spring-tx só existe nos módulos com banco (ver ErrorHandlingAutoConfiguration).
// HIGHEST_PRECEDENCE: entre advices, o primeiro com qualquer handler compatível vence — sem isso, o
// handler de Exception de TrataErros pegaria antes.
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TrataErrosDeBanco {

    private static final Logger log = LoggerFactory.getLogger(TrataErrosDeBanco.class);

    // unique/FK violada — mensagem genérica: a da exceção traz nome de constraint e SQL
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErroDTO> violacaoDeIntegridade(DataIntegrityViolationException e) {
        log.warn("Violação de integridade", e);
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErroDTO(true, "Dados conflitam com um registro existente"));
    }

}
