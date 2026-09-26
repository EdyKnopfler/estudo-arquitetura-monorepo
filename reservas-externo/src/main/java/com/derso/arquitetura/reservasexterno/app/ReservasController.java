package com.derso.arquitetura.reservasexterno.app;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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

    public static final String HEADER_SIMULAR_RESULTADO = "X-Simular-Resultado";

    private final ReservasService servico;
    
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CriacaoReservaResponse criarReserva(
        @Valid @RequestBody CriacaoReservaRequest dados,
        @RequestHeader(value = HEADER_SIMULAR_RESULTADO, required = false) String headerSimularResultado
    ) {
        return new CriacaoReservaResponse(servico.criar(dados.idCliente(), headerSimularResultado));
    }

    @PutMapping("/confirmar/{id}")
    public ResponseEntity<Void> confirmarReserva(
        @PathVariable("id") UUID id,
        @RequestHeader(value = HEADER_SIMULAR_RESULTADO, required = false) String headerSimularResultado
    ) {
        servico.confirmar(id, headerSimularResultado);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelarReserva(
        @PathVariable("id") UUID id,
        @RequestHeader(value = HEADER_SIMULAR_RESULTADO, required = false) String headerSimularResultado
    ) {
        servico.remover(id, headerSimularResultado);
        return ResponseEntity.ok().build();
    }

}
