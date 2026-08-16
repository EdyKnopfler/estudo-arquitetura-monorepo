package com.derso.arquitetura.sessaocompra;

// Mesma chave pública já usada em .env pra dev local (descartável). Nenhum token real é
// assinado/validado nos testes deste módulo — só precisa ser uma chave RSA sintaticamente
// válida pra TrustedJwtIssuersConfig/JwtValidatorService construir sem falhar.
final class ChaveJwtTeste {

    // @TestPropertySource(properties = ...) exige inicializador de array literal na própria
    // anotação — não aceita referenciar um array separado (é erro de compilação silencioso,
    // não dá erro no @TestPropertySource em si). Por isso 3 constantes soltas, montadas num
    // array `{...}` direto no ponto de uso.
    static final String JWT_KID_PROPERTY = "jwt.trusted-issuers[0].kid=clientes-teste";
    static final String JWT_ISSUER_PROPERTY = "jwt.trusted-issuers[0].issuer=clientes";
    static final String JWT_PUBLIC_KEY_PROPERTY =
        "jwt.trusted-issuers[0].public-key=MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAzVeS3Zm2+ZaYGmJBDP1COLyJo64EFY7mE6RRyFZbyJbqB1fbx5d4ut6omCdNJm2/QjsEFoUWasjvuC8X5GVDTwgB5Gu9ib1N6EsVONJ+LLyJ+F8O2ETxZXb97+gZdwbYI0wYqvEASK0MwroRxK01uhLiliRjBZmgu3F44qir1L2Gv+vM+3MWx2/XFpo0lw36nq+JNX3iXJ3D7eRukUIJ8YJnBLuZD3/Or1MvzkTjmjl2G/0Vltuc6lBfawEN+RcH/QIDJQD2N9ywsMhBHpjChUR8OFmYZx+GmGdQnbggrPY/lfpTB2TmWPQT8OijDCfm+9rQuVHLcFvDCeuxhSFpnQIDAQAB";

    private ChaveJwtTeste() {
    }

}
