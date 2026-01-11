package com.derso.arquitetura.reservasinterno.app;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.derso.arquitetura.reservasinterno.ReservasService;
import com.derso.arquitetura.reservasinterno.dto.CriarReservaRequest;
import com.derso.arquitetura.reservasinterno.dto.ReservaDTO;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/reservas")
@RequiredArgsConstructor
public class ReservasInternoController {

    private final ReservasService servico;
    
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservaDTO criar(@RequestBody @Valid CriarReservaRequest dados) {
        return servico.criarReserva(dados.idCliente());
    }

}
