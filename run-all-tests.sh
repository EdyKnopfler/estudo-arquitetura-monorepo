#!/usr/bin/env bash
# Suíte em fases, um Maven por módulo, liberando a RAM entre elas — porquê em docs/testing-strategy.md#rodar
# Uso: ./run-all-tests.sh [integrados: sim|nao, padrão sim] [sistema: sim|nao, padrão nao]
set -euo pipefail
cd "$(dirname "$0")"

integrados=${1:-sim}
sistema=${2:-nao}
for valor in "$integrados" "$sistema"; do
    [[ "$valor" == sim || "$valor" == nao ]] || { echo "parâmetro inválido: '$valor' (use sim ou nao)" >&2; exit 1; }
done

# credenciais de dev: o .env é a fonte, para o compose e para os testes
[ -f .env ] || { echo ".env não encontrado — copie de .env.example e preencha" >&2; exit 1; }
set -a; source .env; set +a

modulos=$(sed -n 's:.*<module>\(.*\)</module>.*:\1:p' pom.xml)

# "down" dos containers de teste: tudo que o Testcontainers criou leva esse label
derrubar_containers() {
    docker ps -aq --filter label=org.testcontainers | xargs -r docker rm -f
}

# uma fase: o mesmo Maven em cada módulo, depois derruba o que o Testcontainers subiu
fase() {
    for m in $modulos; do
        ./mvnw test "$@" -pl "$m"
    done
    derrubar_containers
}

# libs pros -pl abaixo e jars que os integrados/sistema sobem
./mvnw install -DskipTests

# testes de microsserviço: o Testcontainers sobe db e broker sozinho ("up")
fase
if [ "$integrados" == sim ]; then
    fase -Pintegrado
fi
if [ "$sistema" == sim ]; then
    fase -Psistema
fi

echo "Todos os testes passaram (integrados: $integrados, sistema: $sistema)."
