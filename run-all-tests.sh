#!/usr/bin/env bash
# Suíte inteira em duas fases, um Maven por módulo, liberando a RAM entre elas — porquê em docs/testing-strategy.md#rodar
set -euo pipefail
cd "$(dirname "$0")"

modulos=$(sed -n 's:.*<module>\(.*\)</module>.*:\1:p' pom.xml)

# "down" dos containers de teste: tudo que o Testcontainers criou leva esse label
derrubar_containers() {
    docker ps -aq --filter label=org.testcontainers | xargs -r docker rm -f
}

# libs pros -pl abaixo e jars que os integrados sobem
./mvnw install -DskipTests

# testes de microsserviço: o Testcontainers sobe db e broker sozinho ("up")
for m in $modulos; do
    ./mvnw test -pl "$m"
done
derrubar_containers

for m in $modulos; do
    ./mvnw test -Pintegrado -pl "$m"
done
derrubar_containers

echo "Todos os testes passaram."
