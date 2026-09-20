create table pagamentos (
    id uuid primary key,
    id_externo uuid not null

    -- TODO status do pagamento

    -- TODO colunas de correlação pro payload da mensagem da SAGA (id_sessao_compra, id_reserva_hotel,
    -- id_reserva_voo_ida, id_reserva_voo_volta — só ids internos, idExterno não sai de reservas-interno)
    -- populadas por uma chamada nova de sessaocompra->pagamento-interno em iniciarPagamento, que hoje não existe.
    -- Ver docs/purchase-flow-design.md#payload-da-mensagem-da-saga.

);
