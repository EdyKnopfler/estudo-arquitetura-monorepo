-- Início da chamada em voo ao gateway; nulo sem chamada em andamento — ver docs/purchase-flow-design.md#pagamento-da-sessão-tentativa-trocável
alter table pagamentos
    add column tentativa_em timestamp;
