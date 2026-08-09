package com.derso.arquitetura.sessaocompra;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("web")
@TestPropertySource(properties = ChaveJwtTeste.JWT_PUBLIC_KEY_PROPERTY)
@Import(TestcontainersConfig.class)
class SessaoCompraApplicationTests {

	@Test
	void contextLoads() {
	}

}
