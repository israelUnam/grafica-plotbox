package mx.unam.anuies.graficas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import mx.unam.anuies.graficas.dto.RegistroRegionDto;
import mx.unam.anuies.graficas.dto.ReporteDto;
import mx.unam.anuies.graficas.exception.CsvNotFoundException;
import mx.unam.anuies.graficas.exception.InvalidCsvException;

class CsvStorageServiceTest {

    @TempDir
    Path tempDir;

    private CsvStorageService service;

    @BeforeEach
    void setUp() {
        service = new CsvStorageService(tempDir.toString());
        service.inicializarDirectorio();
    }

    @Test
    void parseaTituloBomYSeparadoresDeMiles() throws Exception {
        String csv = """
                \uFEFFTotal de computadoras para uso administrativo,,,,,,,
                Región,Universidades,Media,Mediana,Máximo,Mínimo,Q1,Q3
                Centro-Occidente,18,"1,626",216,"13,353",22,153,2026
                Noreste,12,"2,626",877,"11,198",5,71,"3,685"
                """;
        Path archivo = tempDir.resolve("computadoras.csv");
        Files.writeString(archivo, csv, StandardCharsets.UTF_8);

        ReporteDto reporte = service.parsear(archivo);

        assertThat(reporte.getTitulo()).isEqualTo("Total de computadoras para uso administrativo");
        assertThat(reporte.getNombreCategoria()).isEqualTo("Región");
        assertThat(reporte.isEscalaDecimal()).isFalse();
        assertThat(reporte.getRegistros()).hasSize(2);

        RegistroRegionDto centro = reporte.getRegistros().get(0);
        assertThat(centro.getRegion()).isEqualTo("Centro-Occidente");
        assertThat(centro.getUniversidades()).isEqualTo(18);
        assertThat(centro.getMedia()).isCloseTo(1626d, within(0.001));
        assertThat(centro.getMaximo()).isCloseTo(13353d, within(0.001));
        assertThat(reporte.getRegistros().get(1).getQ3()).isCloseTo(3685d, within(0.001));
    }

    @Test
    void parseaCsvConRangoYTabuladores() throws Exception {
        String csv = """
                Número de estudiantes conectados a la red inalámbrica\t\t\t\t\t\t\t
                Rango Institucional\tIES\tMedia\tMediana\tMáximo\tMínimo\tQ1\tQ3
                "Más de 100,000 estudiantes"\t6\t"143,558"\t"123,827"\t"211,584"\t"100,491"\t"113,413"\t"174,715"
                "Entre 30,000 y 100,000 estudiantes"\t15\t"46,733"\t"40,736"\t"96,042"\t"9,000"\t"32,407"\t"57,954"
                """;
        Path archivo = tempDir.resolve("alumnos.csv");
        Files.writeString(archivo, csv, StandardCharsets.ISO_8859_1);

        ReporteDto reporte = service.parsear(archivo);

        assertThat(reporte.getTitulo()).isEqualTo("Número de estudiantes conectados a la red inalámbrica");
        assertThat(reporte.getNombreCategoria()).isEqualTo("Rango");
        assertThat(reporte.isEscalaDecimal()).isFalse();
        assertThat(reporte.getRegistros()).hasSize(2);

        RegistroRegionDto primero = reporte.getRegistros().get(0);
        assertThat(primero.getRegion()).isEqualTo("Más de 100,000 estudiantes");
        assertThat(primero.getUniversidades()).isEqualTo(6);
        assertThat(primero.getMedia()).isCloseTo(143558d, within(0.001));
        assertThat(primero.getMinimo()).isCloseTo(100491d, within(0.001));
    }

    @Test
    void detectaEscalaDecimalEnSegundaCeldaDelTitulo() throws Exception {
        String csv = """
                Docentes conectados a la red inalámbrica,dec,,,,,,
                Rango,IES,Media,Mediana,Máximo,Mínimo,Q1,Q3
                Docentes conectados a la red inalámbrica,116,"1,142",232,"15,397",5,116,803
                """;
        Path archivo = tempDir.resolve("Docentes_Red_Inalambrica.csv");
        Files.writeString(archivo, csv, StandardCharsets.UTF_8);

        ReporteDto reporte = service.parsear(archivo);

        assertThat(reporte.isEscalaDecimal()).isTrue();
        assertThat(reporte.getTitulo()).isEqualTo("Docentes conectados a la red inalámbrica");
        assertThat(reporte.getNombreCategoria()).isEqualTo("Rango");
        assertThat(reporte.getRegistros()).hasSize(1);
        assertThat(reporte.getRegistros().get(0).getMedia()).isCloseTo(1142d, within(0.001));
    }

    @Test
    void guardaYListaSoloCsv() {
        MockMultipartFile csv = new MockMultipartFile(
                "file", "reporte.csv", "text/csv", "titulo\na,b\n".getBytes(StandardCharsets.UTF_8));

        String nombre = service.guardar(csv);

        assertThat(nombre).isEqualTo("reporte.csv");
        assertThat(service.listar()).containsExactly("reporte.csv");
    }

    @Test
    void rechazaExtensionNoCsv() {
        MockMultipartFile txt = new MockMultipartFile(
                "file", "notas.txt", "text/plain", "hola".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.guardar(txt))
                .isInstanceOf(InvalidCsvException.class)
                .hasMessageContaining(".csv");
    }

    @Test
    void sanitizaRutaRelativaYNoEscapaDelDirectorio() {
        assertThat(service.sanitizarNombre("../secreto.csv")).isEqualTo("secreto.csv");
        assertThatThrownBy(() -> service.obtenerReporte("../secreto.csv"))
                .isInstanceOf(CsvNotFoundException.class);
        assertThat(tempDir.resolve("secreto.csv")).doesNotExist();
        assertThat(tempDir.getParent().resolve("secreto.csv")).doesNotExist();
    }

    @Test
    void lanzaNotFoundSiNoExiste() {
        assertThatThrownBy(() -> service.obtenerReporte("ausente.csv"))
                .isInstanceOf(CsvNotFoundException.class);
    }

    @Test
    void obtieneRecursoExistente() throws Exception {
        Path archivo = tempDir.resolve("datos_prueba.csv");
        Files.writeString(archivo, "col1,col2\n1,2\n", StandardCharsets.UTF_8);

        org.springframework.core.io.Resource recurso = service.obtenerRecurso("datos_prueba.csv");
        assertThat(recurso.exists()).isTrue();
        assertThat(recurso.getFilename()).isEqualTo("datos_prueba.csv");
    }

    @Test
    void lanzaNotFoundAlObtenerRecursoInexistente() {
        assertThatThrownBy(() -> service.obtenerRecurso("no_existe.csv"))
                .isInstanceOf(CsvNotFoundException.class);
    }
}
