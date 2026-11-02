package mx.unam.anuies.graficas.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import mx.unam.anuies.graficas.dto.RegistroRegionDto;
import mx.unam.anuies.graficas.dto.ReporteDto;
import mx.unam.anuies.graficas.exception.CsvNotFoundException;
import mx.unam.anuies.graficas.exception.CsvStorageException;
import mx.unam.anuies.graficas.exception.InvalidCsvException;

@Service
public class CsvStorageService {

    private static final String EXTENSION_CSV = ".csv";
    private static final char BOM = '\uFEFF';

    private final Path storagePath;

    public CsvStorageService(@Value("${app.storage.path}") String storagePath) {
        this.storagePath = Path.of(storagePath).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void inicializarDirectorio() {
        try {
            Files.createDirectories(storagePath);
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo crear el directorio de datos: " + storagePath, ex);
        }
    }

    public Path getStoragePath() {
        return storagePath;
    }

    public String guardar(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new InvalidCsvException("Debe seleccionar un archivo CSV no vacío.");
        }

        String nombreSanitizado = sanitizarNombre(archivo.getOriginalFilename());
        Path destino = resolverDentroDelAlmacen(nombreSanitizado);

        try {
            archivo.transferTo(destino);
            return nombreSanitizado;
        } catch (IOException ex) {
            throw new CsvStorageException("No se pudo guardar el archivo " + nombreSanitizado, ex);
        }
    }

    public List<String> listar() {
        if (!Files.isDirectory(storagePath)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(storagePath)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(nombre -> nombre.toLowerCase(Locale.ROOT).endsWith(EXTENSION_CSV))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        } catch (IOException ex) {
            throw new CsvStorageException("No se pudieron listar los archivos CSV.", ex);
        }
    }

    public ReporteDto obtenerReporte(String nombreArchivo) {
        Path archivo = resolverDentroDelAlmacen(sanitizarNombre(nombreArchivo));
        if (!Files.exists(archivo) || !Files.isRegularFile(archivo)) {
            throw new CsvNotFoundException(nombreArchivo);
        }
        return parsear(archivo);
    }

    public Resource obtenerRecurso(String nombreArchivo) {
        Path archivo = resolverDentroDelAlmacen(sanitizarNombre(nombreArchivo));
        if (!Files.exists(archivo) || !Files.isRegularFile(archivo)) {
            throw new CsvNotFoundException(nombreArchivo);
        }
        return new FileSystemResource(archivo);
    }

    public ReporteDto parsear(Path archivo) {
        String contenido = leerTexto(archivo);
        try (BufferedReader reader = new BufferedReader(new StringReader(contenido))) {
            String lineaTitulo = reader.readLine();
            if (lineaTitulo == null || lineaTitulo.isBlank()) {
                throw new InvalidCsvException("El archivo no contiene un título en la primera línea.");
            }

            String lineaCabecera = reader.readLine();
            if (lineaCabecera == null || lineaCabecera.isBlank()) {
                throw new InvalidCsvException("El archivo no contiene una cabecera de columnas.");
            }

            char delimitador = detectarDelimitador(lineaCabecera);
            String titulo = extraerTitulo(lineaTitulo, delimitador);
            boolean escalaDecimal = esEscalaDecimalDesdeTitulo(lineaTitulo, delimitador);

            List<String> columnasCabecera = parsearLineaCsv(lineaCabecera, delimitador);
            if (columnasCabecera.size() < 8) {
                throw new InvalidCsvException(
                        "La cabecera no contiene las 8 columnas esperadas (Región o Rango, Universidades/IES, Media, Mediana, Máximo, Mínimo, Q1, Q3).");
            }
            String nombreCategoria = determinarNombreCategoria(columnasCabecera.get(0));

            List<RegistroRegionDto> registros = new ArrayList<>();
            String linea;
            int numeroLinea = 2;
            while ((linea = reader.readLine()) != null) {
                numeroLinea++;
                if (linea.isBlank()) {
                    continue;
                }
                registros.add(parsearRegistro(linea, numeroLinea, delimitador));
            }

            if (registros.isEmpty()) {
                throw new InvalidCsvException("El archivo no contiene registros de datos.");
            }

            return ReporteDto.builder()
                    .titulo(titulo)
                    .nombreArchivo(archivo.getFileName().toString())
                    .nombreCategoria(nombreCategoria)
                    .escalaDecimal(escalaDecimal)
                    .registros(registros)
                    .build();
        } catch (IOException ex) {
            throw new CsvStorageException("No se pudo leer el archivo " + archivo.getFileName(), ex);
        }
    }

