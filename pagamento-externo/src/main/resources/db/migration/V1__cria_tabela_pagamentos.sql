-- Chave única por cliente, não global — como em gateways reais
create table pagamentos (
    id uuid primary key,
    id_cliente varchar(100) not null,
    chave_idempotencia uuid not null,
    metodo varchar(30) not null,
    valor numeric(12, 2) not null,
    criacao timestamp not null,
    status varchar(20) not null,
    constraint uk_pagamentos_cliente_chave unique (id_cliente, chave_idempotencia)
);
