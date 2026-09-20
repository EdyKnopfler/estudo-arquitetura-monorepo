alter table pagamentos
    add column id_sessao_compra uuid not null,
    add column id_reserva_hotel uuid not null,
    add column id_reserva_voo_ida uuid not null,
    add column id_reserva_voo_volta uuid not null;
