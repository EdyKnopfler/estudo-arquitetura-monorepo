alter table clientes
add column senha varchar(255);

update clientes
set senha = '$2a$10$N9qo8uLOickgx2ZMRZo5i.uC5QF2Q4h6kF1Cz1P3pQnF9l9z2eZ1O';

alter table clientes
alter column senha set not null;