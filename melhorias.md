# Análise de Arquitetura e Sugestões de Melhoria

Este documento registra a análise realizada sobre a estrutura do monorepo e as recomendações para evolução do sistema.

## Pergunta Original
> "gostaria de uma análise deste monorepo composto por múltiplos projetos maven. basicamente, módulos que são usados por vários serviços (em geral relativos a entidades e regras de negócio) são encapsulados em módulos do tipo biblioteca ("common"), que são referenciados com os módulos que se comunicam com o mundo externo (filas, APIs web, etc.)
> a distribuição de atribuições está boa? alguma prática melhor que pode ser seguida?"

---

## Análise da Distribuição de Atribuições

### Pontos Fortes
*   **Separação por Domínio:** Divisão clara entre clientes, pagamentos, reservas e sessões.
*   **Encapsulamento de Infraestrutura:** Distinção entre lógica/entidades (`common`) e pontos de entrada (`web/sagas`), permitindo escalabilidade independente.
*   **Resiliência e Simulação:** Uso de `CHANCE_FALHA` para testar a robustez das Sagas.
*   **Tecnologia Moderna:** Uso de Java 25 e **Virtual Threads**, otimizando a concorrência.

### Pontos de Atenção
*   **Risco do "Shared Kernel":** O compartilhamento de entidades JPA entre serviços pode gerar acoplamento excessivo, onde mudanças no banco exigem deploys simultâneos de múltiplos módulos.
*   **Máquina de Estados:** Em arquiteturas de Saga, o estado deve ser alterado de forma incremental e controlada, evitando "updates gerais" de entidades.

---

## Sugestões de Melhores Práticas

### 1. Refinamento dos Módulos "Common" (Arquitetura Hexagonal)
Dividir o que hoje é "Common" em:
*   **Domain/Core:** Apenas POJOs e interfaces de negócio, sem dependências de frameworks (Spring/JPA).
*   **Infrastructure/Adapters:** Implementações JPA e clientes HTTP.
Isso evita que anotações de persistência poluam a regra de negócio.

### 2. Contratos de API (DTOs)
Em vez de compartilhar a **Entidade JPA**, criar módulos de `api-contracts` com DTOs. Isso protege a estrutura interna do banco de dados e define um contrato claro entre os serviços.

### 3. Engenharia de Caos (Chaos Engineering)
Melhorar a simulação de falhas adicionando latência variável antes de lançar exceções. **(Já implementado em `ReservasService.java`)**.

```java
private void seraQueVaiFalhar() {
    if (random.nextDouble() < CHANCE_FALHA) {
        try {
            Thread.sleep(random.nextLong(100, 500)); // Simula latência real
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        throw new FalhaAleatoriaException();
    }
}
```

### 4. Idempotência
Garantir que as entidades possam lidar com reprocessamento de mensagens. **(Já implementado em `Reserva.java`)**: O ID da entidade pode ser fornecido pelo orquestrador da Saga para evitar duplicidade em retentativas.

```java
public Reserva(UUID id, UUID idExterno) {
    this.id = id != null ? id : UUID.randomUUID();
    this.idExterno = idExterno;
}
```

---

## Conclusão
A arquitetura está bem encaminhada para um estudo de Sagas e Monorepo. O próximo passo recomendado é o isolamento da lógica de **compensação** (rollback) nos módulos `-sagas`.