    char detectarDelimitador(String lineaCabecera) {
        int comas = 0;
        int tabs = 0;
        int puntoYComas = 0;
        for (int i = 0; i < lineaCabecera.length(); i++) {
            char c = lineaCabecera.charAt(i);
            if (c == ',') {
                comas++;
            } else if (c == '\t') {
                tabs++;
            } else if (c == ';') {
                puntoYComas++;
            }
        }
        if (tabs > comas && tabs > puntoYComas) {
            return '\t';
        }
        if (puntoYComas > comas && puntoYComas > tabs) {
            return ';';
        }
        return ',';
    }

    String determinarNombreCategoria(String cabeceraPrimeraColumna) {
        if (cabeceraPrimeraColumna == null) {
            return "Región";
        }
        String limpio = cabeceraPrimeraColumna.trim();
        String normalizado = limpio.toLowerCase(Locale.ROOT);
        if (normalizado.contains("rango")) {
            return "Rango";
        }
        if (normalizado.contains("regi")) {
            return "Región";
        }
        return limpio.isEmpty() ? "Región" : limpio;
    }

    boolean esEscalaDecimalDesdeTitulo(String lineaTitulo, char delimitador) {
        List<String> campos = parsearLineaCsv(quitarBom(lineaTitulo), delimitador);
        if (campos.size() < 2) {
            return false;
        }
        String segundo = campos.get(1) == null ? "" : campos.get(1).trim().toLowerCase(Locale.ROOT);
        return segundo.contains("dec");
    }

    String extraerTitulo(String primeraLinea) {
        return extraerTitulo(primeraLinea, ',');
    }

    String extraerTitulo(String primeraLinea, char delimitador) {
        List<String> campos = parsearLineaCsv(quitarBom(primeraLinea), delimitador);
        return campos.stream()
                .map(String::trim)
                .filter(campo -> !campo.isEmpty())
                .findFirst()
                .orElseThrow(() -> new InvalidCsvException("No se encontró un título válido en la primera línea."));
    }

    RegistroRegionDto parsearRegistro(String linea, int numeroLinea) {
        return parsearRegistro(linea, numeroLinea, ',');
    }

    RegistroRegionDto parsearRegistro(String linea, int numeroLinea, char delimitador) {
        List<String> campos = parsearLineaCsv(linea, delimitador);
        if (campos.size() < 8) {
            throw new InvalidCsvException(
                    "La línea " + numeroLinea + " no tiene las 8 columnas esperadas (Región o Rango, Universidades/IES, Media, Mediana, Máximo, Mínimo, Q1, Q3).");
        }
        try {
            return RegistroRegionDto.builder()
                    .region(campos.get(0).trim())
                    .universidades(parsearEntero(campos.get(1)))
                    .media(parsearNumero(campos.get(2)))
                    .mediana(parsearNumero(campos.get(3)))
                    .maximo(parsearNumero(campos.get(4)))
                    .minimo(parsearNumero(campos.get(5)))
                    .q1(parsearNumero(campos.get(6)))
                    .q3(parsearNumero(campos.get(7)))
                    .build();
        } catch (NumberFormatException ex) {
            throw new InvalidCsvException("La línea " + numeroLinea + " contiene un valor numérico inválido.", ex);
        }
    }

    List<String> parsearLineaCsv(String linea) {
        return parsearLineaCsv(linea, ',');
    }

