package mx.unam.anuies.graficas.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import mx.unam.anuies.graficas.dto.DispersionDto;
import mx.unam.anuies.graficas.dto.PuntoDispersionDto;
import mx.unam.anuies.graficas.exception.CsvNotFoundException;
import mx.unam.anuies.graficas.exception.CsvStorageException;
import mx.unam.anuies.graficas.exception.InvalidCsvException;

/**
 * Almacén de CSV para diagramas de dispersión; usa un directorio propio para no
 * mezclarse con los datasets de diagramas de caja.
 *
 * Formato esperado: [línea de título opcional], cabecera y datos con
 * Nombre, valor Y, valor X, grupo, [marca para graficar (1=sí)].
 * La línea de título se reconoce por tener menos de 4 celdas con texto; si contiene
 * "log -coordenadas", el eje de las ordenadas usa escala logarítmica.
 */
@Service
public class DispersionStorageService {

    private static final int COLUMNAS_MINIMAS = 4;
    private static final Set<String> MARCAS_AFIRMATIVAS = Set.of("1", "si", "sí", "x", "true");
    private static final Pattern MARCA_LOG_ORDENADAS = Pattern.compile("log\\s*-\\s*(co)?ordenadas",
            Pattern.CASE_INSENSITIVE);

    private final CsvStorageService almacen;

    public DispersionStorageService(
            @Value("${app.storage.dispersion-path:${app.storage.path}/dispersion}") String storagePath) {
        this.almacen = new CsvStorageService(storagePath);
        this.almacen.inicializarDirectorio();
    }

    public String guardar(MultipartFile archivo) {
        return almacen.guardar(archivo);
    }

    public List<String> listar() {
        return almacen.listar();
    }

    public Resource obtenerRecurso(String nombreArchivo) {
        return almacen.obtenerRecurso(nombreArchivo);
    }

    public DispersionDto obtenerDispersion(String nombreArchivo) {
        Path archivo = almacen.resolverDentroDelAlmacen(almacen.sanitizarNombre(nombreArchivo));
        if (!Files.isRegularFile(archivo)) {
            throw new CsvNotFoundException(nombreArchivo);
        }
        return parsear(archivo);
    }

