package mx.unam.anuies.graficas.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import mx.unam.anuies.graficas.exception.ApiExceptionHandler;
import mx.unam.anuies.graficas.exception.CsvNotFoundException;
import mx.unam.anuies.graficas.service.CsvStorageService;

@ExtendWith(MockitoExtension.class)
class CsvApiControllerTest {

    private MockMvc mockMvc;

    @Mock
    private CsvStorageService csvStorageService;

    @InjectMocks
    private CsvApiController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void descargarArchivoExistenteRetornaCsvYHeaderAttachment() throws Exception {
        String contenido = "Titulo\nCol1,Col2\n1,2\n";
        ByteArrayResource recurso = new ByteArrayResource(contenido.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "reporte.csv";
            }
        };

        given(csvStorageService.obtenerRecurso("reporte.csv")).willReturn(recurso);

        mockMvc.perform(get("/api/archivos/reporte.csv/descargar"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"reporte.csv\"; filename*=UTF-8''reporte.csv"))
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(content().string(contenido));
    }

    @Test
    void descargarArchivoInexistenteRetornaNotFound() throws Exception {
        given(csvStorageService.obtenerRecurso("inexistente.csv"))
                .willThrow(new CsvNotFoundException("inexistente.csv"));

        mockMvc.perform(get("/api/archivos/inexistente.csv/descargar"))
                .andExpect(status().isNotFound());
    }
}
