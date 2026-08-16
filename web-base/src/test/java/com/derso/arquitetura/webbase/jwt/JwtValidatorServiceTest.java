package com.derso.arquitetura.webbase.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyPair;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class JwtValidatorServiceTest {

    @Test
    void tokenEmitidoComAChaveEIssuerCorrespondentesEhAceito() {
        KeyPair par = ChavesRsaDeTeste.gerarPar();
        JwtIssuerService issuer = new JwtIssuerService(ChavesRsaDeTeste.base64PrivadaDe(par), "k1", "clientes");
        JwtValidatorService validator = new JwtValidatorService(configConfiavel("k1", "clientes", par));

        String token = issuer.generateToken("123", "a@b.com", "CLIENTE");

        assertTrue(validator.validateToken(token).isPresent());
    }

    @Test
    void tokenComKidDesconhecidoEhRejeitado() {
        KeyPair par = ChavesRsaDeTeste.gerarPar();
        JwtIssuerService issuer = new JwtIssuerService(ChavesRsaDeTeste.base64PrivadaDe(par), "k1", "clientes");
        // validador só confia em "k2" — o token vem assinado com "k1"
        JwtValidatorService validator = new JwtValidatorService(configConfiavel("k2", "clientes", par));

        String token = issuer.generateToken("123", "a@b.com", "CLIENTE");

        assertTrue(validator.validateToken(token).isEmpty());
    }

    @Test
    void tokenAssinadoComOutroParDeChavesMesmoKidEhRejeitado() {
        KeyPair parDoEmissor = ChavesRsaDeTeste.gerarPar();
        KeyPair parConfiadoPeloValidador = ChavesRsaDeTeste.gerarPar();
        JwtIssuerService issuer = new JwtIssuerService(ChavesRsaDeTeste.base64PrivadaDe(parDoEmissor), "k1", "clientes");
        // mesmo kid "k1", mas a chave pública configurada não é o par da chave privada usada pra assinar
        JwtValidatorService validator = new JwtValidatorService(configConfiavel("k1", "clientes", parConfiadoPeloValidador));

        String token = issuer.generateToken("123", "a@b.com", "CLIENTE");

        assertTrue(validator.validateToken(token).isEmpty());
    }

    @Test
    void tokenComIssuerDiferenteDoEsperadoParaOKidEhRejeitado() {
        KeyPair par = ChavesRsaDeTeste.gerarPar();
        // token é assinado de fato pela chave certa (par), então a assinatura por si só é válida —
        // é exatamente esse caso que a checagem de `iss` por kid precisa pegar
        JwtIssuerService issuer = new JwtIssuerService(ChavesRsaDeTeste.base64PrivadaDe(par), "k1", "clientes");
        JwtValidatorService validator = new JwtValidatorService(configConfiavel("k1", "outro-emissor", par));

        String token = issuer.generateToken("123", "a@b.com", "CLIENTE");

        assertTrue(validator.validateToken(token).isEmpty());
    }

    @Test
    void tokenSemKidNoHeaderEhRejeitado() {
        KeyPair par = ChavesRsaDeTeste.gerarPar();
        JwtValidatorService validator = new JwtValidatorService(configConfiavel("k1", "clientes", par));

        String tokenSemKid = Jwts.builder()
            .claim("id", "123")
            .issuer("clientes")
            .issuedAt(Date.from(Instant.now()))
            .expiration(Date.from(Instant.now().plusSeconds(600)))
            .signWith(par.getPrivate(), Jwts.SIG.RS256)
            .compact();

        assertTrue(validator.validateToken(tokenSemKid).isEmpty());
    }

    @Test
    void tokenComAssinaturaAdulteradaEhRejeitado() {
        KeyPair par = ChavesRsaDeTeste.gerarPar();
        JwtIssuerService issuer = new JwtIssuerService(ChavesRsaDeTeste.base64PrivadaDe(par), "k1", "clientes");
        JwtValidatorService validator = new JwtValidatorService(configConfiavel("k1", "clientes", par));

        String token = issuer.generateToken("123", "a@b.com", "CLIENTE");
        String tokenAdulterado = adulteraUltimoCaractereDaAssinatura(token);

        assertTrue(validator.validateToken(tokenAdulterado).isEmpty());
    }

    @Test
    void tokenComAlgTrocadoParaHmacUsandoChavePublicaComoSegredoEhRejeitado() {
        KeyPair par = ChavesRsaDeTeste.gerarPar();
        JwtValidatorService validator = new JwtValidatorService(configConfiavel("k1", "clientes", par));

        // alg confusion: o token declara HS256 (simétrico) e é "assinado" usando os bytes da
        // CHAVE PÚBLICA (não é segredo, qualquer um tem) como se fosse o segredo HMAC. Só
        // funcionaria se o validador confiasse cegamente no `alg` que o próprio token declara.
        // JJWT rejeita porque a chave que o keyLocator resolve pro kid "k1" é uma PublicKey RSA,
        // que não serve como SecretKey pra verificação HMAC — incompatibilidade de tipo, não de
        // conteúdo.
        SecretKey chaveForjada = Keys.hmacShaKeyFor(par.getPublic().getEncoded());
        String tokenForjado = Jwts.builder()
            .header().keyId("k1").and()
            .claim("id", "123")
            .issuer("clientes")
            .issuedAt(Date.from(Instant.now()))
            .expiration(Date.from(Instant.now().plusSeconds(600)))
            .signWith(chaveForjada, Jwts.SIG.HS256)
            .compact();

        assertTrue(validator.validateToken(tokenForjado).isEmpty());
    }

    @Test
    void tokenSemAssinaturaAlgNoneEhRejeitado() {
        KeyPair par = ChavesRsaDeTeste.gerarPar();
        JwtValidatorService validator = new JwtValidatorService(configConfiavel("k1", "clientes", par));

        // alg:none — o próprio JWT declara "não tenho assinatura, confie assim mesmo". Sem
        // signWith(), o builder do JJWT produz exatamente esse token inseguro.
        String tokenSemAssinatura = Jwts.builder()
            .claim("id", "123")
            .issuer("clientes")
            .issuedAt(Date.from(Instant.now()))
            .expiration(Date.from(Instant.now().plusSeconds(600)))
            .compact();

        assertTrue(validator.validateToken(tokenSemAssinatura).isEmpty());
    }

    @Test
    void tokenExpiradoEhRejeitado() {
        KeyPair par = ChavesRsaDeTeste.gerarPar();
        JwtValidatorService validator = new JwtValidatorService(configConfiavel("k1", "clientes", par));

        Instant passado = Instant.now().minusSeconds(3600);
        String tokenExpirado = Jwts.builder()
            .header().keyId("k1").and()
            .claim("id", "123")
            .claim("email", "a@b.com")
            .issuer("clientes")
            .issuedAt(Date.from(passado.minusSeconds(60)))
            .expiration(Date.from(passado))
            .signWith(par.getPrivate(), Jwts.SIG.RS256)
            .compact();

        assertTrue(validator.validateToken(tokenExpirado).isEmpty());
    }

    @Test
    void claimsSaoLegiveisQuandoTokenEhValido() {
        KeyPair par = ChavesRsaDeTeste.gerarPar();
        JwtIssuerService issuer = new JwtIssuerService(ChavesRsaDeTeste.base64PrivadaDe(par), "k1", "clientes");
        JwtValidatorService validator = new JwtValidatorService(configConfiavel("k1", "clientes", par));

        String token = issuer.generateToken("123", "a@b.com", "CLIENTE");
        Claims claims = validator.validateToken(token).orElseThrow();

        assertEquals("123", claims.get("id"));
        assertEquals("a@b.com", claims.get("email"));
        assertEquals("clientes", claims.getIssuer());
    }

    private static TrustedJwtIssuersConfig configConfiavel(String kid, String issuer, KeyPair par) {
        TrustedJwtIssuersConfig.TrustedIssuer trusted = new TrustedJwtIssuersConfig.TrustedIssuer();
        trusted.setKid(kid);
        trusted.setIssuer(issuer);
        trusted.setPublicKey(ChavesRsaDeTeste.base64PublicaDe(par));

        TrustedJwtIssuersConfig config = new TrustedJwtIssuersConfig();
        config.setTrustedIssuers(List.of(trusted));
        return config;
    }

    private static final String ALFABETO_BASE64URL =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

    // o último caractere do base64url de uma assinatura RSA fica numa posição onde alguns bits
    // baixos são ignorados pelo decoder (sobra de 1 byte no agrupamento de 3-em-3) — trocar A<->B
    // ingenuamente às vezes cai só nesses bits ignorados e o token "adulterado" continua válido
    // por coincidência. XOR nos 2 bits mais significativos do sextet garante mudar o byte de
    // verdade, não só o caractere.
    private static String adulteraUltimoCaractereDaAssinatura(String token) {
        int ultimoPonto = token.lastIndexOf('.');
        String corpo = token.substring(0, ultimoPonto + 1);
        String assinatura = token.substring(ultimoPonto + 1);
        char ultimoChar = assinatura.charAt(assinatura.length() - 1);
        int indice = ALFABETO_BASE64URL.indexOf(ultimoChar);
        char trocado = ALFABETO_BASE64URL.charAt(indice ^ 0b110000);
        return corpo + assinatura.substring(0, assinatura.length() - 1) + trocado;
    }
}
