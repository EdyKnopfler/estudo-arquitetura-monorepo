package com.derso.arquitetura.webbase.teste;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;

// Serviço do monorepo como container pra teste integrado: jar já compilado no host + imagem só com o JRE.
// Não usa o Dockerfile do módulo: ele builda com Maven dentro do Docker e `RUN --mount` exige BuildKit,
// que o cliente Docker do Testcontainers não suporta. Estratégia de testes: docs/testing-strategy.md
public final class ServicoEmContainer {

    // um build por módulo na execução: os containers do mesmo módulo saem da mesma imagem
    private static final Map<String, ImageFromDockerfile> IMAGENS = new ConcurrentHashMap<>();

    private ServicoEmContainer() {
    }

    public static GenericContainer<?> de(String modulo) {
        return new GenericContainer<>(IMAGENS.computeIfAbsent(modulo, ServicoEmContainer::imagem))
            // teste curto: só o C1 (sobe mais rápido, JIT gasta menos), cache de código e pilhas menores
            .withEnv("JAVA_TOOL_OPTIONS", "-Xmx256m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=32m -Xss256k");
    }

    // Nome fixo e sem apagar no fim: cada execução sobrescreve a mesma tag. Com nome aleatório, builds
    // iguais viravam a mesma imagem com várias tags, e a limpeza do fim (por id) falhava — acumulava
    private static ImageFromDockerfile imagem(String modulo) {
        return new ImageFromDockerfile("arquitetura-teste/" + modulo, false)
            .withFileFromPath("app.jar", jarDoModulo(modulo))
            .withDockerfileFromBuilder(imagem -> imagem
                .from("eclipse-temurin:25-jre-alpine")
                .copy("app.jar", "/app.jar")
                .entryPoint("java", "-jar", "/app.jar")
                .build());
    }

    private static Path jarDoModulo(String modulo) {
        // teste roda com o diretório do próprio módulo como cwd
        Path target = Paths.get("").toAbsolutePath().getParent().resolve(modulo).resolve("target");
        try (Stream<Path> arquivos = Files.list(target)) {
            return arquivos
                .filter(p -> p.toString().endsWith(".jar") && !p.toString().endsWith("-tests.jar"))
                .findFirst()
                .orElseThrow(() -> semJar(modulo));
        } catch (IOException e) {
            throw semJar(modulo);
        } catch (UncheckedIOException e) {
            throw semJar(modulo);
        }
    }

    private static IllegalStateException semJar(String modulo) {
        return new IllegalStateException("jar de " + modulo + " não encontrado — rode `./mvnw package -DskipTests` antes de -Pintegrado");
    }

}
