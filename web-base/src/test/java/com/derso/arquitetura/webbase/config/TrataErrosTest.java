package com.derso.arquitetura.webbase.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.core.MethodParameter;

import com.derso.arquitetura.webbase.jwt.UsuarioInvalidoException;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;

class TrataErrosTest {

    private final TrataErros trataErros = new TrataErros();

    @Test
    void erroGeralNaoVazaMensagemInternaAoCliente() {
        Exception erroComDetalheSensivel = new RuntimeException(
            "duplicate key value violates unique constraint \"clientes_email_key\"");

        ResponseEntity<ErroDTO> resposta = trataErros.erroGeral(erroComDetalheSensivel);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resposta.getStatusCode());
        assertTrue(resposta.getBody().error());
        assertFalse(resposta.getBody().message().contains("clientes_email_key"),
            "mensagem interna da exceção não pode vazar no corpo da resposta");
    }

    @Test
    void jsonOuUuidInvalidoRetornaErroDTOCom400() {
        HttpMessageNotReadableException e = new HttpMessageNotReadableException("JSON malformado", (org.springframework.http.HttpInputMessage) null);

        ResponseEntity<ErroDTO> resposta = trataErros.tratarJsonOuUuidInvalido(e);

        assertEquals(HttpStatus.BAD_REQUEST, resposta.getStatusCode());
        assertEquals("JSON malformado", resposta.getBody().message());
    }

    @Test
    void usuarioInvalidoRetornaErroDTOCom403() {
        ResponseEntity<ErroDTO> resposta = trataErros.tratarUsuarioNaoPermitido(new UsuarioInvalidoException());

        assertEquals(HttpStatus.FORBIDDEN, resposta.getStatusCode());
        assertEquals("Usuário inválido", resposta.getBody().message());
    }

    @Test
    void acessoNegadoRetornaErroDTOCom403() {
        ResponseEntity<ErroDTO> resposta = trataErros.tratarAcessoNegado(new AccessDeniedException("sem permissão"));

        assertEquals(HttpStatus.FORBIDDEN, resposta.getStatusCode());
        assertEquals("sem permissão", resposta.getBody().message());
    }

    @Test
    void falhaValidacaoRetornaErroDTOComMensagensDeCampo() throws NoSuchMethodException {
        Object alvo = new Object();
        BindException bindingResult = new BindException(alvo, "alvo");
        bindingResult.addError(new FieldError("alvo", "nome", "não pode ser vazio"));
        MethodParameter parametro = new MethodParameter(getClass().getDeclaredMethod("metodoFalso"), -1);
        MethodArgumentNotValidException e = new MethodArgumentNotValidException(parametro, bindingResult);

        ResponseEntity<ErroDTO> resposta = trataErros.falhaValidacao(e);

        assertEquals(HttpStatus.BAD_REQUEST, resposta.getStatusCode());
        assertEquals("nome: não pode ser vazio", resposta.getBody().message());
    }

    @Test
    void falhaValidacaoDeParametroRetornaErroDTOCom400() {
        ConstraintViolation<?> violacao = mock(ConstraintViolation.class);
        Path caminho = mock(Path.class);
        when(caminho.toString()).thenReturn("id");
        when(violacao.getPropertyPath()).thenReturn(caminho);
        when(violacao.getMessage()).thenReturn("deve ser um UUID válido");

        ResponseEntity<ErroDTO> resposta = trataErros.falhaValidacaoDeParametro(
            new ConstraintViolationException(Set.of(violacao)));

        assertEquals(HttpStatus.BAD_REQUEST, resposta.getStatusCode());
        assertEquals("id: deve ser um UUID válido", resposta.getBody().message());
    }

    @Test
    void entidadeNaoEncontradaRetornaErroDTOCom404() {
        ResponseEntity<ErroDTO> resposta = trataErros.handleNotFound(new EntityNotFoundException("não encontrado"));

        assertEquals(HttpStatus.NOT_FOUND, resposta.getStatusCode());
        assertEquals("não encontrado", resposta.getBody().message());
    }

    @Test
    void excecaoDeNegocioRetornaErroDTOCom409() {
        ResponseEntity<ErroDTO> resposta = trataErros.tratarExcecoesDeNegocio(new BusinessException("conflito de negócio"));

        assertEquals(HttpStatus.CONFLICT, resposta.getStatusCode());
        assertEquals("conflito de negócio", resposta.getBody().message());
    }

    private void metodoFalso() {
    }
}
