package com.derso.arquitetura.webbase.teste;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;

// Serviço do monorepo como container pra teste integrado: jar já compilado no host + imagem só com o JRE.
// Não usa o Dockerfile do módulo: ele builda com Maven dentro do Docker e `RUN --mount` exige BuildKit,
// que o cliente Docker do Testcontainers não suporta. Estratégia de testes: docs/testing-strategy.md
public final class ServicoEmContainer {

    private ServicoEmContainer() {
    }

    public static GenericContainer<?> de(String modulo) {
        Path jar = jarDoModulo(modulo);
        return new GenericContainer<>(
            new ImageFromDockerfile()
                .withFileFromPath("app.jar", jar)
                .withDockerfileFromBuilder(imagem -> imagem
                    .from("eclipse-temurin:25-jre-alpine")
                    .copy("app.jar", "/app.jar")
                    .entryPoint("java", "-jar", "/app.jar")
                    .build())
        ).withEnv("JAVA_TOOL_OPTIONS", "-Xmx256m -XX:+UseSerialGC");
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
