package mx.unam.anuies.graficas;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "app.storage.path=${java.io.tmpdir}/graficas-context-datos")
class GraficasApplicationTests {

	@Test
	void contextLoads() {
	}

}
