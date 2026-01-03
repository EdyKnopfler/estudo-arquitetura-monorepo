package com.derso.arquitetura.reservasexterno.app;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.derso.arquitetura.reservasexterno.app.dto.CriacaoReservaRequest;
import com.derso.arquitetura.reservasexterno.app.dto.CriacaoReservaResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/reservas")
@RequiredArgsConstructor
public class ReservasController {

    private final ReservasService servico;
    
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CriacaoReservaResponse criarReserva(@Valid @RequestBody CriacaoReservaRequest dados) {
        return new CriacaoReservaResponse(servico.criar(dados.idCliente()));
    }

    @PutMapping("/confirmar/{id}")
    public ResponseEntity<Void> confirmarReserva(@PathVariable("id") UUID id) {
        servico.confirmar(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelarReserva(@PathVariable("id") UUID id) {
        servico.remover(id);
        return ResponseEntity.ok().build();
    }

}
