package com.derso.arquitetura.sessaocompra;

// Mesma chave pública já usada em .env pra dev local (descartável). Nenhum token real é
// assinado/validado nos testes deste módulo — só precisa ser uma chave RSA sintaticamente
// válida pra JwtValidatorService construir sem falhar.
final class ChaveJwtTeste {

    static final String JWT_PUBLIC_KEY_PROPERTY =
        "jwt.public-key=MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAzVeS3Zm2+ZaYGmJBDP1COLyJo64EFY7mE6RRyFZbyJbqB1fbx5d4ut6omCdNJm2/QjsEFoUWasjvuC8X5GVDTwgB5Gu9ib1N6EsVONJ+LLyJ+F8O2ETxZXb97+gZdwbYI0wYqvEASK0MwroRxK01uhLiliRjBZmgu3F44qir1L2Gv+vM+3MWx2/XFpo0lw36nq+JNX3iXJ3D7eRukUIJ8YJnBLuZD3/Or1MvzkTjmjl2G/0Vltuc6lBfawEN+RcH/QIDJQD2N9ywsMhBHpjChUR8OFmYZx+GmGdQnbggrPY/lfpTB2TmWPQT8OijDCfm+9rQuVHLcFvDCeuxhSFpnQIDAQAB";

    private ChaveJwtTeste() {
    }

}
