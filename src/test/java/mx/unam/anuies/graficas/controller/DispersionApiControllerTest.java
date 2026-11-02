package mx.unam.anuies.graficas.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@TestPropertySource(properties = {
        "app.storage.path=${java.io.tmpdir}/graficas-disp-test-datos",
        "app.storage.dispersion-path=${java.io.tmpdir}/graficas-disp-test-datos/dispersion",
        "app.security.allowed-emails=juan.perez@unam.mx"
})
class DispersionApiControllerTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void subeYListaArchivoEnAlmacenDeDispersionSinMezclarConCajas() throws Exception {
        MockMultipartFile archivo = new MockMultipartFile("file", "puntos.csv", "text/csv",
                "Titulo\nX,Y\n1,2\n".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/dispersion/upload").file(archivo).with(oauth2Login()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("puntos.csv"));

        mockMvc.perform(get("/api/dispersion").with(oauth2Login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasItem("puntos.csv")));

        mockMvc.perform(get("/api/archivos").with(oauth2Login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(hasItem("puntos.csv"))));
    }

    @Test
    void conservaAcentosDelArchivoRealEnApiPaginaYScript() throws Exception {
        byte[] contenido = Files.readAllBytes(Path.of("datos", "dispersion", "Alumnos_proyectos.csv"));
        MockMultipartFile archivo = new MockMultipartFile("file", "acentos.csv", "text/csv", contenido);
        mockMvc.perform(multipart("/api/dispersion/upload").file(archivo).with(oauth2Login()))
                .andExpect(status().isCreated());

        String json = mockMvc.perform(get("/api/dispersion/acentos.csv").with(oauth2Login()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(json)
                .contains("\"etiquetaNombre\":\"Institución\"")
                .contains("\"etiquetaGrupo\":\"Rango alumnos\"")
                .contains("Benemérita Universidad Autónoma de Puebla")
                .contains("50000 o más")
                .contains("Universidad Nacional Autónoma de México")
                .contains("UPN Unidad N° 111 Guanajuato")
                .doesNotContain("\uFEFF")
                .doesNotContain("Ã");

        String html = mockMvc.perform(get("/dispersion").with(oauth2Login()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html)
                .contains("Tablero de diagramas de dispersión")
                .contains("ANUIES · Estadística")
                .contains("logarítmica")
                .contains("máximo 10 MB")
                .doesNotContain("Ã")
                .doesNotContain("\uFFFD");

        String index = mockMvc.perform(get("/").with(oauth2Login()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(index)
                .contains("Tablero de gráficas box plot - Diagramas de caja")
                .contains("Región")
                .contains("Mínimo")
                .doesNotContain("Ã")
                .doesNotContain("\uFFFD");

        String script = mockMvc.perform(get("/js/dispersion.js"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(script).contains("ρ de Spearman").contains("logarítmica");
    }

    @Test
    void conservaAcentosDeArchivoEnLatin1() throws Exception {
        byte[] contenido = "IES,Proyectos,Alumnos,Rango\nUniversidad Autónoma de Querétaro,3,1500,1000 a 3499\n"
                .getBytes(StandardCharsets.ISO_8859_1);
        MockMultipartFile archivo = new MockMultipartFile("file", "latin1.csv", "text/csv", contenido);
        mockMvc.perform(multipart("/api/dispersion/upload").file(archivo).with(oauth2Login()))
                .andExpect(status().isCreated());

        String json = mockMvc.perform(get("/api/dispersion/latin1.csv").with(oauth2Login()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(json).contains("Universidad Autónoma de Querétaro");
    }

    @Test
    void rechazaArchivoQueNoEsCsv() throws Exception {
        MockMultipartFile archivo = new MockMultipartFile("file", "datos.txt", "text/plain",
                "hola".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/dispersion/upload").file(archivo).with(oauth2Login()))
                .andExpect(status().isBadRequest());
    }
}
