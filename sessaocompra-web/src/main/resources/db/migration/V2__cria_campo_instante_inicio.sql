alter table sessao_compra add column start_time timestamp;

create index idx_sessao_compra_start_time on sessao_compra (start_time);