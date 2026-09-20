package com.derso.arquitetura.pagamentointerno;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.derso.arquitetura.pagamentointerno.entity.Pagamento;

public interface PagamentoRepository extends JpaRepository<Pagamento, UUID> {

}
