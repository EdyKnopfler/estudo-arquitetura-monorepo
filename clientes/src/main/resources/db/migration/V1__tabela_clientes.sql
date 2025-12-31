create table clientes (
    id uuid primary key,
    nome varchar(120) not null,
    email varchar(150) not null unique,
    cpf char(11) not null unique
);