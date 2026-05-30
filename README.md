# Estudo Arquitetura (Monorepo)

Testando arquitetura para sistema (simplificado) de Agência de Viagens, simulando pagamento, reserva de hoteis e voos em serviços separados.

---

### 📌 **Análise de Arquitetura**
Confira a análise detalhada e sugestões de melhorias para este projeto em: [melhorias.md](./melhorias.md).

Até não tomei muito puxão de orelha do Gemini... Já tenho atividade para as férias :)

---

## **TODO** 

* Encaixar e conectar todos os serviços (isso **demora**!)
  * Um pouquinho a cada final de semana e chegamos lá!
* Preencher as regras de negócio
  * _Vai ficar divertido fazendo isso com testes integrados_
* **(FEITO)** Contêineres Docker para os serviços
* Desenhar alguns diagramas para ilustrar como funcionam os serviços e a coreografia SAGAS
* Documentar inicialização e amostras de uso (quando estiver funcional)

## Módulos

* **clientes:** cadastra e autentica clientes
* **pagamento-externo**: simula serviço externo _instável_ de pagamento, com falhas, aceites e recusas; não precisa implementar lógica completa
* **pagamento-interno-common**: centraliza a lógica de negócio dos pagamentos
* **pagamento-interno-web**: interface web para a realização de pagamentos na aplicação; webhook para o serviço externo comunicar confirmação ou recusa de pagamentos
* **pagamento-interno-sagas**: a confirmação de um pagamento envia mensagens para os sistemas de reservas realizarem a confirmação; ao menos uma instância deste serviço fica observando a fila para estornar pagamentos em caso de erro nas reservas
* **reservas-externo:** simula serviços externos _instáveis_ para teste da arquitetura; não precisa implementar lógica completa
* **reservas-interno-common**: centraliza a lógica de negócio das reservas de voo e hotel
* **reservas-interno-web**: interface web para a realização de reservas na aplicação; deve haver ao menos uma instância para voos e uma para hotel
* **reservas-interno-sagas**: responde aos eventos de pagamento e erros em outros serviços para confirmação e cancelamento das reservas; deve haver ao menos uma instância para voos e uma para hotel
* **sessaocompra-common:** centraliza a lógica de negócio das sessões de compra. Implementa bloqueios de consistência.
* **sessaocompra-timeout:** invalida sessões de compra não confirmadas, expiradas
* **sessaocompra-web:** interface web para a sessão de compra, onde o cliente informa as pré-reservas e o sistema de pagamentos notifica para confirmação.
* **web-base:** módulos reusados nos serviços web: tratamento de erros, tokens JWT, client id/client secret

### A fazer

* [ ] **Timeout:** dispara sequência SAGAS de cancelamento

* [ ] **Pagamentos**:
  * [ ] **web:** aciona o serviço externo, webhook de confirmação e erro
    * [ ] chama endpoints do serviço externo
    * [ ] bate no serviço de sessões para modificar o estado (árbitro)
    * [ ] dispara sequência SAGAS de confirmação a partir do webhook
  * [ ] **sagas:** eventos de confirmação e cancelamento
    * [ ] recebe do anterior e passa para o próximo (filas de "entrada" e saída)
  * [X] **externo:** simula serviço externo, **introduz erros aleatórios**
    * [X] endpoints de pagamento e estorno, _devem falhar às vezes de propósito_
  * [ ] Testes integrados
    * [ ] Requisição > Externo > Webhook
    * [ ] Encaminha sucesso para outro serviço
    * [ ] Notificação de falha por outro serviço

* [ ] **Hotel:**
  * [X] **web:** interação com usuário (pré-reservas)
    * [ ] bate no serviço de sessões para modificar o estado (árbitro)
    * [ ] chama endpoints do serviço externo
  * [ ] **sagas:** eventos de confirmação e cancelamento
    * [X] recebe do anterior e passa para o próximo (filas de "entrada" e saída)
    * [ ] bate no serviço de sessões para modificar o estado (árbitro)
    * [ ] chama endpoints do serviço externo
  * [X] **externo:** simula serviço externo, **introduz erros aleatórios**
    * [X] **pré-reserva:** cria reserva sem confirmação
    * [X] **confirmação:** confirma pré-reservas feitas _há menos de 15 minutos_
    * [X] **cancelamento:** cancela pré-reservas
    * [X] _deve falhar às vezes de propósito_
  * [ ] Testes integrados
    * [ ] Requisição > Externo
    * [ ] Encaminha sucesso para outro serviço
    * [ ] Notificação de falha por outro serviço

* [ ] **Voo:** para voos ida e volta, mesma estrutura de _Hotel_
  * [X] **web:** interação com usuário (pré-reservas)
    * [ ] bate no serviço de sessões para modificar o estado (árbitro)
    * [ ] chama endpoints do serviço externo
  * [ ] **sagas:** eventos de confirmação e cancelamento por timeout
    * [X] recebe do anterior e passa para o próximo (filas de "entrada" e saída)
    * [ ] bate no serviço de sessões para modificar o estado (árbitro)
    * [ ] chama endpoints do serviço externo
  * [X] **externo:** simula serviço externo, **introduz erros aleatórios**
    * [X] **pré-reserva:** cria reserva sem confirmação
    * [X] **confirmação:** confirma pré-reservas feitas _há menos de 15 minutos_
    * [X] **cancelamento:** cancela pré-reservas
    * [X] _deve falhar às vezes de propósito_
  * [ ] Testes integrados
    * [ ] Requisição > Externo
    * [ ] Encaminha sucesso para outro serviço
    * [ ] Notificação de falha por outro serviço

#### Tarefas repetidas

* [X] Serviços externos Voo/Hotel
  * [X] Pequeno banco de dados com id da reserva, id do cliente e status é suficiente
  * [X] **Simulação de falha dos endpoints externos:** um bom e velho `Math.random()` resolve
  * [X] 2 instâncias do mesmo projeto?
* [X] Serviços internos Voo/Hotel
  * [X] Chamadas aos serviços externos correspondentes
  * [X] **SAGAS:** conexão com uma fila de entrada e uma de saída (já tenho amostras com RabbitMQ em Python e Go)
  * [ ] Lógica de negócio (o "recheio")