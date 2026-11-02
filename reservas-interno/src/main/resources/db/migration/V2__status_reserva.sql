-- Status da reserva; falha/cancelada/intenção ainda sem nome — ver docs/todo.md
alter table reservas
    add column status varchar(30) not null default 'PRE_RESERVADA';

alter table reservas
    alter column status drop default;
