package com.derso.arquitetura.webbase.config;

import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.derso.arquitetura.webbase.jwt.UsuarioInvalidoException;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class TrataErros {

    private static final Logger log = LoggerFactory.getLogger(TrataErros.class);

    // exceção não mapeada abaixo = imprevista (bug, falha de infra); a mensagem dela pode
    // conter detalhe interno (erro de SQL, path de arquivo etc.) — nunca devolver ao cliente,
    // só logar. Os outros handlers devolvem e.getMessage() de propósito: são exceções que a
    // própria aplicação lança com mensagem pensada para o cliente ler.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroDTO> erroGeral(Exception e) {
        log.error("Erro não tratado", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErroDTO(true, "Erro interno. Tente novamente mais tarde."));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErroDTO> tratarJsonOuUuidInvalido(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErroDTO(true, e.getMessage()));
    }

    @ExceptionHandler(UsuarioInvalidoException.class)
    public ResponseEntity<ErroDTO> tratarUsuarioNaoPermitido(UsuarioInvalidoException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErroDTO(true, e.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErroDTO> tratarAcessoNegado(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErroDTO(true, e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroDTO> falhaValidacao(MethodArgumentNotValidException e) {
        String mensagens = e.getBindingResult()
                .getAllErrors()
                .stream()
                .map(error -> {
                    if (error instanceof FieldError) {
                        return ((FieldError) error).getField() + ": " + error.getDefaultMessage();
                    }

                    return error.getDefaultMessage();
                })
                .collect(Collectors.joining("; "));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErroDTO(true, mensagens));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErroDTO> falhaValidacaoDeParametro(ConstraintViolationException e) {
        String mensagens = e.getConstraintViolations()
                .stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining("; "));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErroDTO(true, mensagens));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErroDTO> handleNotFound(EntityNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErroDTO(true, e.getMessage()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErroDTO> tratarExcecoesDeNegocio(BusinessException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErroDTO(true, e.getMessage()));
    }

}
