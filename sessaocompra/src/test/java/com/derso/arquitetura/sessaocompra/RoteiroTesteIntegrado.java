package com.derso.arquitetura.sessaocompra;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

// Narrativa de teste integrado longo: o próprio teste conta o que faz e o que confere.
// Terminal com cor; arquivo em target/ sem cor, pra ler depois sem o log do resto da build.
final class RoteiroTesteIntegrado implements AutoCloseable {

    private static final String RESET = "\u001B[0m";
    private static final String NEGRITO = "\u001B[1m";
    private static final String VERDE = "\u001B[32m";
    private static final String VERMELHO = "\u001B[31m";
    private static final String CINZA = "\u001B[90m";

    private final PrintWriter arquivo;
    private final Instant inicio = Instant.now();

    RoteiroTesteIntegrado(Path caminho) {
        try {
            Files.createDirectories(caminho.getParent());
            arquivo = new PrintWriter(Files.newBufferedWriter(caminho), true);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    void etapa(String titulo) {
        escrever("", "");
        escrever(NEGRITO, "== " + titulo + " ==");
    }

    void fazendo(String acao) {
        escrever("", "  → " + acao);
    }

    void ok(String fato) {
        escrever(VERDE, "  ✔ " + fato);
    }

    // bloco de várias linhas (ex.: JSON de mensagem), recuado
    void dado(String texto) {
        texto.lines().forEach(linha -> escrever(CINZA, "      " + linha));
    }

    void falha(Throwable erro) {
        escrever(VERMELHO, "  ✘ " + erro);
    }

    @Override
    public void close() {
        arquivo.close();
    }

    private void escrever(String cor, String texto) {
        String linha = texto.isEmpty() ? "" : "[" + decorrido() + "] " + texto;
        System.out.println(cor.isEmpty() ? linha : cor + linha + RESET);
        arquivo.println(linha);
    }

    private String decorrido() {
        Duration tempo = Duration.between(inicio, Instant.now());
        return String.format("%02d:%02d", tempo.toMinutes(), tempo.toSecondsPart());
    }

}
