# Estudo Arquitetura (Monorepo)

Testando arquitetura para sistema (simplificado) de Agência de Viagens, simulando pagamento, reserva de hoteis e voos em serviços separados.

* **TODO** contêineres Docker, documentar inicialização e amostras de uso (quando estiver funcional)

## Módulos

* **clientes:** cadastra e autentica clientes
* **reservas-externo:** simula serviços externos _instáveis_ para teste da arquitetura; não precisam implementar lógica completa
* **sessaocompra-common:** centraliza a lógica de negócio das sessões de compra. Implementa bloqueios de consistência.
* **sessaocompra-timeout:** invalida sessões de compra não confirmadas, expiradas
* **sessaocompra-web:** interface web para a sessão de compra, onde o cliente informa as pré-reservas e o sistema de pagamentos notifica para confirmação.
* **web-base:** módulos reusados nos serviços web: tratamento de erros, tokens JWT

### A fazer

* [ ] **Timeout:** dispara sequência SAGAS de cancelamento

* [ ] **Pagamentos**:
  * [ ] **web:** aciona o serviço externo, webhook de confirmação e erro
    * [ ] chama endpoints do serviço externo
    * [ ] bate no serviço de sessões para modificar o estado (árbitro)
    * [ ] **sagas:** eventos de confirmação e cancelamento
      * [ ] recebe do anterior e passa para o próximo (filas de "entrada" e saída)
    * [ ] dispara sequência SAGAS de confirmação a partir do webhook
  * [ ] **externo:** simula serviço externo, **introduz erros aleatórios**
    * [ ] endpoints de pagamento e estorno, _devem falhar às vezes de propósito_

* [ ] **Hotel:**
  * [X] **web:** interação com usuário (pré-reservas)
    * [ ] bate no serviço de sessões para modificar o estado (árbitro)
    * [ ] chama endpoints do serviço externo
  * [ ] **sagas:** eventos de confirmação e cancelamento
    * [ ] recebe do anterior e passa para o próximo (filas de "entrada" e saída)
  * [X] **externo:** simula serviço externo, **introduz erros aleatórios**
    * [X] **pré-reserva:** cria reserva sem confirmação
    * [X] **confirmação:** confirma pré-reservas feitas _há menos de 15 minutos_
    * [X] **cancelamento:** cancela pré-reservas
    * [X] _deve falhar às vezes de propósito_

* [ ] **Voo:** para voos ida e volta, mesma estrutura de _Hotel_
  * [X] **web:** interação com usuário (pré-reservas)
    * [ ] bate no serviço de sessões para modificar o estado (árbitro)
    * [ ] chama endpoints do serviço externo
  * [ ] **sagas:** eventos de confirmação e cancelamento por timeout
    * [ ] recebe do anterior e passa para o próximo (filas de "entrada" e saída)
    * [ ] chama endpoints do serviço externo
  * [X] **externo:** simula serviço externo, **introduz erros aleatórios**
    * [X] **pré-reserva:** cria reserva sem confirmação
    * [X] **confirmação:** confirma pré-reservas feitas _há menos de 15 minutos_
    * [X] **cancelamento:** cancela pré-reservas
    * [X] _deve falhar às vezes de propósito_

#### Tarefas repetidas

* [X] Serviços externos Voo/Hotel
  * [X] Pequeno banco de dados com id da reserva, id do cliente e status é suficiente
  * [X] **Simulação de falha dos endpoints externos:** um bom e velho `Math.random()` resolve
  * [X] 2 instâncias do mesmo projeto?
* [ ] Serviços internos Voo/Hotel
  * [ ] Chamadas aos serviços externos correspondentes
  * [ ] **SAGAS:** conexão com uma fila de entrada e uma de saída (já tenho amostras com RabbitMQ em Python e Go)
