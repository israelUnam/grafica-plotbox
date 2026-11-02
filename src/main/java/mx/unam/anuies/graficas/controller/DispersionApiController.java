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

import mx.unam.anuies.graficas.dto.DispersionDto;
import mx.unam.anuies.graficas.service.DispersionStorageService;

@RestController
@RequestMapping("/api/dispersion")
public class DispersionApiController {

    private final DispersionStorageService dispersionStorageService;

    public DispersionApiController(DispersionStorageService dispersionStorageService) {
        this.dispersionStorageService = dispersionStorageService;
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        String nombre = dispersionStorageService.guardar(file);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "nombre", nombre,
                "mensaje", "Archivo cargado correctamente."));
    }

    @GetMapping
    public List<String> listar() {
        return dispersionStorageService.listar();
    }

    @GetMapping("/{nombreArchivo}")
    public DispersionDto obtener(@PathVariable String nombreArchivo) {
        return dispersionStorageService.obtenerDispersion(nombreArchivo);
    }

    @GetMapping("/{nombreArchivo}/descargar")
    public ResponseEntity<Resource> descargar(@PathVariable String nombreArchivo) {
        Resource recurso = dispersionStorageService.obtenerRecurso(nombreArchivo);
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
