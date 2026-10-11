-- Vencimento da transação vigente, informado pelo gateway; nulo enquanto CRIANDO
alter table pagamentos
    add column vencimento timestamp;
