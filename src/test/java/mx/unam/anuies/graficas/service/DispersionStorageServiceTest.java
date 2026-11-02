package mx.unam.anuies.graficas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import mx.unam.anuies.graficas.dto.DispersionDto;
import mx.unam.anuies.graficas.exception.CsvNotFoundException;
import mx.unam.anuies.graficas.exception.InvalidCsvException;

class DispersionStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void parseaPuntosYExcluyeSinValorOMarcadosConCero() throws IOException {
        String csv = """
                Institución,Proyectos TI,Alumnos,Rango alumnos,Para gráfica (1=sí)
                Benemérita Universidad Autónoma de Puebla,40,124312,50000 o más,1
                "Centro de Diseño y Comunicación S.C. (Centro Diseño, Cine y Televisión)",12,1600,1000 a 3499,1
                El Colegio de la Frontera Sur,65,384,Menos de 1000,1
                Escuela Normal de Zumpango,Sin valor,291,Menos de 1000,0
                Instituto Nacional de Salud Pública,24,,Sin dato alumnos,0
                """;
        Files.writeString(tempDir.resolve("Alumnos_proyectos.csv"), csv, StandardCharsets.UTF_8);
        DispersionStorageService service = new DispersionStorageService(tempDir.toString());

        DispersionDto dto = service.obtenerDispersion("Alumnos_proyectos.csv");

        assertThat(dto.getTitulo()).isEqualTo("Proyectos TI vs. Alumnos");
        assertThat(dto.getEtiquetaX()).isEqualTo("Alumnos");
        assertThat(dto.getEtiquetaY()).isEqualTo("Proyectos TI");
        assertThat(dto.getEtiquetaGrupo()).isEqualTo("Rango alumnos");
        assertThat(dto.getPuntos()).hasSize(3);
        assertThat(dto.getPuntos().get(1).getNombre())
                .isEqualTo("Centro de Diseño y Comunicación S.C. (Centro Diseño, Cine y Televisión)");
        assertThat(dto.getPuntos().get(0).getX()).isEqualTo(124312.0);
        assertThat(dto.getPuntos().get(0).getY()).isEqualTo(40.0);
        assertThat(dto.getGrupos()).containsExactly("Menos de 1000", "1000 a 3499", "50000 o más");
        assertThat(dto.getExcluidos())
                .containsExactly("Escuela Normal de Zumpango", "Instituto Nacional de Salud Pública");
    }

    @Test
    void lineaDeTituloConLogCoordenadasActivaEscalaLogEnOrdenadas() throws IOException {
        Files.writeString(tempDir.resolve("log.csv"), """
                Proyectos TI por matrícula,log -coordenadas,,,
                Institución,Proyectos TI,Alumnos,Rango alumnos,Para gráfica (1=sí)
                A,10,100,Menos de 1000,1
                B,0,2000,1000 a 3499,1
                """, StandardCharsets.UTF_8);
        DispersionStorageService service = new DispersionStorageService(tempDir.toString());

        DispersionDto dto = service.obtenerDispersion("log.csv");

        assertThat(dto.isEscalaLogY()).isTrue();
        assertThat(dto.getTitulo()).isEqualTo("Proyectos TI por matrícula");
        assertThat(dto.getEtiquetaY()).isEqualTo("Proyectos TI");
        assertThat(dto.getPuntos()).hasSize(2);
    }

    @Test
    void marcaLogDentroDelMismoTextoDelTituloSeOmite() throws IOException {
        Files.writeString(tempDir.resolve("mismo.csv"),
                "Proyectos TI por matrícula LOG-Coordenadas,,,,\nIES,Y,X,Grupo\nA,1,10,G1\n",
                StandardCharsets.UTF_8);
        DispersionStorageService service = new DispersionStorageService(tempDir.toString());

        DispersionDto dto = service.obtenerDispersion("mismo.csv");

        assertThat(dto.isEscalaLogY()).isTrue();
        assertThat(dto.getTitulo()).isEqualTo("Proyectos TI por matrícula");
    }

    @Test
    void tituloSinMarcaMantieneEscalaLinealYSinTituloSeDerivaDeCabecera() throws IOException {
        Files.writeString(tempDir.resolve("titulo.csv"), "Mi reporte,,,,\nIES,Y,X,Grupo\nA,1,10,G1\n",
                StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("sin-titulo.csv"), "IES,Y,X,Grupo\nA,1,10,G1\n",
                StandardCharsets.UTF_8);
        DispersionStorageService service = new DispersionStorageService(tempDir.toString());

        DispersionDto conTitulo = service.obtenerDispersion("titulo.csv");
        assertThat(conTitulo.isEscalaLogY()).isFalse();
        assertThat(conTitulo.getTitulo()).isEqualTo("Mi reporte");

        DispersionDto sinTitulo = service.obtenerDispersion("sin-titulo.csv");
        assertThat(sinTitulo.isEscalaLogY()).isFalse();
        assertThat(sinTitulo.getTitulo()).isEqualTo("Y vs. X");
    }

    @Test
    void sinColumnaDeMarcaGraficaTodosLosValidos() throws IOException {
        Files.writeString(tempDir.resolve("simple.csv"), "IES,Y,X,Grupo\nA,1,10,G1\nB,2,20,G2\n",
                StandardCharsets.UTF_8);
        DispersionStorageService service = new DispersionStorageService(tempDir.toString());

        assertThat(service.obtenerDispersion("simple.csv").getPuntos()).hasSize(2);
    }

    @Test
    void rechazaCabeceraIncompletaYArchivoInexistente() throws IOException {
        Files.writeString(tempDir.resolve("malo.csv"), "IES,Y\nA,1\n", StandardCharsets.UTF_8);
        DispersionStorageService service = new DispersionStorageService(tempDir.toString());

        assertThatThrownBy(() -> service.obtenerDispersion("malo.csv")).isInstanceOf(InvalidCsvException.class);
        assertThatThrownBy(() -> service.obtenerDispersion("nada.csv")).isInstanceOf(CsvNotFoundException.class);
    }
}