    List<String> parsearLineaCsv(String linea, char delimitador) {
        List<String> campos = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        boolean entreComillas = false;

        for (int i = 0; i < linea.length(); i++) {
            char caracter = linea.charAt(i);
            if (caracter == '"') {
                if (entreComillas && i + 1 < linea.length() && linea.charAt(i + 1) == '"') {
                    actual.append('"');
                    i++;
                } else {
                    entreComillas = !entreComillas;
                }
            } else if (caracter == delimitador && !entreComillas) {
                campos.add(actual.toString().trim());
                actual.setLength(0);
            } else {
                actual.append(caracter);
            }
        }
        campos.add(actual.toString().trim());
        return campos;
    }

    Double parsearNumero(String valor) {
        String limpio = limpiarNumero(valor);
        if (limpio.isEmpty()) {
            throw new NumberFormatException("Valor vacío");
        }
        return Double.valueOf(limpio);
    }

    Integer parsearEntero(String valor) {
        return parsearNumero(valor).intValue();
    }

    String limpiarNumero(String valor) {
        if (valor == null) {
            return "";
        }
        String limpio = valor.replace("\"", "").replace("\u00A0", "").trim().replace(" ", "");
        if (limpio.isEmpty()) {
            return "";
        }

        boolean tienePunto = limpio.indexOf('.') >= 0;
        boolean tieneComa = limpio.indexOf(',') >= 0;

        if (tienePunto && tieneComa) {
            int ultimoPunto = limpio.lastIndexOf('.');
            int ultimaComa = limpio.lastIndexOf(',');
            if (ultimaComa > ultimoPunto) {
                limpio = limpio.replace(".", "").replace(',', '.');
            } else {
                limpio = limpio.replace(",", "");
            }
        } else if (tieneComa) {
            if (pareceDecimalConComa(limpio)) {
                limpio = limpio.replace(',', '.');
            } else {
                limpio = limpio.replace(",", "");
            }
        }

        return limpio;
    }

    private boolean pareceDecimalConComa(String valor) {
        int indice = valor.lastIndexOf(',');
        String decimales = valor.substring(indice + 1);
        return decimales.length() > 0 && decimales.length() <= 2 && valor.indexOf(',') == indice;
    }

    String sanitizarNombre(String nombreOriginal) {
        if (nombreOriginal == null || nombreOriginal.isBlank()) {
            throw new InvalidCsvException("El nombre del archivo es inválido.");
        }

        String nombre = Path.of(nombreOriginal).getFileName().toString().trim();
        if (nombre.isEmpty() || ".".equals(nombre) || "..".equals(nombre)) {
            throw new InvalidCsvException("El nombre del archivo no está permitido.");
        }
        if (nombre.contains("..") || nombre.indexOf('/') >= 0 || nombre.indexOf('\\') >= 0) {
            throw new InvalidCsvException("El nombre del archivo contiene una ruta no permitida.");
        }
        if (!nombre.toLowerCase(Locale.ROOT).endsWith(EXTENSION_CSV)) {
            throw new InvalidCsvException("Solo se permiten archivos con extensión .csv.");
        }
        return nombre;
    }

    Path resolverDentroDelAlmacen(String nombreArchivo) {
        Path destino = storagePath.resolve(nombreArchivo).normalize();
        if (!destino.startsWith(storagePath)) {
            throw new InvalidCsvException("El nombre del archivo no está permitido.");
        }
        return destino;
    }

    String leerTexto(Path archivo) {
        try {
            byte[] bytes = Files.readAllBytes(archivo);
            return new String(bytes, detectarCharset(bytes));
        } catch (IOException ex) {
            throw new CsvStorageException("No se pudo leer el archivo " + archivo.getFileName(), ex);
        }
    }

    private Charset detectarCharset(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF) {
            return StandardCharsets.UTF_8;
        }
        try {
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
            decoder.onMalformedInput(CodingErrorAction.REPORT);
            decoder.onUnmappableCharacter(CodingErrorAction.REPORT);
            decoder.decode(ByteBuffer.wrap(bytes));
            return StandardCharsets.UTF_8;
        } catch (CharacterCodingException ex) {
            return StandardCharsets.ISO_8859_1;
        }
    }

    String quitarBom(String texto) {
        if (texto != null && !texto.isEmpty() && texto.charAt(0) == BOM) {
            return texto.substring(1);
        }
        return texto;
    }
}
