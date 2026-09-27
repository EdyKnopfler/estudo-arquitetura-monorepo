-- Uma linha por sessão, gravada antes de chamar o gateway — ver docs/purchase-flow-design.md#criação-do-pagamento
alter table pagamentos
    alter column id_externo drop not null,
    add column status varchar(30) not null default 'AGUARDANDO_PAGAMENTO',
    add column chave_idempotencia uuid not null default gen_random_uuid(),
    add column url_pagamento varchar(500),
    add constraint uk_pagamentos_sessao_compra unique (id_sessao_compra);

alter table pagamentos
    alter column status drop default,
    alter column chave_idempotencia drop default;
