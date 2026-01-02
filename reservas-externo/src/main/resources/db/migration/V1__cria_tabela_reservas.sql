create table reservas (
    id uuid primary key,
    id_cliente uuid not null,
    criacao timestamp not null,
    confirmado boolean not null
);
