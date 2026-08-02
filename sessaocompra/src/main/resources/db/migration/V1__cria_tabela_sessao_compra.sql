create table sessao_compra (
    id uuid primary key,
    status varchar(32) not null,
    id_customer uuid,
    id_reserva_voo_ida uuid,
    id_reserva_hotel uuid,
    id_reserva_voo_volta uuid
);
