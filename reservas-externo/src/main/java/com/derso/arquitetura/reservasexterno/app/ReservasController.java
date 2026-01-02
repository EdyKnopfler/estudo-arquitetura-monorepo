package com.derso.arquitetura.reservasexterno.app;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
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

}
