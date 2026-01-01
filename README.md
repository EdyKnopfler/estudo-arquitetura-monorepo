# Estudo Arquitetura (Monorepo)

Testando arquitetura para sistema (simplificado) de Agência de Viagens, simulando pagamento, reserva de hoteis e voos em serviços separados.

* **TODO** contêineres Docker, documentar inicialização e amostras de uso (quando estiver funcional)

## Módulos

* **clientes:** cadastra e autentica clientes
* **sessaocompra-common:** centraliza a lógica de negócio das sessões de compra. Implementa bloqueios de consistência.
* **sessaocompra-timeout:** invalida sessões de compra não confirmadas, expiradas
* **sessaocompra-web:** interface web para a sessão de compra, onde o cliente informa as pré-reservas e o sistema de pagamentos notifica para confirmação.
* **web-base:** módulos reusados nos serviços web: tratamento de erros, tokens JWT

### A fazer

* [ ] **Pagamentos**:
  * [ ] **web:** aciona o serviço externo, webhook de confirmação e erro
  * [ ] **externo:** simula serviço externo, **introduz erros aleatórios**
* [ ] **Hotel:**
  * [ ] **web:** interação com usuário (pré-reservas)
  * [ ] **sagas:** eventos de confirmação e cancelamento por timeout
  * [ ] **externo:** simula serviço externo, **introduz erros aleatórios**
* [ ] **Voo:** para voos ida e volta, mesma estrutura de _Hotel_
  * [ ] **web:** interação com usuário (pré-reservas)
  * [ ] **sagas:** eventos de confirmação e cancelamento por timeout
  * [ ] **externo:** simula serviço externo, **introduz erros aleatórios**
