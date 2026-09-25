package mx.unam.anuies.graficas.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import mx.unam.anuies.graficas.dto.ReporteDto;
import mx.unam.anuies.graficas.service.CsvStorageService;

@RestController
@RequestMapping("/api/archivos")
public class CsvApiController {

    private final CsvStorageService csvStorageService;

    public CsvApiController(CsvStorageService csvStorageService) {
        this.csvStorageService = csvStorageService;
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        String nombre = csvStorageService.guardar(file);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "nombre", nombre,
                "mensaje", "Archivo cargado correctamente."));
    }

    @GetMapping
    public List<String> listar() {
        return csvStorageService.listar();
    }

    @GetMapping("/{nombreArchivo}")
    public ReporteDto obtener(@PathVariable String nombreArchivo) {
        return csvStorageService.obtenerReporte(nombreArchivo);
    }

    @GetMapping("/{nombreArchivo}/descargar")
    public ResponseEntity<Resource> descargar(@PathVariable String nombreArchivo) {
        Resource recurso = csvStorageService.obtenerRecurso(nombreArchivo);
        String nombre = recurso.getFilename() != null ? recurso.getFilename() : nombreArchivo;

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(nombre, StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(recurso);
    }
}
