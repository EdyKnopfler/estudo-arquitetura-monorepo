-- Prazo de INICIADA com registro próprio: voltar pra INICIADA reinicia; start_time fica como base do teto —
-- ver docs/purchase-flow-design.md#dois-timeouts
alter table sessao_compra add column prazo_iniciada timestamp;

update sessao_compra set prazo_iniciada = start_time + interval '15 minutes';

create index idx_sessao_compra_prazo_iniciada on sessao_compra (prazo_iniciada) where status = 'INICIADA';
