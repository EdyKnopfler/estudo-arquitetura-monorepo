-- Intenções do webhook pago, executadas por quem ocupa a linha — ver docs/purchase-flow-design.md#dual-write-no-webhook
alter table pagamentos
    add column sessao_pendente boolean not null default false,
    add column fila_pendente boolean not null default false,
    add column intencoes_ocupadas_em timestamp;

create index idx_pagamentos_intencoes_pendentes on pagamentos (intencoes_ocupadas_em)
    where sessao_pendente or fila_pendente;