    DispersionDto parsear(Path archivo) {
        String contenido = almacen.leerTexto(archivo);
        try (BufferedReader reader = new BufferedReader(new StringReader(contenido))) {
            String primeraLinea = almacen.quitarBom(reader.readLine());
            if (primeraLinea == null || primeraLinea.isBlank()) {
                throw new InvalidCsvException("El archivo no contiene una cabecera de columnas.");
            }
            char delimitador = almacen.detectarDelimitador(primeraLinea);
            List<String> camposPrimeraLinea = almacen.parsearLineaCsv(primeraLinea, delimitador);

            String lineaTitulo = null;
            List<String> cabecera = camposPrimeraLinea;
            if (contarNoVacios(camposPrimeraLinea) < COLUMNAS_MINIMAS) {
                lineaTitulo = primeraLinea;
                String lineaCabecera = reader.readLine();
                if (lineaCabecera == null || lineaCabecera.isBlank()) {
                    throw new InvalidCsvException("El archivo no contiene una cabecera de columnas.");
                }
                delimitador = almacen.detectarDelimitador(lineaCabecera);
                cabecera = almacen.parsearLineaCsv(lineaCabecera, delimitador);
            }
            if (cabecera.size() < COLUMNAS_MINIMAS) {
                throw new InvalidCsvException(
                        "La cabecera debe tener al menos 4 columnas (Nombre, valor Y, valor X, grupo).");
            }
            boolean tieneMarca = cabecera.size() > COLUMNAS_MINIMAS
                    && !cabecera.get(COLUMNAS_MINIMAS).isBlank();

            List<PuntoDispersionDto> puntos = new ArrayList<>();
            List<String> excluidos = new ArrayList<>();
            String linea;
            while ((linea = reader.readLine()) != null) {
                if (linea.isBlank()) {
                    continue;
                }
                List<String> campos = almacen.parsearLineaCsv(linea, delimitador);
                String nombre = campos.get(0).trim();
                if (nombre.isEmpty()) {
                    continue;
                }
                PuntoDispersionDto punto = construirPunto(campos, tieneMarca);
                if (punto == null) {
                    excluidos.add(nombre);
                } else {
                    puntos.add(punto);
                }
            }

            if (puntos.isEmpty()) {
                throw new InvalidCsvException("El archivo no contiene registros graficables.");
            }

            String etiquetaY = cabecera.get(1).trim();
            String etiquetaX = cabecera.get(2).trim();
            boolean escalaLogY = lineaTitulo != null && MARCA_LOG_ORDENADAS.matcher(lineaTitulo).find();
            String titulo = extraerTitulo(lineaTitulo, delimitador);
            return DispersionDto.builder()
                    .titulo(titulo.isEmpty() ? etiquetaY + " vs. " + etiquetaX : titulo)
                    .nombreArchivo(archivo.getFileName().toString())
                    .etiquetaNombre(cabecera.get(0).trim())
                    .etiquetaX(etiquetaX)
                    .etiquetaY(etiquetaY)
                    .etiquetaGrupo(cabecera.get(3).trim())
                    .escalaLogY(escalaLogY)
                    .grupos(ordenarGrupos(puntos))
                    .puntos(puntos)
                    .excluidos(excluidos)
                    .build();
        } catch (IOException ex) {
            throw new CsvStorageException("No se pudo leer el archivo " + archivo.getFileName(), ex);
        }
    }

    private int contarNoVacios(List<String> campos) {
        return (int) campos.stream().filter(campo -> !campo.isBlank()).count();
    }

    /** Primer texto no vacío de la línea de título, sin la marca de escala. */
    private String extraerTitulo(String lineaTitulo, char delimitador) {
        if (lineaTitulo == null) {
            return "";
        }
        return almacen.parsearLineaCsv(lineaTitulo, delimitador).stream()
                .map(campo -> MARCA_LOG_ORDENADAS.matcher(campo).replaceAll(" ").trim().replaceAll("\\s{2,}", " "))
                .filter(campo -> !campo.isEmpty())
                .findFirst()
                .orElse("");
    }

    private PuntoDispersionDto construirPunto(List<String> campos, boolean tieneMarca) {
        if (campos.size() < COLUMNAS_MINIMAS) {
            return null;
        }
        if (tieneMarca) {
            String marca = campos.size() > COLUMNAS_MINIMAS
                    ? campos.get(COLUMNAS_MINIMAS).trim().toLowerCase(Locale.ROOT)
                    : "";
            if (!MARCAS_AFIRMATIVAS.contains(marca)) {
                return null;
            }
        }
        Double y = numeroOpcional(campos.get(1));
        Double x = numeroOpcional(campos.get(2));
        if (x == null || y == null) {
            return null;
        }
        return PuntoDispersionDto.builder()
                .nombre(campos.get(0).trim())
                .x(x)
                .y(y)
                .grupo(campos.get(3).trim())
                .build();
    }

    private Double numeroOpcional(String valor) {
        String limpio = almacen.limpiarNumero(valor);
        if (limpio.isEmpty()) {
            return null;
        }
        try {
            return Double.valueOf(limpio);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private List<String> ordenarGrupos(List<PuntoDispersionDto> puntos) {
        Map<String, Double> minimoPorGrupo = new LinkedHashMap<>();
        for (PuntoDispersionDto punto : puntos) {
            minimoPorGrupo.merge(punto.getGrupo(), punto.getX(), Math::min);
        }
        return minimoPorGrupo.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.naturalOrder()))
                .map(Map.Entry::getKey)
                .toList();
    }
}
